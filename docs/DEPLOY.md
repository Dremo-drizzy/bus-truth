# Deploying Bus Truth to Render

Written to be followed in six months having forgotten all of this. Everything in
`render.yaml` is created by Render; everything in this file is done by hand, in order.

**Nothing has been deployed yet.** Steps 1–10 are the whole job — about 40 minutes, of
which 30 is waiting for a restore.

## What you are building

```
   bus-truth-web          static site, free      the five React pages
         │  fetches https://bus-truth-api.onrender.com/api/...
   bus-truth-api          web service, free      the eight endpoints, from Docker
         │  private network, same region
   bus-truth-db           Postgres, free         a copy of the derived tables
```

The collector is **not** deployed and is not in the blueprint. It keeps running on your
laptop, archiving to `data/`. The cloud holds a read-only copy of the derived tables and
nothing that cannot be rebuilt by replaying the archive.

## Two things to know before you start

**The free database expires 30 days after you create it.** Render then allows a 14-day
grace period to upgrade to a paid plan, after which it deletes the database and
everything in it. Put a reminder in your calendar the day you create it. Nothing
irreplaceable is lost when it goes — the archive is on your machine and every cloud
table can be rebuilt from it — but the site stops working. See
[ADR-016](decisions/016-how-it-is-deployed.md).

**Free web services sleep.** After roughly 15 minutes without traffic the API spins
down, and the next visitor waits for it: about 12 seconds of JVM boot plus however long
Render takes to schedule the container, so budget 30 seconds. The site tells them that
rather than spinning silently — but it is still 30 seconds. Step 10 is where you decide
whether it is worth paying to avoid.

---

## 1. Push the blueprint

```bash
git checkout r8-ship
git push origin r8-ship
```

`render.yaml` has to be on the branch you point Render at. Everything below assumes
`r8-ship`; if it has been merged by then, use `main`.

## 2. Create everything from the blueprint

Render dashboard: **New → Blueprint**, choose the `bus-truth` repository, choose the
branch, **Apply**.

Render creates three services plus a database and starts building. Two will not be
healthy yet, and that is expected:

- `bus-truth-db` — ready in a minute or so
- `bus-truth-api` — builds the Docker image (5–10 minutes the first time), then starts
  and **passes its health check while returning errors from `/api/...`**, because the
  database is empty. That combination is deliberate: the health check is liveness only
  (`/actuator/health/liveness`), so Render leaves the service running instead of
  restarting it in a loop before you have had a chance to load the data. Measured: with
  no database, `/actuator/health` answers **503 DOWN** while
  `/actuator/health/liveness` answers **200 UP**
- `bus-truth-web` — builds in 1–2 minutes and succeeds, but every page shows an error,
  because the API has nothing to answer with

If Render rejects `plan: free` on anything, the free plan has been renamed since this
was written: pick the free option in the dashboard for that service and carry on.

## 3. Note the two connection strings

Dashboard → `bus-truth-db` → **Connections**:

- **Internal Database URL** — used automatically by the API. You never need to touch it.
- **External Database URL** — what you restore through in step 4. Looks like
  `postgres://bustruth:...@dpg-xxxx-a.virginia-postgres.render.com/bustruth`.

The external one is a credential. It does not go into a file in this repository, a
commit message, or a chat window.

## 4. Load the data — the one manual step that matters

The cloud database starts empty **and the API cannot create its own schema**: Flyway
belongs to the collector, which is not deployed. The restore brings the tables and the
figures together.

Four sets of tables go up. Raw positions do not — 1.4 million rows that nothing in the
cloud reads.

```bash
cd ~/bus-truth                      # wherever the repo lives
export PGPASSWORD=$(grep '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)

pg_dump -h localhost -p 55432 -U bustruth -d bustruth \
  --no-owner --no-privileges --clean --if-exists \
  -t 'gtfs_*' -t arrival_events -t ontime_events -t ontime_quality \
  -f bustruth-derived.sql

ls -lh bustruth-derived.sql          # expect about 48 MB
```

**Measured, not estimated:**

| | |
|---|---|
| the dump file | **47.8 MB** |
| the same file gzipped | 6.8 MB |
| **restored size in the cloud, with indexes** | **139 MB** |
| of which table data | 83 MB |
| against the free tier's 1 GB | **14% used** |

So storage is not a constraint — there is room for roughly six more weeks of derived
data before the limit matters, and the database expires long before that.

What is in it, measured on the current database:

| table | rows | size |
|---|---|---|
| `gtfs_stop_times` | 324,165 | 64 MB |
| `ontime_events` | 112,951 | 2.6 MB |
| `arrival_events` | 113,841 | 1.9 MB |
| `gtfs_trips` | 9,239 | 1.8 MB |
| `gtfs_stops`, `gtfs_routes`, `gtfs_calendar*`, `gtfs_feed_versions`, `ontime_quality` | smaller | < 1 MB |

Restore it through the **External** URL from step 3:

```bash
psql "postgres://bustruth:...@dpg-xxxx-a.virginia-postgres.render.com/bustruth" \
  -v ON_ERROR_STOP=1 -f bustruth-derived.sql
```

**Expect 5–15 minutes**, and the time is not the upload. 48 MB goes over a home
connection in a couple of minutes; the rest is PostgreSQL rebuilding indexes on a free
instance with a shared CPU. It prints a line per object, and the `DROP ... IF EXISTS`
notices at the start are normal on a first run.

If your upstream is slow, gzip makes the transfer about seven times smaller:

```bash
gzip -9 bustruth-derived.sql                     # 47.8 MB -> 6.8 MB
gunzip -c bustruth-derived.sql.gz | psql "<external url>" -v ON_ERROR_STOP=1
```

At 139 MB restored against a 1 GB free tier there is no need to thin anything. If a
future load does hit the limit, `gtfs_stop_times` is the table to cut — the API reads
only `feed_version`, `trip_id`, `stop_sequence`, `stop_id`, `arrival_time` and
`departure_time`.

Confirm it arrived:

```bash
psql "<external url>" -c "
  select 'ontime_events' t, count(*) from ontime_events
  union all select 'arrival_events', count(*) from arrival_events
  union all select 'gtfs_stop_times', count(*) from gtfs_stop_times;"
```

Then delete the dump — it holds no credentials but it is 70 MB of clutter:
`rm bustruth-derived.sql`.

## 5. Restart the API

Dashboard → `bus-truth-api` → **Manual Deploy → Restart service**. It started against an
empty database; after the restart the health check passes and the endpoints have data.

## 6. Close the database to the internet

This is the project's one hard requirement, and the restore in step 4 is the only reason
the database was ever reachable from outside.

Dashboard → `bus-truth-db` → **Access Control**. Remove `0.0.0.0/0` if present, and
either leave the allowed list **empty** (no external access at all) or restrict it to
your own IP if you expect to restore again.

The API is unaffected — it reaches the database over Render's private network with the
internal URL, which access control does not govern.

**Prove it, the way it was proved locally:**

```bash
# Locally this printed exactly one published port and nothing else:
#   $ docker compose -f infra/docker-compose.prod.yml config | grep published
#   web: published: "80"

# On Render the equivalent is that the external URL stops answering:
psql "postgres://bustruth:...@dpg-xxxx-a.virginia-postgres.render.com/bustruth" \
  -c "select 1" ; echo "exit=$?"
# Expect a connection refused or timeout, and a non-zero exit.

# And the site still works, which is what proves the private path is the one in use:
curl -s -o /dev/null -w "%{http_code}\n" https://bus-truth-api.onrender.com/api/coverage
# Expect 200.
```

Paste both outputs into the pull request. A claim that a database is unreachable is
worth nothing without the failed connection beside it.

## 7. Secrets

**There are none to set.** Every value the API needs comes either from the database
Render created (`DATABASE_URL`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) or
from the other service (`BUSTRUTH_API_WEB_ORIGIN`), and `render.yaml` wires all of them
with `fromDatabase` and `fromService`.

If you later add something secret — an API key, a password on the processor — declare it
in `render.yaml` as `sync: false` and set the value in **Dashboard → service →
Environment**. Never as a literal in the file.

Worth one look by eye: Dashboard → `bus-truth-api` → **Environment** should show
`DATABASE_URL` as coming *from the database*, not as a typed value.

## 8. Verify all eight endpoints

```bash
API=https://bus-truth-api.onrender.com
R="from=2026-10-04&to=2026-10-10"

for p in \
  "/api/vehicles/live" \
  "/api/routes" \
  "/api/routes/performance?$R" \
  "/api/routes/1/performance?$R" \
  "/api/stops/8640/reliability?$R" \
  "/api/coverage" \
  "/api/meta" \
  "/api/performance/summary?$R" ; do
  printf "%-3s %s\n" "$(curl -s -o /dev/null -w '%{http_code}' "$API$p")" "$p"
done
```

All eight must be `200`. The first will be slow if the service was asleep.

`/api/vehicles/live` returning an empty feature collection is **correct**: the collector
is not deployed, so nothing writes live positions, and the map shows its "no recent
data" notice. That is one of the three states it exists to distinguish.

## 9. Verify all five pages

Open the static site URL and check each renders numbers rather than an error:

| page | what to look for |
|---|---|
| `/` | the map draws; the "no recent data" notice is expected |
| `/routes` | the overview (on time, by hour, by service date) and the ranked table |
| `/routes/1` | two charts and a worst-stops table |
| `/stops/8640` | Mumford Terminal, with a by-route table |
| `/about` | the method text and a coverage table with real dates |

**Then the test that catches the mistake this deployment is most likely to make:**

> Open `https://<your-site>.onrender.com/routes/1` **directly in a new tab**. Do not
> click through from the home page.

If that 404s, the SPA rewrite in `render.yaml` is not in effect. It is the commonest
failure when putting a Vite app on a static host, it never appears while clicking
around, and it breaks every link anybody shares.

Also worth doing once at 400px wide in devtools: nothing should scroll sideways. The
Playwright suite asserts that on every push, but against the local build.

## 10. Read the memory, then decide about $7

Dashboard → `bus-truth-api` → **Metrics → Memory**.

Measured locally in the same image, under a hard 512 MB limit, with the same JVM flags
the blueprint sets:

| | |
|---|---|
| steady state, idle | **188 MiB** (37% of 512) |
| under 150 concurrent requests | **194 MiB** (38%) |
| peak ever recorded, which is JVM startup | **300 MiB** (59%) |

**It fits, with headroom.** 512 MB is not the constraint — the startup spike is the
largest figure and it is still three fifths of the limit.

So the $7/month question is not about memory. It is about the sleep.

A free service spins down after about 15 minutes idle. 12 seconds of that is the JVM
booting, measured; the visitor also waits for Render to schedule the container, so
**expect something closer to 30 seconds** and do not be surprised by it.

The site itself is static and never sleeps, so the page appears instantly — the only
question was what it shows while waiting. It now says so: after three seconds the
loading line adds that the server is waking up, that it sleeps when nobody is using it,
and that this takes about thirty seconds. Both behaviours are asserted in the browser
suite, including that a fast response never shows it.

**If you only ever pay for one thing, pay for the database, not the API.** The API
sleeping is cosmetic and now explained; the database expiring takes the whole site down
30 days after you create it. Roughly $6 for Postgres against $7 for the API, and the
failure modes are not comparable.

To upgrade either, change only its `plan:` in `render.yaml` and re-apply the blueprint.

---

## Afterwards: the 24-hour coverage check

The brief's last unmet requirement is the collector running unattended for 24 hours
above 99% coverage. **This deployment is not that.** The collector runs on your laptop,
so the measurement stays local:

```bash
# leave the collector running for a day, then:
curl -s localhost:8084/api/coverage | tail -5
```

Coverage is snapshots captured against the 4,320 a day that 20-second polling produces.
On a laptop that sleeps it has measured 32%–79%. Above 99% needs a machine that does not
sleep, which is what hosting the collector would fix and what this blueprint
deliberately does not do.

## If something is wrong

| symptom | cause |
|---|---|
| API health check fails | not the database — liveness does not touch it. Check the build logs and the JVM flags |
| health check passes but `/api/...` returns errors | the restore has not run (step 4), or the database expired. `curl $API/actuator/health` answers 503 when the database is unreachable, which is the one to ask |
| every page shows "Could not load" | `VITE_API_BASE_URL` was not set at build time — check the static site's Environment, then **Clear build cache & deploy** |
| pages work when clicked but a pasted URL 404s | the SPA rewrite is missing (step 9) |
| the browser console shows a CORS error | `BUSTRUTH_API_WEB_ORIGIN` does not match the site's origin exactly. It is compared as a literal string, so a **trailing slash** refuses every request and looks identical to the API being down. The API normalises both the missing scheme and the trailing slash, so if this happens the value is wrong in some other way — check the API's logs for the line beginning "allowing cross-origin GETs from" |
| the API restarts every few minutes | out of memory — compare Metrics against step 10 |
| it all worked, now the database is gone | the free database expired: 14-day grace to upgrade, then deletion |
