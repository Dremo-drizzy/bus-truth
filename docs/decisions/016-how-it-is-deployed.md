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
