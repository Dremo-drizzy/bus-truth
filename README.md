# Bus Truth

![Live map of Halifax Transit vehicles](docs/images/live-map.png)

Bus Truth measures how reliable Halifax Transit actually is. It records the agency's
live GTFS-Realtime feeds every 20 seconds and keeps every snapshot forever, derives
from those positions the moment each bus reached each stop, compares that against the
timetable published for that day, and publishes the gap.

Both sides of the comparison are the agency's own data. Nothing here is a survey or an
estimate of how a journey felt.

> Contains information licenced under the Open Government Licence – Halifax.

## What this measures, and what it cannot

**It measures:** on-time performance per route, per hour of the local day, per service
date and per stop, against the schedule publication in force on that date; the delay
distribution behind each percentage; and its own collection coverage, day by day.

**It cannot measure what the feed does not show, and that is the central limitation.**
Halifax reports a bus's stop number about **6.8 times across a 38.3-stop trip**, so
roughly **17.5%** of scheduled stop-events have an observed arrival. The other 82.5%
were served and never mentioned. They are counted, never interpolated, and never
reported as missed service.

Two further honest limits, both measured and both below in [data
quality](#data-quality): the published figure moves **17.5 percentage points** on our
own definition of the moment of arrival, carries an **8.7-point** margin of error after
that definition was calibrated, and the two data sources disagree about stop numbering
on **21.5%** of arrivals.

Bunching, ghost buses and prediction accuracy are designed and not built. Prediction
accuracy needs the trip-updates loader, which is sized and deferred for a measured
reason — see [what is not built](#what-is-not-built).

## Architecture

```
gtfs.halifax.ca ──[HTTP, every 20s]──► collector (Java) ──► data/raw/*.pb.gz   the archive, forever
                                            │
                                            └────────────► PostgreSQL          raw_vehicle_positions
static GTFS zip ──[Python]───────────────────────────────► PostgreSQL          gtfs_* tables

PostgreSQL ──► processor (Java) ──► arrival_events ──► ontime_events
                                                            │
                                                api (Spring Boot)
                                                            │
                                      web (Vite + React + React Router)
```

There is no queue. One was removed, with its reasoning kept: see
[ADR-012](docs/decisions/012-replace-streaming-with-batch.md).

| module | what it does |
|---|---|
| `common` | shared immutable records — the shapes the services agree on |
| `collector` | polls the three feeds, archives the raw bytes, writes `raw_*` tables, owns the Flyway schema, and replays the archive into the database |
| `processor` | positions → `arrival_events` → `ontime_events`, re-runnable per date range |
| `api` | six REST endpoints plus OpenAPI |
| `web` | five pages: live map, leaderboard, route, stop, method |
| `scripts` | `save_feeds.py` (the standalone archiver), `load_gtfs.py`, join and fixture checks |
| `infra` | compose for local PostgreSQL, and the production stack |
| `docs` | decision records, stage write-ups, deployment, defence notes |

Every non-trivial choice is recorded in [docs/decisions](docs/decisions/). The ones
marked **NEEDS DANIEL'S REVIEW** were written to unblock the build and are still to be
rewritten by their author.

## Running it

Requires Docker, JDK 21 and Node 24.

```bash
cp .env.example .env                  # then set a local password
docker compose -f infra/docker-compose.yml up -d --wait

# PostgreSQL is published on 55432, not 5432 — see docs/stages/stage-3.md
psql -h localhost -p 55432 -U bustruth -d bustruth

export POSTGRES_PASSWORD=...          # no password has a default, anywhere

./mvnw -pl common -am install -DskipTests
./mvnw -f collector/pom.xml spring-boot:run      # :8081 — polls, archives, migrates
python scripts/load_gtfs.py                      # the published schedule
./mvnw -f api/pom.xml spring-boot:run            # :8084

cd web && npm ci && npm run dev                  # :5173, /api proxied to :8084
```

Deriving the measurements for a date range, from positions already collected:

```bash
./mvnw -pl common,processor -am package -DskipTests
java -jar processor/target/processor-0.1.0-SNAPSHOT.jar     --server.port=0 --from=2026-10-04 --to=2026-10-10     # detection, then on-time
```

If the database is emptier than the archive — which it will be, if collection ran
before the database did — replay the archive through the live write path:

```bash
./mvnw -pl common,collector -am package -DskipTests
java -jar collector/target/collector-0.1.0-SNAPSHOT.jar     --bustruth.collector.polling-enabled=false     --backfill-from=2026-10-04 --backfill-to=2026-10-10
```

The site is at `http://localhost:5173`, the API at `http://localhost:8084/api/coverage`,
and its OpenAPI document at `http://localhost:8084/v3/api-docs`.

Tests: `./mvnw verify` runs everything, including integration tests that start a real
PostgreSQL 18 through Testcontainers, so Docker must be running. Any test that boots
the Spring context must set `bustruth.collector.polling-enabled=false` — otherwise the
pollers fetch live data and write rows underneath the test's own assertions, which is
how CI once failed with 159 rows before a replay and 233 after.

Deploying it: [docs/DEPLOY.md](docs/DEPLOY.md). PostgreSQL publishes no port there, and
[ADR-016](docs/decisions/016-how-it-is-deployed.md) says why that is a property of the
compose file rather than a value in it.

## Findings

> **NEEDS DANIEL'S REVIEW** — written by Claude Code to unblock the build. Every number
> here is measured and reproducible; the choice of what matters is his.

**Route 63 collapses after 21:00.** On 9 October it ran 29.2% on time across 24
observed arrivals — the worst of the 54 routes with enough observations to rank — and
its hourly breakdown shows where: 2 of 4 on time at 18:00, 3 of 6 at 19:00, 2 of 5 at
20:00, then **0 of 5, 0 of 3 and 0 of 1** through to midnight. The failure is a time of
day, not a route.

**The best and worst routes by percentage are an artefact of how little we saw them.**
Ranked on share alone, the top three routes that day had **eleven, four and two**
observed arrivals and the bottom two had one each. Published that way the leaderboard
would rank routes by their own sample size. This is why no percentage leaves the API
without its denominator, and why the site says "too few observations" rather than
printing a number from a handful of rows.

**The agency's realtime feed and its published schedule disagree about their own
trips.** They name the same stop for the same stop number on 78.5% of matched arrivals;
on the rest the feed runs one to three places ahead, because its trips contain stops the
published zip does not list. Both are the agency's own data, published weeks apart.

**4.4% of live trip ids cannot join the schedule at all** without stripping a suffix
the schedule has never used.

**The honest headline is about us, not the buses.** On the same week's data, the
on-time figure is **40.9%** or **58.4%** depending on which end of a stop's reporting
window counts as the arrival — **17.5 points** on an internal definition, with no bus
behaving differently. The definition was then settled against the agency's own
`STOPPED_AT` positions, leaving a measured **8.7-point** margin of error. Any single
number from this project should be read with both figures in mind, which is why both
are on the method page permanently.

## Data quality

### The feed shows 6.8 of a trip's 38.3 stops

The single most important number in this project, and it bounds everything else.
Halifax Transit's realtime feed reports a vehicle's `current_stop_sequence`
**correctly** — it agrees exactly with the published schedule's numbering — but it only
*updates* that value about **6.8 times across an average 38.3-stop trip**: 17.8% of the
stops. One observed vehicle reported "at stop 20" for eight minutes, then "at stop 34".
The twelve stops in between were served and never mentioned.

So arrival detection derives an arrival for **17.5%** of scheduled stop-events — and
17.5% against 17.8% visible is the proof that the detector extracts essentially
everything the feed exposes. **The ceiling is the data source, not the code.** Polling
five times faster would fetch the same stale value five times as often.

**The unobserved stops were served, not missed.** The buses called at them; the agency
simply did not report it. Nothing here presents them as missed service, and the
counter is named `stopsUnobserved` for that reason.

### Collection coverage

Collection has run since **24 September 2026** and is ongoing. The archive holds
**152,036 realtime snapshots** across the three feeds plus **22 daily captures** of the
static schedule.

Coverage is **incomplete and uneven**, and that is stated rather than hidden: the
archiver runs on a laptop, so it stops when the machine sleeps or is closed.

| | vehicle positions |
|---|---|
| days captured | 22 (24 September – 15 October 2026) |
| snapshots | 50,925+ |
| coverage against a continuous 20-second capture | **73.7%** |
| best day | 99.9% |
| worst day | 42.4% |

Gaps are not interpolated or filled. A missing snapshot is simply absent, and the
detector refuses to derive arrivals across one — without that rule a sleeping laptop
would be indistinguishable from a cancelled trip, and the project would publish
invented cancellations.

**Every percentage this project publishes carries the count it was computed from.** A
percentage over eleven arrivals is not a percentage, and the API returns the
denominator so the frontend can say so instead of printing a number.

### The published figure, and what it rests on

**58.4% of arrivals were on time across 4–10 October 2026** — 63,991 of 109,584
arrivals matched to a published time, out of 110,439 derived. 11.1% were more than a
minute early and 30.5% more than five minutes late.

The week is the figure. A single day is not representative of it:

| service date | on time | median delay |
|---|---|---|
| Sun 4 Oct | 53.6% of 16,359 | +176s |
| Mon 5 Oct | 60.8% of 13,750 | +121s |
| Tue 6 Oct | 46.9% of 18,634 | +209s |
| Wed 7 Oct | 58.5% of 21,818 | +125s |
| Thu 8 Oct | 57.1% of 6,064 | +101s |
| Fri 9 Oct | 64.2% of 14,583 | +100s |
| Sat 10 Oct | 68.3% of 18,376 | +70s |
| **week** | **58.4% of 109,584** | **+123s** |

The spread between the best and worst day is **21.4 points** — wider than the
17.5-point definitional gap and wider still than the 8.7-point margin of error. Quoting
any single day as "Halifax's on-time performance" would have been quoting a number with
more day-to-day variation in it than methodology.

### What "arrived" means here, and the two margins that come with it

The feed holds one stop number for minutes at a time, so each stop is reported as a
**window** of positions rather than a moment, and an arrival has to be timed from one
end of it or the other. This site takes the **end** of the window.

The same week, derived both ways, changing nothing else:

| 4–10 October 2026, 109,584 matched arrivals | start of window | **end of window** (used here) |
|---|---|---|
| **on time** | 40.9% | **58.4%** |
| early | 35.5% | 11.1% |
| late | 23.6% | 30.5% |

**17.5 percentage points**, and not one bus behaved differently between those columns.
Both stay published, permanently, because a reader who knows the figure is
definition-sensitive can judge it and one shown 58.4% as a bare fact cannot.

That is not a coin-flip, though. GTFS-Realtime has a field, `current_status`, which can say
`STOPPED_AT` — the agency asserting a bus is *at* a stop rather than us inferring it.
Halifax populates it on **0.40%** of positions, far too few to build the measurement
on, but enough to judge the two candidate definitions against:

| definition | median error against the agency's own `STOPPED_AT` |
|---|---|
| **end of window** (used here) | **61s late** |
| start of window | 544s early |

**The remaining bias is named, not corrected.** Removing that 61 seconds from every
arrival in the week would move the published figure from 58.4% to **49.7% — 8.7
points.**

The two margins mean different things and both are published:

- **17.5 points** is the gap between the two candidate definitions. It is a *finding*
  about how much the question "what counts as arriving?" is worth.
- **8.7 points** is the residual margin of error of the definition that won, after it
  was calibrated against the agency's own `STOPPED_AT` positions. It is the figure the
  observation floors below are derived from, because it is what the project still does
  not know rather than a choice it has already made.

Two honest limits on that calibration, both measured:

- **It can only be checked where the agency reports `STOPPED_AT`, which is at
  terminals.** 27 of the 45 stops that ever report it are terminals or bays, and those
  windows are a median 815 seconds wide against 32 seconds for windows in general. So
  the calibration sample is 25× wider than typical.
- **The bias grows with the width of the window**, which means the 61-second figure is
  measured on the worst cases rather than the normal ones:

  | window width | median bias of the window's end |
  |---|---|
  | under 60s | **0s** |
  | 60–180s | 30s |
  | 180–420s | 30s |
  | 420–900s | 32s |
  | over 900s | **211s** |

  Read together: at an ordinary stop the end of the window is close to the arrival; at a
  terminal where a bus lays over for ten minutes, it is minutes late. The pooled 61
  seconds is published because it is the figure that can be checked, and it is
  conservative for a typical stop rather than optimistic.

It is **not** a handful of buses: 234 of the 366 vehicles ever seen report `STOPPED_AT`
at least once, so this is the fleet, not one firmware version.

**And the reconciliation, which matters more than either number:** the two definitions
sit at opposite ends of a window that is often minutes wide, yet the median delay
differs by only 74 seconds between them while the headline moves 8.7 points. Both are
true, because **50.5% of the windows that produce an arrival contain a single
position** — for half of all arrivals the two definitions are *the same timestamp*. The
movement comes almost entirely from the other half. Without that sentence the two
figures look like a contradiction; with it, they are the finding.

See [ADR-014](docs/decisions/014-the-arrival-rule-and-the-on-time-window.md).

### How few observations is too few

A single observation moves a percentage by 100/n points. This project publishes an 8.7
point margin of error, so a figure where one bus swings it further than that is
describing its own sample size:

- **a percentage is shown** only over **12** or more arrivals
- **a route or stop is ranked** only over **46** or more — a quarter of the margin,
  because placing one route above another is a stronger claim than printing its number

Both are computed from the 8.7 in code and served to the site from `/api/meta`, so the
floors cannot drift from the figure that justifies them. Over a week they cost little:
75 of the 81 observed routes clear 46, and the median route has 302 arrivals. See
[ADR-017](docs/decisions/017-how-few-observations-is-too-few.md).

### The two sources number the same trip's stops differently

The live feed and the published zip agree on which stops a trip serves and disagree
about their order: on 9 October they named the same stop for the same sequence number
on only 78.5% of 6,525 matched arrivals, with the feed's number running one to three
places ahead on the rest, because the feed's trip contains stops the published zip does
not list. Matching an arrival to its scheduled time by sequence number — the obvious
join — would have compared a fifth of all arrivals against a neighbouring stop's time, a
median of 94 seconds out and up to 16 minutes, against a five-minute on-time window.
The match is therefore made on the stop itself, and every row records the disagreement.
See [ADR-015](docs/decisions/015-matching-an-arrival-to-its-published-time.md).

### Two further measured limits

- **4.4% of live trip ids carry an `_N` suffix** from the agency's trip-modification
  extension, and none of the 9,239 trip ids in the published schedule contains an
  underscore. Stripping the suffix recovers the join in every observed case, and those
  arrivals are marked with lower confidence because the recovery rests on an
  unverified assumption. See [ADR-013](docs/decisions/013-trip-id-join-rule.md).
- **`currentStatus`, which would say `STOPPED_AT`, is present on 11 of 69 vehicles.**
  It is the field arrival detection would ideally use, and a rule built on it would
  measure which buses report it rather than how the service ran — which is why
  detection reads the stop sequence instead (ADR-014).
- **Service alerts are near-static**: the content of the alerts feed changed on 0 of
  180 consecutive polls, so it carries no signal at this sampling rate and is archived
  rather than analysed.

## What is not built

**Bunching and ghost buses.** Both are designed and neither is built. Bunching needs
the same `ontime_events` rows grouped by stop and headway, which is a SQL question
rather than a new pipeline. Ghost buses — scheduled trips that never ran — are harder
than they look: a trip absent from the feed is indistinguishable from a trip that ran
while the collector was asleep, so the claim cannot be made honestly until coverage is
high and continuous. That is the strongest argument for hosting it.

**Prediction accuracy**, comparing the agency's own predicted arrival times against the
observed ones. Deferred for a measured reason, and the reason is the design: keyed on
`(trip_id, feed_timestamp, stop_sequence)`, trip updates write every stop-time update on
every poll — about 34,000 rows per snapshot, 4,320 snapshots a day, which is unusable.
Keyed instead on `(trip_id, stop_sequence, service_date, predicted_arrival_at)` with
`on conflict do nothing`, a row is written only when the prediction *changes*: about
6.1M rows a day, measured. Workable, still large, and that dedupe key would be the whole
design. See [docs/stages/stage-3.md](docs/stages/stage-3.md).

The agency's own `delay` field is not a shortcut to it: it is present on 34.8% of
stop-time updates, from 0% overnight to about 70% late evening, so using it as a
fallback would introduce a bias that varies by time of day.

**A deployment.** The compose stack, the images, the backup job and the security
properties are written and validated; no host exists yet, so the brief's bar — the
collector running unattended for 24 hours above 99% coverage — is unmet. On a laptop
that sleeps it cannot be met, which is the point.

**The decision records marked NEEDS DANIEL'S REVIEW**, and the answers in
[docs/defence-notes.md](docs/defence-notes.md). Both are deliberate: the markers are a
to-do list, and answers written by anyone but their author would make the file look
finished when it is not.

## Attribution

Contains information licenced under the Open Government Licence – Halifax.

Map data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, tiles by
[OpenFreeMap](https://openfreemap.org/).
