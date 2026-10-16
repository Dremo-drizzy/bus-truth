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

- **The brief's option B — a managed PostgreSQL tier — is dead, and not only the free
  one.** Supabase's free tier is 500 MB of database storage and Neon's is about 0.5 GB
  per project; the database is already **353 MB** after sixteen partly-collected days
  and grows about **7.5 GB a month**. It would exhaust either free tier within the first
  week of real collection — and Supabase's Pro tier, at $25 a month, tops out at 8 GB
  per project, which is roughly one month of growth. Paying more than the whole VPS
  costs to run out of room by Christmas is not a trade worth making.
  ([Supabase pricing](https://www.activepieces.com/blog/supabase-pricing-free-tier-limits-pro-costs-egress.md),
  [Neon pricing](https://neon.com/pricing) — the free storage figure is from
  third-party summaries, as Neon's own page did not state it in the text I could
  retrieve.)
- **Archiving all three feeds on any affordable host is a dead end.** At 910 MB a day,
  a 160 GB disk is about 143 days and a 40 GB one is six weeks. This is what ADR-018
  settles, and it is why the recommendation below needs no block-storage volume at
  all.

### The feed that drives the bill, and the ruling on it

**94% of the archive is trip updates**, at a mean 202.4 kB a snapshot against 3.8 kB
for vehicle positions. They are also the agency's own *predictions* and the only
possible basis for R9 — and October 2026's predictions cannot be collected in December.

**They keep being collected. Nothing is paused and nothing is deleted** (the senior
reviewer's ruling, and it is right). But the arithmetic rules out archiving them on the
host: 130 GB of usable disk at 910 MB a day is **about 143 days**, before PostgreSQL
and before the existing 16 GB. With only positions and alerts — 6% of the bytes, about
55 MB a day — the same disk lasts over five years.

So the host archives positions and alerts, and trip updates stay on the laptop until
the archive can be written to object storage. That is scoped at a day and a half,
mostly because the archive is not write-only — `ArchiveBackfill` replays it off the
filesystem, so a remote archive needs a remote read path or replay silently stops
working. Over the reviewer's one-day threshold, so it waits.
[ADR-018](decisions/018-where-the-archive-lives-once-it-is-hosted.md) has the estimate,
the fallback and what the fallback costs. The switch is already built:
`bustruth.collector.trip-updates.enabled=false` on the host.

### The host: Hetzner CPX31 in Ashburn, not Germany

The first version of this plan put the site in Nuremberg, which was wrong for a reason
that has nothing to do with money: **this is a Halifax transit site built for Canadian
employers to look at.** Serving it from Germany adds roughly 100 ms to every request
for exactly the audience that matters.

Verified from Hetzner's own plan pages: the **CX** line and the EU **CPX** plans exist
only in `FSN1`/`NBG1`/`HEL1` (and `SIN1`). The North American locations are `ASH1`
(Ashburn, Virginia) and `HIL1` (Hillsboro, Oregon), and they carry a separate CPX
series:

| plan | vCPU | RAM | NVMe | included traffic | locations |
|---|---|---|---|---|---|
| CPX21 | 3 AMD | 4 GB | 80 GB | 2 TB | HIL1, ASH1 |
| **CPX31** | **4 AMD** | **8 GB** | **160 GB** | **3 TB** | **HIL1, ASH1** |
| CPX41 | 8 AMD | 16 GB | 240 GB | 4 TB | HIL1, ASH1 |

**CPX31 in ASH1** is the plan: four AMD vCPUs, 8 GB, and a 160 GB NVMe disk — double
the disk of the EU CX33 this replaces, about 1,000 km from Halifax instead of 6,000.
The traffic allowance drops from 20 TB to 3 TB, which this project will not approach: a
GeoJSON response for 240 vehicles is about 40 kB, so 3 TB is tens of millions of map
refreshes a month.

No separate volume is needed under ADR-018. At ~55 MB of archive a day the 160 GB disk
is years of runway, and a volume can be added later without moving anything.

### The price: pending, and deliberately not guessed

**The monthly figure is not obtainable from the public site, and I am not going to
average third-party guesses into a number that looks like a fact.** Every amount on
Hetzner's Cost-Optimized and Regular Performance pages renders empty — `from /month`
with nothing in it — and every plan simultaneously reads "currently unavailable". Both
symptoms have one cause: the price and stock data load only after their cookie banner
is accepted, so neither is a finding about availability. The senior reviewer checked
this independently in a browser and got the same result.

Third-party guides disagree with each other (€3.99, €4.35, $7.69 for comparable plans)
and several report both a 2026 price rise and a renaming of the CX line, so a searched
figure may be describing a retired plan.

**Where the real number comes from:** signing up is free, and the console and the
Hetzner Cloud API both show live prices per plan per location. Three things to read
there, which takes a minute:

1. **CPX31 in ASH1**, monthly, including VAT and any IPv4 surcharge
2. whether an IPv4 address is charged separately
3. whether CPX31 is actually orderable in ASH1 today

My expectation is that it lands near the low tens of euros a month. **That is an
expectation, not a quote**, and no figure goes in the README until it is read from the
console.

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
