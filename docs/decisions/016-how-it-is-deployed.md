# 016 — How it is deployed

> **NEEDS DANIEL'S REVIEW** — written by Claude Code; the reasoning is not yet the
> author's own.
>
> **The host choice is Daniel's and is not made here.** The brief leaves it to him on
> the day; `docs/DEPLOY.md` sets out both options and recommends one. What this record
> fixes is the shape of the deployment, which is a security question rather than a
> preference.

Context: the streaming version's compose file published PostgreSQL on 5432 and an
unauthenticated Kafka UI on 8080. On a laptop behind a router that was merely untidy.
On a public VPS it would have been a breach within hours: anyone who found the host
could read and write the database and the queue, with no credential needed for the UI.
So before anything is hosted, the deployment needs a shape that cannot be run
carelessly.

Options considered:

- **A — one compose file for both development and production**, with ports and
  passwords differing by environment variable.
- **B — two compose files**: `docker-compose.yml` for development, which publishes the
  database because psql and the Java modules run on the host, and
  `docker-compose.prod.yml` which publishes nothing but the site.
- **C — production left undefined**, documented as steps for a human to follow.

Decision: **B**.

Why: the difference between the two environments is not a value, it is a topology, and
a value is too easy to get wrong. In development the database *must* be reachable from
the host — that is how `psql`, the processor and the backfill all work. In production it
must not be reachable at all. Expressing that as `POSTGRES_PORT=` and hoping is how a
database ends up on the internet; expressing it as the **absence of a `ports:` key** is
a property of the file that no missing variable can undo.

The production file therefore publishes exactly one port, 80, on the `web` container.
The api, the database and the backup job publish nothing and are reachable only over
the private compose network. nginx proxies `/api` to the api service, which also keeps
the frontend same-origin and means this project has no CORS configuration anywhere.

A verification, because a claim like that should not rest on reading the file:

```
$ docker compose -f infra/docker-compose.prod.yml config | grep published
web: published: "80"
```

Three further properties of that file:

- **Non-root users** in all three images Bus Truth builds.
- **`restart: unless-stopped`**, with `depends_on: condition: service_healthy` for
  anything that talks to the database — Flyway against a still-initialising PostgreSQL
  fails, and the container then restart-loops rather than recovering.
- **A nightly `pg_dump`** at 03:15 UTC, gzipped, kept a fortnight, written to a
  `.partial` name and renamed only on success. The database is derived data and could
  be rebuilt from the archive, but a week's replay takes hours, and a dump that exists
  beats a recovery procedure that is only theoretical. An interrupted dump left under
  its final name would be worse than no dump at all.

What I gave up:

- **One file to maintain.** Two files drift; the comments in each say what the other is
  for, and the development file is now explicit that its published 55432 is the reason
  it must never be the one deployed.
- **A single `docker compose up` that also loads the schedule and derives arrivals.**
  Both are deliberate commands: loading the schedule is a decision about which
  publication is in force (ADR-007, ADR-015), and deriving arrivals is a decision about
  a date range. Neither should happen because a container restarted.

Revisit if: a second host joins (a separate database, per option B in `DEPLOY.md`), at
which point the private network stops being a boundary and the database needs TLS and
its own firewall rules rather than the absence of a published port.

## Amendment: Render, not a VPS, and what the free tier costs

Daniel's decision, and it changes the host rather than the shape. The compose stack and
the Hetzner plan stay in the repository because they still describe how to run the whole
system — including the collector — on one machine. What is deployed is narrower:

| | on Render | not on Render |
|---|---|---|
| the site | static build, free | — |
| the API | web service from `infra/api.Dockerfile`, free | — |
| the derived tables | Postgres, free | |
| the collector | | stays on the laptop |
| the archive | | stays on the laptop, ~16 GB and growing |

**Nothing in the cloud writes to `data/`.** That is what makes a free tier viable: the
cloud holds a copy of four derived tables, about 70 MB, every row of which can be
rebuilt from the local archive by replaying it. There is no disk to pay for, no cron
job, and no loader.

### What the free tier actually costs

- **The database expires 30 days after creation**, with a 14-day grace period to
  upgrade before Render deletes it and its contents. When it goes the site breaks and
  the figures have to be restored again — a 10–30 minute `pg_dump`/`psql` round trip
  (`docs/DEPLOY.md`, step 4). Nothing irreplaceable is lost, because the archive is not
  there.
- **Free web services sleep** after about 15 minutes idle, and cold-start in roughly 12
  seconds. For a portfolio link sent to an interviewer that is the real cost, not RAM.
- **Memory is not the constraint.** Measured in the same image under a hard 512 MB
  limit with the blueprint's JVM flags: 188 MiB idle, 194 MiB under 150 concurrent
  requests, 300 MiB peak at JVM startup. 59% of the limit at its worst.

### Two things the split hosting forced, and they are worth noting

**CORS exists now.** It did not before: behind nginx or the Vite proxy the site fetched
`/api/...` from its own origin, and this project could honestly say it had no CORS
configuration anywhere. A static site and a separate API service are two hostnames, so
the permission has to be granted — one origin, injected from the other service by the
blueprint, GET only, no credentials, and no mapping registered at all when no origin is
configured (`WebOriginConfig`). A wildcard would let anybody's page present these
numbers as their own.

**The API now understands `DATABASE_URL`.** A Render blueprint can inject a database's
connection string, user, password and database name, but **not its host or port** — the
internal hostname is generated at creation time, so a JDBC URL cannot be assembled in
the blueprint. `DatabaseUrl` translates the `postgres://` string at startup, before the
context is built. Local development is untouched: without the variable nothing fires and
the `POSTGRES_HOST`/`POSTGRES_PORT` defaults still point at the compose database on
55432.

### The hard requirement, on this host

PostgreSQL must not be reachable from the public internet. On Render that is not an
absent `ports:` key but an **Access Control** list, and it starts open because the
one-time restore has to come in from outside. So the requirement becomes a step with a
proof: after loading the data, empty the allowed list and demonstrate that the external
connection string fails while the site keeps working (`docs/DEPLOY.md`, step 6). The
API's path is unaffected — it uses the internal connection string over Render's private
network, which access control does not govern.

This is weaker than the compose version, and the difference should be stated plainly
rather than glossed: with compose, the database had no door at all; here it has a door
that is closed and can be reopened from a dashboard.

Revisit if: the free database expires and is not renewed, in which case the site needs
either a paid database or a different host — or the collector gets hosted too, at which
point the archive needs object storage (ADR-018) and this record is no longer the whole
picture.
