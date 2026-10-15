# Stage 3 — Loader (vehicle positions)

## Connecting to the project database

**Never type bare `psql` on this project.** It resolves to a native PostgreSQL install on
this machine, on port 5432, which is a different server with none of this data:

```
psql -h localhost -p 55432 -U bustruth -d bustruth
```

The container is published on **55432**, not 5432, because two native PostgreSQL services
(`postgresql-x64-15` and `postgresql-x64-17`) already hold that port. The clash is silent:
the container starts, Docker reports the port mapped, and a client on the host quietly
reaches the native server instead.

## What was built

One Flyway migration creating `raw_vehicle_positions`, one Kafka listener on
`hfx.vehicle-positions`, one writer using `JdbcClient`, and thirteen tests. Trip updates,
the stops table, alerts and the static GTFS loader are deliberately out of scope.

The primary key `(vehicle_id, vehicle_timestamp)` is the identity of an observation rather
than a surrogate. Delivery is at-least-once, so the key plus `ON CONFLICT DO NOTHING` is
what makes a redelivery a no-op; the loader never has to ask whether it has seen a message
before. It also serves the index the live map needs. `first_feed_timestamp` is named for
what it holds, because a later snapshot carrying the same observation is discarded along
with its timestamp. `kafka_partition` and `kafka_offset` record which delivery produced a
row, so during an incident "where did this row come from" is answerable.

## The replay proof

The loader consumed the topic backlog into 663 rows across 199 vehicles. The consumer group
was then rewound to offset 0 on all three partitions and every message reconsumed: **663
rows before, 663 after, and `max(inserted_at)` identical to the microsecond**. The timestamp
matters as much as the count — a count alone would also pass if rows had been deleted and
rewritten.

## Three traps, all of the same shape

**Flyway sat on the classpath and never ran.** Boot 4 moved auto-configuration into
per-technology modules, so `flyway-core` plus `flyway-database-postgresql` give the engine
with nothing to start it; `org.springframework.boot:spring-boot-flyway` is the missing
third artifact. The symptom is the dangerous kind: the application starts, health reports
`UP`, and the database stays empty. This is the second time this exact shape has appeared —
`spring-kafka` without `spring-boot-starter-kafka` was the first.

**`/actuator/health` cannot detect that failure.** Boot 4's Flyway module ships a
`FlywayEndpoint` and no health indicator, so health will never report a migration problem
whatever happens. `/actuator/flyway` is the runtime answer, and it is exposed in
application.yml for that reason. The real guard is the integration test: it truncates
`raw_vehicle_positions`, so if the migration had not run the test could not even start.

**Failsafe had to be bound explicitly.** Surefire runs `*Test` and ignores `*IT`, so
without the plugin binding the idempotency proof would have existed and never run.

## Tests

Thirteen: seven idempotency tests against a real PostgreSQL 18 under Testcontainers, five
deserialization tests, one context test. Postgres rather than H2, because `ON CONFLICT DO
NOTHING` is Postgres syntax and proving it on another engine proves nothing. The
deserialization tests cover the configuration most likely to break this service, where a
wrong `spring.json.value.default.type` presents as "the loader runs and stores nothing".
`org.testcontainers:postgresql` does not resolve under Boot 4: Testcontainers 2.x renamed
every module to `testcontainers-*`.

## Carried forward: suffixed trip ids will break trip matching

Live trip ids sometimes carry an `_N` suffix — `4659595_1`, `4660053_3` — the
modified-trip variants of the same Modifications extension that leaves 43% of trip
updates without a `trip.trip_id`. Two vehicles were observed on route 8 at the same
moment, one on `4659595` and one on `4659595_1`.

Measured against the archived static feed (2026-09-29, 9,239 trip ids in `trips.txt`, of
which **none contains an underscore**):

| feed | trip ids | suffixed `_N` | join to trips.txt | suffixed ids whose base id joins |
|---|---|---|---|---|
| vehicle positions | 114 | 5 (4.4%) | 109 (95.6%) | 5 / 5 |
| trip updates | 406 | 6 (1.5%) | 398 (98.0%) | 6 / 6 |

So a plain join on `trip_id` silently drops those trips, and a dropped trip looks exactly
like a ghost bus — the metric would report a service failure that never happened. Stripping
the suffix recovers the join in every observed case, but the fact that the trip was
*modified* must be kept rather than erased: a modified trip may not follow the stop times
its base id points at. Two trip-update ids failed to join for some other reason and are
not yet explained.

This belongs in the same decision record as the modified-trip handling.

## Known gap

`scripts/save_feeds.py` runs continuously; the collector does not. Kafka therefore receives
nothing between manual collector runs, so the loader has a backlog to read rather than a
stream. A live map needs collector, Kafka, loader, Postgres and api all running at once,
which has to be settled before the map is judged.
