# Deploying Bus Truth

Everything here is built and validated. **Nothing has been deployed** — the host, the
accounts and the domain are Daniel's to choose, and the brief leaves the choice to him
on the day. This is what to run once that choice is made.

## The costed plan — one host, waiting on approval

**Nothing has been created.** No account, no server, no volume. This section is the
plan and the sizing; the spend is Daniel's to approve.

### What has to be hosted, measured rather than guessed

| | measured now | growth at full coverage |
|---|---|---|
| archive — trip updates | **15.0 GB** (74,313 snapshots, mean 202.4 kB) | **~840 MB/day** |
| archive — alerts | 709 MB (74,574 snapshots, mean 9.5 kB) | ~39 MB/day |
| archive — vehicle positions | 285 MB (75,124 snapshots, mean 3.8 kB) | ~16 MB/day |
| **archive total** | **~16 GB** | **~910 MB/day ≈ 27 GB/month** |
| PostgreSQL | 353 MB (1.15M position rows, 16 partly-collected days) | ~250 MB/day ≈ 7.5 GB/month |

A year of uninterrupted collection is therefore **roughly 330 GB of archive and 90 GB
of database**. Those two numbers decide the hosting, and they decide it before price
does.

### What the sizing rules out immediately

- **The brief's option B — a free managed PostgreSQL tier — is dead.** Neon's and
  Supabase's free tiers are around 0.5 GB. The database is already 353 MB after sixteen
  partly-collected days and grows about 7.5 GB a month. It would exhaust a free tier
  within the first week of real collection.
- **A 40 GB server is a few weeks of runway**, and an 80 GB one a couple of months,
  against 27 GB a month of archive. Whatever host is chosen needs expandable block
  storage, not just a bigger root disk.

### The lever worth deciding before spending anything

**94% of the archive is trip updates, and nothing in the project reads them yet.** They
exist for prediction accuracy, which is R9 and out of scope. Two honest choices:

| | archive growth | one year |
|---|---|---|
| keep all three feeds | ~910 MB/day | ~330 GB |
| pause the trip-updates feed | **~56 MB/day** | **~20 GB** |

That is a **16× difference in the thing that drives the bill.** Keeping them preserves
the option to build prediction accuracy from history that cannot be re-collected later;
pausing them makes this project cheap to run indefinitely. ADR-005 says raw data is kept
forever, which argues for keeping them — but ADR-005 was written when the archive was
megabytes. This is a decision for Daniel and the reviewer, not for me, and it is worth
making before a disk is paid for. A third option exists — keep collecting them but move
snapshots older than a month to object storage — and that is an architecture change,
so it needs the reviewer rather than a commit from me.

### The host: Hetzner Cloud, one server plus a volume

Chosen over Fly.io, Railway and Render for one reason that follows from the table
above: this workload is **a long-running collector with a large and steadily growing
disk**, which is the shape those three price least favourably and a plain VPS prices
best. It also keeps the archive and the database on the same machine, so rebuilding the
database by replaying the archive — hours of work — never crosses a network.

Verified from Hetzner's own product pages (specifications only — see the caveat below):

| plan | vCPU | RAM | included NVMe | included traffic |
|---|---|---|---|---|
| CX23 | 2 | 4 GB | 40 GB | 20 TB |
| CX33 | 4 | 8 GB | 80 GB | 20 TB |
| CX43 | 8 | 16 GB | 160 GB | 20 TB |

**CX33 (4 vCPU, 8 GB, 80 GB) plus a block-storage volume** is the recommendation. The
collector, the api, nginx and PostgreSQL together idle well under 8 GB; the 80 GB root
disk holds the operating system, the images and the database, and the archive goes on a
volume that can be grown as it fills. Starting at a 50 GB volume covers today's 16 GB
with about a year of headroom at the reduced growth rate, or four months at the full
one.

### What I could not verify, and will not invent

**I cannot give you a monthly figure I trust.** Hetzner renders every price on its
pricing pages in the browser, so the fetched pages carry the plan names, specifications
and included traffic but no amounts, and every plan currently reads "currently
unavailable" in the fetched text. Third-party guides disagree with each other — one
quotes CX23 at €3.99 rising to €5.49, another CX22 at €4.35, another $7.69 for a CX32 —
and several note a price increase during 2026 and a renaming of the CX line, so the
figures a search returns may describe retired plans. **Block storage price per GB is
not on the volumes page at all.**

Quoting one of those numbers as a cost would be exactly the mistake this project exists
to avoid. So the three things to check, which take a minute in the Hetzner Console:

1. the monthly price of **CX33** in EUR, including VAT and any IPv4 surcharge
2. the **volume price per GB per month**, and the minimum volume size
3. whether the shared-vCPU plans are actually orderable in the Nuremberg or Helsinki
   regions today

Multiply (2) by 50 GB, add (1), and that is the monthly cost of the plan above. My
expectation is that it lands in the region of €10–15 a month, and that is an
expectation, not a quote.

### What happens on Daniel's go

1. He creates the account and the server; I do not create anything that costs money.
2. `docker compose -f infra/docker-compose.prod.yml up -d --build`, which is already
   written and validated — all three images build, and the api image has been run and
   serves real data.
3. Prove the hard requirement on the host the same way it was proved locally:
   `docker compose -f infra/docker-compose.prod.yml config | grep published` must print
   `web: published: "80"` and nothing else. **PostgreSQL must not be reachable from the
   internet on any host — no `ports:` key, no public bind, no exception.**
4. Start the 24-hour observation and report `/api/coverage` for that period. Above 99%
   is the brief's bar for done, and it is the last unmet one.

## The two options the brief allows

| | A — one small VPS | B — managed PostgreSQL plus a VPS |
|---|---|---|
| cost | about €10/month | free database tier, about €5/month for the VM |
| operating | one machine, one compose file | two providers, two dashboards |
| the database | yours to back up and patch | backed up and patched for you |
| the risk | a disk failure takes everything | the free tier's limits are someone else's decision |

**A is the better fit for this project**, for one reason that has nothing to do with
cost: the archive and the database have to live together. The archive is the
irreplaceable asset — 150,000+ snapshots, and everything else is derived from it — and
replaying a week of it into the database takes hours. Splitting the two across
providers means the replay crosses the internet. `infra/docker-compose.prod.yml`
implements A.

For B, point `POSTGRES_HOST` and `POSTGRES_PORT` at the managed instance and drop the
`postgres` and `backup` services. Nothing else changes, which is the point of those
two variables.

## Before the first deploy

1. **A host with Docker.** Two things need to be true of it and they are easy to get
   wrong:
   - its firewall allows only 22, 80 and 443 inbound
   - it is not the same machine as anything else that matters
2. **`.env` on the host**, beside the clone, containing `POSTGRES_USER`,
   `POSTGRES_PASSWORD` and `POSTGRES_DB`. Generate the password; do not reuse the
   local one.
   ```bash
   cp .env.example .env
   # POSTGRES_PASSWORD=$(openssl rand -base64 32) — then chmod it
   chmod 600 .env
   ```
   `.env` is git-ignored and must stay that way. No password has a default anywhere in
   this repo, so a missing variable fails loudly at startup rather than quietly
   starting with a known one.
3. **TLS in front of port 80.** The compose stack serves plain HTTP on 80; put Caddy
   or nginx with Let's Encrypt in front before pointing a domain at it.

## Running it

```bash
git clone https://github.com/Dremo-drizzy/bus-truth.git
cd bus-truth
# create .env as above, then:
docker compose -f infra/docker-compose.prod.yml up -d --build
```

That builds three images from source and starts five services. The collector begins
polling within a minute; the site is on port 80 immediately, with the live map empty
until the first positions land.

The schedule is not loaded automatically, because loading it is a decision about which
publication is in force (ADR-007, ADR-015):

```bash
python scripts/load_gtfs.py        # needs POSTGRES_* in the environment
```

Then derive arrivals and punctuality for a date range:

```bash
docker compose -f infra/docker-compose.prod.yml run --rm \
  -e POSTGRES_HOST=postgres -e POSTGRES_PORT=5432 \
  collector java -jar /app/app.jar --from=2026-10-04 --to=2026-10-10
```

## What makes this safe to put on the internet

**PostgreSQL publishes no port.** Not `127.0.0.1:5432`, not a firewalled 5432 —
nothing. It is reachable only as the host `postgres` on the private compose network,
by the collector, the api and the backup job. The api publishes nothing either; nginx
proxies to it over the same network. **Port 80 on the `web` container is the only door
into the stack.**

This is a correction of a real mistake, not a precaution. The streaming version of
this project published 5432 and an **unauthenticated Kafka UI on 8080**: anyone who
found the host could read and write the database and the queue. Running that compose
file on a public VPS would have been a breach within hours.

Three more things the production file does and the development one does not:

- **every service runs as a non-root user** inside its image
- **`restart: unless-stopped` everywhere**, and `depends_on: service_healthy` for
  anything that talks to the database, because Flyway against a still-initialising
  PostgreSQL fails and restart-loops
- **a nightly `pg_dump`** at 03:15 UTC, gzipped, kept a fortnight, written to a
  temporary name and renamed on success — an interrupted dump is not a backup, and a
  corrupt backup that looks healthy is worse than none

## Verifying a deploy

```bash
curl -s localhost/api/coverage | head          # the pipeline's own record
curl -s localhost/api/vehicles/live | head     # positions arriving
docker compose -f infra/docker-compose.prod.yml ps   # all healthy
ls -la infra/backups/                          # a dump within 24 hours
```

The brief's bar for done is **the collector running unattended for 24 hours with
coverage above 99%.** `/api/coverage` is the measurement: it reports snapshots
captured against the 4,320 a day of 20-second polling produces. Below 99% means the
collector restarted or the host throttled it, and the figures for that day rest on
part of the day.

## What is not done

- **Nothing is deployed.** No host exists yet.
- **No domain, no TLS certificate.** Both wait on the host.
- **The 24-hour unattended run has not happened**, so the coverage-above-99%
  requirement is unverified. It cannot be checked from a laptop that sleeps — which is
  precisely why collection has been at 73.7% here, and why hosting it is the last
  stage rather than an optional one.
