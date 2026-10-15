# Bus Truth — rescope build brief

**For Claude Code. Save as `docs/BUILD-BRIEF.md` and commit it before starting.**

Daniel's decision, 14 October 2026: the streaming architecture is being replaced by
a batch one, and the project is finally building the feature it was designed for —
the gap between what Halifax Transit scheduled and what its buses actually did.

Work through the stages **in order**. Each stage has done criteria. Stop where the
brief says STOP and wait for Daniel.

---

## Why this rescope exists — read this first

The analysis is **historical, not real-time**. Comparing a month of archived feed
data against a published timetable needs a database and a query. It does not need a
streaming pipeline. Kafka was the correct tool for a problem this project does not
have.

The archive (`data/raw/**.pb.gz`, collecting since 24 September) is the
irreplaceable asset. Everything else is derived and rebuildable. Protect the
archive above all else.

## Rules that still apply

`CLAUDE.md` governs, with these additions and overrides:

1. **Human-first zones for this rescope.** Daniel writes the first draft, you
   review and improve — you do **not** write these from scratch:
   - the arrival-detection rule inside `PositionBasedDetector`
   - the on-time window
   - the four constants listed in "Daniel's decisions" below
   - the About/methodology page text
   - the README findings section
2. **No new dependencies** beyond those named in this brief without asking.
3. **Small commits**, imperative subject lines, body says *why*.
4. **No authorship trailers, no "Generated with" lines, no session links** in
   commits, PRs or file contents.
5. **Never commit secrets.** `${POSTGRES_PASSWORD}` keeps no default, anywhere.
6. After each stage, ask Daniel two short questions about what was built and wait
   for his answers.

---

## Daniel's decisions — leave these as named constants

Implement each as a clearly-named constant or configuration property with the
suggested starting value and a comment saying it is Daniel's to change. Do not bury
them in expressions.

| Constant | Suggested start | Meaning |
|---|---|---|
| `ARRIVAL_MOMENT` | `FIRST_SIGHTING` | which position marks the arrival: the first one reporting `stop_sequence = N`, or the last one before the sequence advances. Implement both; default to the first. |
| `ON_TIME_WINDOW_EARLY` | `-60` seconds | how early still counts as on time |
| `ON_TIME_WINDOW_LATE` | `+300` seconds | how late still counts as on time |
| `MAX_TRAIL_GAP` | `120` seconds | a gap longer than this breaks the trail; derive **no** arrivals across it |

`MAX_TRAIL_GAP` is not optional. Collection coverage was measured at 73.7% with a
worst day of 42.4%, because the laptop slept. Without this rule a sleeping laptop
is indistinguishable from a cancelled trip, and the project would publish invented
cancellations.

---

# Stage R1 — Remove Kafka, merge the loader into the collector

## Before anything is deleted

```
git tag v1-streaming
git push origin v1-streaming
```

Confirm the tag exists on the remote before continuing. This tag is what makes the
"two architectures, documented reason for moving between them" story provable.

Work on a branch `r1-remove-kafka`.

## Move into the collector — do NOT delete

| From | To |
|---|---|
| `loader/src/main/resources/db/migration/V1__create_raw_vehicle_positions.sql` | `collector/src/main/resources/db/migration/` |
| `loader/.../loader/store/VehiclePositionWriter.java` | `collector/.../collector/store/` |
| `loader/.../store/VehiclePositionWriterIT.java` | `collector/src/test/.../collector/store/` |
| `loader/.../TestcontainersConfig.java` | `collector/src/test/.../collector/` |
| `loader/.../loader/Fixtures.java` | merge into the collector's existing `Fixtures` |
| `loader/src/test/resources/fixtures/vehicle-positions.jsonl` | `collector/src/test/resources/fixtures/` |

`VehiclePositionWriterIT` is the single most valuable test in the repository — it
is the idempotency proof against a real PostgreSQL. It must still run and pass
after this stage.

## Change the collector

**`collector/pom.xml`**
- remove `spring-boot-starter-kafka`
- add `spring-boot-starter-jdbc`, `org.postgresql:postgresql` (runtime scope),
  `spring-boot-flyway`, `flyway-core`, `flyway-database-postgresql`
- add test scope: `spring-boot-testcontainers`,
  `org.testcontainers:testcontainers-postgresql`
- bind `maven-failsafe-plugin` to `integration-test` and `verify`, exactly as the
  loader's pom did. Without that binding the `*IT` tests silently never run.

**`FeedPoller<T>`** — the template method survives. Replace the Kafka publish step
with a store step:

- remove the `KafkaTemplate` field and constructor parameter
- remove `protected abstract String topic();`
- remove `protected abstract String keyOf(T record);` — a key only ever existed to
  choose a Kafka partition, so it has no job now
- add `protected abstract void store(List<T> records);`
- `pollOnce()` stays `final`. The sequence stays: fetch → archive → decode →
  content-hash skip → map → store. The `catch (Exception)` stays. Archive still
  happens before decode.

**Subclass `store()` implementations**
- `VehiclePositionPoller` — insert each record via `VehiclePositionWriter`
- `TripUpdatePoller` — **no-op for now**, with a comment: trip updates are
  archived but not loaded; see Stage R9
- `ServiceAlertPoller` — **no-op**, with a comment: alerts are archived; nothing
  queries them

Keep all three pollers. The archive is worth more than the database rows and costs
nothing to maintain.

**`CollectorConfig`** — delete `collectorKafkaTemplate` and the `KafkaProperties`
import. Keep `feedHttpClient` and `rawArchiver`.

**`collector/src/main/resources/application.yml`**
- delete the entire `spring.kafka` block
- delete `topic:` from all three feeds under `bustruth.collector`
- add, matching the loader's old settings exactly:
  ```yaml
  spring:
    datasource:
      url: jdbc:postgresql://localhost:55432/${POSTGRES_DB:bustruth}
      username: ${POSTGRES_USER:bustruth}
      password: ${POSTGRES_PASSWORD}      # no default, deliberately
    flyway:
      enabled: true
  ```

## Delete

- the whole `loader/` module, and its `<module>` entry in the parent `pom.xml`
- the `kafka` and `kafka-ui` services from `infra/docker-compose.yml`, and the
  `kafka-data` volume
- `analytics/` entirely

## Fix three statements that are not true

- `scripts/README.md` advertises a gtfs-loader that does not exist. It will exist
  after Stage R2 — rewrite it to describe only `save_feeds.py` for now.
- `CLAUDE.md` lines 40–43 draw a gtfs-loader, `arrival_events`, dbt and metric
  marts. Replace the diagram with the one in "Target architecture" below.
- `CLAUDE.md`'s module list still names `loader/` and `analytics/`.

## Also update CLAUDE.md

- the accepted-decisions list: strike the three Kafka decisions, note that ADR-012
  supersedes them
- the "Current stage" section: Phase R · Stage R1
- rule 3's human-first zones: replace with the list at the top of this brief

## Target architecture (use this diagram)

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

## Done when

- `./mvnw verify` passes, `VehiclePositionWriterIT` included
- `docker compose -f infra/docker-compose.yml up -d --wait` starts PostgreSQL only
- the collector runs, writes `.pb.gz` files, and inserts rows into
  `raw_vehicle_positions`
- the api and the map still work end to end
- CI is green

**STOP after the moves, before the deletions.** Report what moved and that
`./mvnw verify` still passes. Then continue.

---

# Stage R2 — Load the published schedule

A Python script in `scripts/load_gtfs.py` that downloads
`https://gtfs.halifax.ca/static/google_transit.zip`, reads it, and loads it into
versioned tables.

## Dependencies

`psycopg[binary]` only. Use the standard library's `zipfile` and `csv` for the
rest. Add `scripts/requirements.txt`.

## Migration `V2__create_gtfs_tables.sql`, in the collector

```sql
create table gtfs_feed_versions (
    feed_version  text primary key,
    loaded_at     timestamptz not null default clock_timestamp(),
    source_sha256 text not null,
    source_bytes  bigint not null
);

create table gtfs_routes (
    feed_version text not null references gtfs_feed_versions,
    route_id text not null,
    route_short_name text,
    route_long_name text,
    route_type integer,
    primary key (feed_version, route_id)
);

create table gtfs_stops (
    feed_version text not null references gtfs_feed_versions,
    stop_id text not null,
    stop_code text,
    stop_name text,
    stop_lat double precision,
    stop_lon double precision,
    location_type integer,
    parent_station text,
    primary key (feed_version, stop_id)
);

create table gtfs_trips (
    feed_version text not null references gtfs_feed_versions,
    trip_id text not null,
    route_id text not null,
    service_id text not null,
    direction_id smallint,
    trip_headsign text,
    shape_id text,
    primary key (feed_version, trip_id)
);

create table gtfs_stop_times (
    feed_version text not null references gtfs_feed_versions,
    trip_id text not null,
    stop_sequence integer not null,
    stop_id text not null,
    -- TEXT, not time: GTFS permits times past 24:00:00 for trips that run after
    -- midnight, e.g. "25:10:00". Converting here would reject valid data.
    arrival_time text,
    departure_time text,
    pickup_type integer,
    drop_off_type integer,
    primary key (feed_version, trip_id, stop_sequence)
);

create table gtfs_calendar (
    feed_version text not null references gtfs_feed_versions,
    service_id text not null,
    monday boolean, tuesday boolean, wednesday boolean, thursday boolean,
    friday boolean, saturday boolean, sunday boolean,
    start_date text not null,
    end_date text not null,
    primary key (feed_version, service_id)
);

create table gtfs_calendar_dates (
    feed_version text not null references gtfs_feed_versions,
    service_id text not null,
    date text not null,
    exception_type integer not null,
    primary key (feed_version, service_id, date)
);

create index gtfs_stop_times_trip_idx on gtfs_stop_times (feed_version, trip_id);
create index gtfs_trips_route_idx on gtfs_trips (feed_version, route_id);
```

## Script behaviour

- read `feed_version` from `feed_info.txt`; if absent, fall back to the SHA-256 of
  the zip (and log that it did)
- if that `feed_version` already exists, **do nothing and say so** — never
  overwrite a loaded version
- load inside one transaction: either the whole version lands or none of it
- use `COPY` for `stop_times`, which is the large one
- print a per-table row count at the end

## Done when

- the script runs twice; the second run reports the version is already loaded and
  changes nothing
- `select count(*) from gtfs_stop_times` returns a plausible number (expect
  hundreds of thousands)
- `scripts/README.md` describes both scripts accurately

**STOP.** Report the row counts per table, and the `feed_version` loaded.

---

# Stage R3 — The trip-id join, measured before anything depends on it

The single highest-risk thing in this rescope. Do it as its own stage so the number
is known before the detector is built on top of it.

Already measured by Daniel: **4.4%** of live trip ids carry an `_N` suffix (e.g.
`4659595_1`), and **none** of the 9,239 schedule trip ids contains an underscore.

## Build `scripts/check_trip_join.py`

Report, for the loaded `feed_version` against `raw_vehicle_positions`:

1. distinct live `trip_id` values
2. how many join `gtfs_trips.trip_id` directly
3. how many join after stripping a trailing `_\d+`
4. how many do not join either way — with twenty examples
5. the same four counts as a percentage of **rows**, not just distinct ids

## Then decide and record

If stripping the suffix recovers almost all of them, that is the join rule, and it
goes in an ADR with this caveat stated plainly:

> Stripping the suffix assumes a modified trip visits the same stops in the same
> order as the trip it modifies. That is unverified. Arrivals derived from
> suffixed trip ids are marked with lower confidence.

Add a `confidence` column to `arrival_events` in Stage R4 carrying that
distinction.

## Done when

the numbers are printed, and `docs/decisions/013-trip-id-join-rule.md` exists —
**Daniel writes it**, in his own words.

**STOP.** Report all five figures and wait.

---

# Stage R4 — The arrival detector

The heart of the project. **Daniel writes the rule; you write everything around
it.**

Work in the `processor` module, which is currently an empty scaffold.

## Shapes

```java
// common/
public record ArrivalEvent(
        String tripId,
        String stopId,
        int stopSequence,
        LocalDate serviceDate,
        Instant observedArrivalAt,
        String method,        // "POSITION_SEQUENCE"
        String confidence) {  // "HIGH" | "SUFFIXED_TRIP" | "SPARSE_TRAIL"
}

// processor/
public interface ArrivalDetector {
    List<ArrivalEvent> detect(List<VehiclePosition> trail);
}

public class PositionBasedDetector implements ArrivalDetector { ... }
```

An interface with one implementation is deliberate: it names the seam where a
second detector would go, and keeps the class testable without a framework.

## The rule — Daniel's first draft, your review

Starting point to hand him, which he changes:

1. Group positions by `(vehicleId, tripId, startDate)`. Sort by `timestamp`.
2. Walk consecutive pairs. If the gap exceeds `MAX_TRAIL_GAP`, break the trail
   here and derive nothing across the break.
3. When `currentStopSequence` advances from N to M where M > N, emit an arrival at
   the stop reported at N, timed per `ARRIVAL_MOMENT`.
4. If M > N+1, emit **nothing** for the sequences in between. Count them.
5. Ignore positions with a null `currentStopSequence`.
6. `confidence` is `SUFFIXED_TRIP` when the trip id carried an `_N` suffix,
   `SPARSE_TRAIL` when the pair's gap exceeded 60s but not `MAX_TRAIL_GAP`,
   otherwise `HIGH`.

Note for Daniel: `currentStatus` is present on only 11 of 69 vehicles, so
`STOPPED_AT` cannot be the primary signal. Sequence transitions can.

## Migration `V3__create_arrival_events.sql`

```sql
create table arrival_events (
    trip_id text not null,
    stop_id text not null,
    stop_sequence integer not null,
    service_date date not null,
    observed_arrival_at timestamptz not null,
    method text not null,
    confidence text not null,
    detected_at timestamptz not null default clock_timestamp(),
    primary key (trip_id, service_date, stop_sequence)
);

create index arrival_events_stop_idx on arrival_events (stop_id, service_date);
```

Re-running a date range must be idempotent: delete that range's rows, then insert.
Expose it as `POST /processor/run?from=&to=` plus a CLI argument, and make it safe
to run twice.

## Tests — this is the teaching stage, so make them exemplary

Build fixtures from the **real archive**, not invented data. Generate
`processor/src/test/resources/fixtures/` from `data/raw` with a small script, and
commit the fixtures.

Write at least these, each named for the behaviour it protects:

- `emitsOneArrivalPerStopSequenceTransition`
- `derivesNothingAcrossAGapLongerThanMaxTrailGap`
- `skipsIntermediateStopsWhenTheSequenceJumps`
- `marksSuffixedTripIdsWithLowerConfidence`
- `ignoresPositionsWithNoStopSequence`
- `rerunningTheSameDateRangeProducesIdenticalRows`
- `handlesATrailThatEndsMidTrip`
- `handlesAVehicleReassignedToAnotherTripMidDay`

Every test must fail if its behaviour is removed. Before finishing, delete one line
of detector logic, confirm a test goes red, and restore it. Report which test
caught it.

## Done when

- `./mvnw -pl processor verify` is green
- running it over one real day produces arrivals, and Daniel has hand-checked
  **twenty** of them against the raw positions
- the count of derived arrivals, skipped stops and broken trails is printed per run

**STOP.** Report the three counts and wait for Daniel's hand-check.

---

# Stage R5 — Scheduled versus actual

## Migration `V4__create_ontime_events.sql`

```sql
create table ontime_events (
    trip_id text not null,
    stop_id text not null,
    stop_sequence integer not null,
    service_date date not null,
    route_id text,
    direction_id smallint,
    observed_arrival_at timestamptz not null,
    scheduled_arrival_at timestamptz not null,
    delay_seconds integer not null,
    on_time boolean not null,
    confidence text not null,
    feed_version text not null,
    primary key (trip_id, service_date, stop_sequence)
);

create index ontime_events_route_idx on ontime_events (route_id, service_date);
create index ontime_events_stop_idx on ontime_events (stop_id, service_date);
```

## The join, and the one hard part in it

`gtfs_stop_times.arrival_time` is text and may read `25:10:00`. A scheduled instant
is therefore:

> `service_date` at midnight **in America/Halifax**, plus the hours, minutes and
> seconds parsed from that text as a plain duration.

So `25:10:00` on 2026-10-14 means 01:10 local on 2026-10-15. Do **not** use
Postgres's `time` type — it rejects hours above 23. Parse the three parts and add
them as an interval.

Daylight saving: adding an interval to a `timestamptz` at a zone boundary is where
this breaks. Write a test for a scheduled arrival on the night the clocks change.

`delay_seconds` is `observed - scheduled`, negative for early.
`on_time` is `delay_seconds between ON_TIME_WINDOW_EARLY and ON_TIME_WINDOW_LATE`.

Match the `feed_version` that was in effect on that `service_date`, not simply the
newest one.

## Done when

- one day's `ontime_events` is populated
- Daniel has hand-checked five rows against the published timetable
- a DST test passes
- the number of arrivals that could **not** be matched to a schedule is reported,
  not silently dropped

**STOP.** Report the match rate and the unmatched count.

---

# Stage R6 — The API

Six endpoints in the existing `api` module. Keep `springdoc` 3.1.1. Keep
`spring.flyway.enabled: false` — the collector owns the schema.

| Endpoint | Returns |
|---|---|
| `GET /api/vehicles/live` | unchanged, already built |
| `GET /api/routes` | `route_id`, short and long name — for dropdowns |
| `GET /api/routes/performance?from=&to=` | every route: trips observed, on-time %, median delay, p90 delay. Ranked. |
| `GET /api/routes/{routeId}/performance?from=&to=` | that route: on-time % by hour of day, by day, and its ten worst stops |
| `GET /api/stops/{stopId}/reliability?from=&to=` | that stop: arrivals observed, on-time %, median delay, by route |
| `GET /api/coverage` | per day: snapshots captured, snapshots expected, coverage %, arrivals derived |

Every response that reports a percentage must also report the **count it was
computed from**. A percentage over eleven arrivals is not a percentage, and the
frontend needs the denominator to say so.

`/api/coverage` is not decoration. It publishes the project's own data gaps, which
is the difference between an honest measurement and a dashboard.

## Tests

Integration tests with Testcontainers, following `LiveVehiclesIT`'s pattern. At
minimum: each endpoint's happy path; an empty date range; a route with too few
observations to report a percentage.

## Done when

`./mvnw -pl api verify` is green, all six endpoints respond, and
`/v3/api-docs` lists them.

---

# Stage R7 — The frontend: Vite, React, React Router, five pages

**Replace Next.js.** Daniel knows React, not Next.js.

```
npm create vite@latest web -- --template react-ts
```

Add `react-router-dom`, `maplibre-gl` pinned to `^5.24.0`, and `recharts` for the
charts. Nothing else.

Dev proxy in `vite.config.ts`:

```ts
server: { proxy: { "/api": "http://localhost:8084" } }
```

## Porting the map

`web/app/LiveMap.tsx` is already about 95% plain React — `useEffect`, `useRef`,
`useState`, and nothing Next-specific but `"use client"` and the CSS import. Port
it as-is, keeping:

- the default-export-only MapLibre import, with the comment explaining why
- `width: 100%; height: 100%` on `.map`, with the comment about
  `maplibre-gl.css` winning the cascade
- the four-state `Status` union and all three notices

Those three things each cost a debugging session. The comments stay.

## The five pages

| Route | Page | Content |
|---|---|---|
| `/` | Live map | the ported map, unchanged |
| `/routes` | Leaderboard | every route ranked by on-time %, with its observation count |
| `/routes/:routeId` | Route detail | on-time % by hour (bar chart), by day (line chart), ten worst stops |
| `/stops/:stopId` | Stop reliability | on-time % and median delay, broken down by route |
| `/about` | Methodology | **Daniel writes this text** |

Every page needs loading, error and empty states, and must work at phone width.

Anywhere a percentage is shown, show its denominator. Where the denominator is
below a threshold, show "too few observations" instead of a number.

## The About page must state

The on-time window and why that window. What arrival detection does and cannot do.
The trip-id suffix problem and its caveat. Measured coverage, and that gaps are
never interpolated. That this is not an official Halifax Transit product. The
licence attribution:

> Contains information licenced under the Open Government Licence – Halifax.

## CI

Replace the Next build job with Vite: `npm ci` then `npm run build`, which
type-checks.

## Done when

all five pages render against real data, the build passes in CI, and nothing scrolls
sideways at 400px.

---

# Stage R8 — Cloud, and ship

## Hosting

Two options. Daniel decides on the day.

- **Managed PostgreSQL** (Neon or Supabase free tier) plus the collector on a small
  VPS. Free database, roughly €5/month for the VM.
- **One small VPS** running the compose stack. Roughly €10/month, one thing to
  operate.

Either way: secrets as environment variables, restart policies, a nightly
`pg_dump`, and **PostgreSQL must not be reachable from the internet.** The old
compose file published 5432 and an unauthenticated Kafka UI on 8080; neither may
be exposed on a public host.

## Documentation

- **ADR-012** — replacing streaming with batch. **Daniel writes it.** Context,
  options, decision, why, what was given up, revisit if.
- **ADR-013** — the trip-id join rule, from Stage R3.
- **ADR-014** — the arrival rule and the on-time window. Daniel's.
- **README** — rewrite: what it does and does not measure, the new architecture
  diagram, screenshots of all five pages, how to run it, the findings section
  (**Daniel's**), limitations, attribution.
- **`docs/defence-notes.md`** — rewrite the questions against the new
  architecture. Daniel answers them.

## Done when

a stranger can clone and run it from the README alone, the hosted collector has run
unattended for 24 hours, and coverage for that period is above 99%.

---

# Stage R9 — Stretch, only if ahead

**Prediction accuracy.** Load `raw_trip_updates` and compare the agency's own
predicted arrival times against observed arrivals, bucketed by how far ahead the
prediction was made.

**Deferred for a measured reason, and this reason matters:** keyed on
`(trip_id, feed_timestamp, stop_sequence)`, trip updates write every stop-time
update on every poll — roughly 34,000 rows per snapshot, 4,320 snapshots a day.
That is unusable. Keyed instead on
`(trip_id, stop_sequence, service_date, predicted_arrival_at)` with
`ON CONFLICT DO NOTHING`, a row is written only when the **prediction changes**,
which Daniel measured at about 6.1M rows a day. Workable, still large.

If this stage happens, that dedupe key is the whole design and it needs its own
ADR.

---

# Summary of stop-and-explain points

| After | Report |
|---|---|
| R1 moves, before deletions | what moved; `./mvnw verify` still green |
| R2 | row count per `gtfs_*` table, and the `feed_version` |
| R3 | all five join figures |
| R4 | arrivals derived, stops skipped, trails broken — then wait for the hand-check |
| R5 | schedule match rate, and the unmatched count |
| R6 | the six endpoints responding |
| R7 | five pages rendering |

At every one of those, ask Daniel two questions about what was just built and wait
for his answers before continuing. That rule is in `CLAUDE.md` and it is the point
of the whole exercise.
