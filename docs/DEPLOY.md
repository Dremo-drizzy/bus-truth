# Deploying Bus Truth

Everything here is built and validated. **Nothing has been deployed** — the host, the
accounts and the domain are Daniel's to choose, and the brief leaves the choice to him
on the day. This is what to run once that choice is made.

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
