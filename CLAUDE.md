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
   - the arrival-detection rule inside `PositionBasedDetector` (`processor`)
   - the on-time window
   - the four constants in `docs/BUILD-BRIEF.md` under "Daniel's decisions"
   - the About/methodology page text
   - the README findings section
4. **No new dependencies, frameworks or modules** without asking first and explaining why.
5. **Architecture changes:** if a task seems to need one, stop and say **"ARCHITECTURE CHANGE — needs senior review"** with a one-paragraph rationale. Do not implement it.
6. **Decisions get recorded.** For any non-trivial choice, suggest a record in `docs/decisions/NNN-title.md` using the template below. Daniel writes it in his own words.
7. **Decision logic gets unit tests** — anything that decides on-time, bunched, matched, ghost. Prefer real captured `.pb` fixtures over invented data.
8. **Never commit secrets.** Config goes in environment variables; keep `.env.example` current.
9. **Check understanding.** After finishing a task, ask Daniel two short questions about what was just built. Wait for his answers before starting anything else. If an answer is wrong, explain that point again and ask the question again.
10. **Commits:** one logical change each; imperative subject line; the body says *why*.

## Architecture — do not change without senior review

```
gtfs.halifax.ca ──[HTTP, 20s]──► collector (Java)
                                      ├──► data/raw/*.pb.gz     the archive, forever
                                      └──► PostgreSQL           raw_vehicle_positions

static GTFS zip ──[Python]──────────► PostgreSQL gtfs_* tables  the schedule

PostgreSQL ──► processor (Java) ──► arrival_events ──► ontime_events
                                                            │
                                                   api (Spring Boot)
                                                            │
                                      web (Vite + React + React Router)
```

Modules:

```
common/      shared immutable domain records
collector/   polls the GTFS-RT feeds; archives raw .pb.gz and writes Postgres raw_* tables
processor/   raw → arrival_events → ontime_events (re-runnable per date range)
api/         REST API + OpenAPI
web/         Vite + React + React Router
scripts/     Python utilities: save_feeds.py, load_gtfs.py
infra/       docker-compose (PostgreSQL only)
docs/        decisions/, stages/, BUILD-BRIEF.md, defence-notes.md
```

Accepted decisions (see `docs/decisions/`):

- Maven multi-module monorepo.
- ~~Kafka sits between collector and database~~ — superseded by ADR-012 (batch, no queue).
- ~~One entity per Kafka message, JSON, keyed by vehicle or trip~~ — superseded by ADR-012.
- ~~Offsets committed after the DB write~~ — superseded by ADR-012. The insert stays
  idempotent on its natural key, which is what the guarantee rested on.
- Raw data is kept forever; everything else is derived and re-derivable.
- Arrival detection lives in Java (processor); metrics live in SQL (dbt).
- Static GTFS is versioned by `feed_version` and never overwritten.
- All timestamps are stored as UTC `timestamptz`; service dates are computed in `America/Halifax`. GTFS times can exceed `24:00:00`.

## Conventions

- **Java 21 LTS**, current stable Spring Boot, Maven. Base package `io.github.dremo_drizzy.bustruth.<module>`.
- Records for immutable domain objects. Constructor injection only — no field injection.
- Database migrations: Flyway only. Never edit a migration that has already been applied — add a new one.
- Frontend: Vite + React + React Router, TypeScript `strict`, MapLibre GL pinned to ^5.
- Tests: JUnit 5 + AssertJ; Testcontainers for integration tests.
- **Any test that boots the Spring context must set
  `bustruth.collector.polling-enabled=false`.** The default is on, which is right for
  production and wrong for a test: the three pollers otherwise fetch live data from
  the agency and write rows underneath the test's own assertions. That is how CI
  failed with 159 rows before a replay and 233 after, and it passed locally only
  because a network call happened to be slow.
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

**Phase R · Stage R8 — cloud and ship.** Governed by `docs/BUILD-BRIEF.md` and
`docs/BUILD-BRIEF-AMENDMENT.md`; where they disagree the amendment wins.

Done: R0 (fresh repo) · R1 (batch, no queue) · R2 (static GTFS loaded) · R3 (trip-id
join rule) · R4 (arrival detection, and why 17.5% is its ceiling) · R5 (on-time
events) · R6 (six API endpoints) · R7 (Vite + React, five pages) · R8 (deployment
built and validated — **not deployed**: the host is Daniel's choice, see
`docs/DEPLOY.md`).

R9 (prediction accuracy) stays out of scope.

Everything marked **NEEDS DANIEL'S REVIEW** is waiting on him, and the markers are the
to-do list for the curriculum phase: the arrival rule and the four constants (ADR-014),
the trip-id join (ADR-013), the schedule match (ADR-015), the streaming-to-batch
decision (ADR-012), the README findings section, and the method page text.
`docs/defence-notes.md` has 40 questions and no answers, deliberately.
