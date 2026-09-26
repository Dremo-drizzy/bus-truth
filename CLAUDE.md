# CLAUDE.md — Bus Truth

## What this project is

Bus Truth measures how reliable Halifax Transit actually is. It records the live GTFS-Realtime feeds continuously, compares what buses actually did against the published schedule and the live predictions, and publishes the gap.

Five features: live map · route on-time performance · bunching · ghost buses (scheduled trips that never ran) · prediction accuracy.

This is a portfolio project. **The owner (Daniel) must be able to explain and defend every line in an interview.** Optimise for his understanding, not for speed.

## Who's who

- **Daniel — owner.** Makes every decision. Writes the first draft of the core logic. Must understand everything that gets merged.
- **You (Claude Code) — pair programmer.** Implement, explain, test, review Daniel's drafts.
- **Senior reviewer — separate.** Owns the architecture and reviews each stage through a pull request. The architecture below is theirs; don't change it without review.

## Rules of engagement

1. **Small steps.** One logical change at a time. Stop after each and explain what you did before moving on.
2. **Explain every change:** what it does, why, and what the alternatives were. Assume Daniel is learning the tool as well as the code.
3. **Human-first zones.** Do NOT write these from scratch. Ask Daniel to write the first draft, then review it and suggest improvements. You may write the tests, plumbing, config and boilerplate around them.
   - Stage 1 raw-feed saver script (`scripts/`)
   - the `FeedPoller<T>` abstraction design (`collector`)
   - database table designs / Flyway DDL
   - the "latest position per vehicle" query
   - trip matching and `PositionBasedDetector` (`processor`)
   - the on-time rule and the ghost-trip rule (`analytics`)
   - the README findings section and the About/methodology page text
4. **No new dependencies, frameworks or modules** without asking first and explaining why.
5. **Architecture changes:** if a task seems to need one, stop and say **"ARCHITECTURE CHANGE — needs senior review"** with a one-paragraph rationale. Do not implement it.
6. **Decisions get recorded.** For any non-trivial choice, suggest a record in `docs/decisions/NNN-title.md` using the template below. Daniel writes it in his own words.
7. **Decision logic gets unit tests** — anything that decides on-time, bunched, matched, ghost. Prefer real captured `.pb` fixtures over invented data.
8. **Never commit secrets.** Config goes in environment variables; keep `.env.example` current.
9. **Check understanding.** After finishing a task, ask Daniel two short questions about what was just built. Wait for his answers before starting anything else. If an answer is wrong, explain that point again and ask the question again.
10. **Commits:** one logical change each; imperative subject line; the body says *why*.

## Architecture — do not change without senior review

```
GTFS-RT feeds (3, ~20s) ─► collector (Java) ─► Kafka ─► loader (Java) ─► PostgreSQL raw_* tables
GTFS static zip ─► gtfs-loader (Python) ─► PostgreSQL gtfs_* tables (versioned)
PostgreSQL ─► processor (Java) ─► arrival_events ─► analytics (dbt) ─► metric marts
metric marts ─► api (Spring Boot REST) ─► web (Next.js + MapLibre)
```

Modules:

```
common/      shared immutable domain records
collector/   GTFS-RT → Kafka; archives raw .pb bytes
loader/      Kafka → Postgres raw tables (idempotent)
processor/   raw → arrival_events (re-runnable per date range)
api/         REST API + OpenAPI
analytics/   dbt project
web/         Next.js app
scripts/     Python utilities
infra/       docker-compose, DB init
docs/        decisions/ and stages/
```

Accepted decisions (see `docs/decisions/`):

- Maven multi-module monorepo.
- Kafka sits between collector and database. At-least-once delivery; the loader dedupes with unique keys + `ON CONFLICT DO NOTHING`; offsets are committed after the DB write.
- One entity per Kafka message, JSON. Key = `vehicle_id` for positions, `trip_id` for trip updates.
- Raw data is kept forever; everything else is derived and re-derivable.
- Arrival detection lives in Java (processor); metrics live in SQL (dbt).
- Static GTFS is versioned by `feed_version` and never overwritten.
- All timestamps are stored as UTC `timestamptz`; service dates are computed in `America/Halifax`. GTFS times can exceed `24:00:00`.

## Conventions

- **Java 21 LTS**, current stable Spring Boot, Maven. Base package `io.github.dremo_drizzy.bustruth.<module>`.
- Records for immutable domain objects. Constructor injection only — no field injection.
- Kafka topics: `hfx.vehicle-positions`, `hfx.trip-updates`, `hfx.alerts`.
- Database migrations: Flyway only. Never edit a migration that has already been applied — add a new one.
- dbt layers: `staging` → `intermediate` → `marts`. Every model gets tests.
- Frontend: Next.js App Router, TypeScript `strict`, MapLibre GL.
- Tests: JUnit 5 + AssertJ; Testcontainers for integration tests.
- Git: one branch per stage, `stage-N-short-name`; a PR to `main` at the end of each stage.

## Data sources

| Feed | URL |
|---|---|
| Static GTFS | https://gtfs.halifax.ca/static/google_transit.zip |
| Vehicle positions | https://gtfs.halifax.ca/realtime/Vehicle/VehiclePositions.pb |
| Trip updates | https://gtfs.halifax.ca/realtime/TripUpdate/TripUpdates.pb |
| Service alerts | https://gtfs.halifax.ca/realtime/Alert/Alerts.pb |

Licence: Open Government Licence – Halifax. Required attribution, in the README and the site footer:
> Contains information licenced under the Open Government Licence – Halifax.

## Decision record template

```
# NNN — <decision>
Context: what forced a choice
Options considered: A / B / C
Decision:
Why:
What I gave up:
Revisit if:
```

## Current stage

**Phase A · Stage 0 — Setup.** Update this line at the start of each stage.
