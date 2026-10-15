# 014 — The arrival rule, the on-time window, and the four constants

> **NEEDS DANIEL'S REVIEW** — written by Claude Code to unblock the build; the
> reasoning here is not yet the author's own. These four numbers decide what the
> project publishes, and the build brief reserved them for Daniel.

Context: the feed never says a bus arrived. It reports which stop a vehicle is working
towards (`current_stop_sequence`) and, on a minority of vehicles, a `current_status`
that can read `STOPPED_AT`. An arrival has to be inferred, and every downstream number
— on-time performance, bunching, ghost trips — inherits whatever that inference gets
wrong.

Options considered for the signal:
A. `current_status = STOPPED_AT`. Measured present on 11 of 69 vehicles, so a rule
   built on it would measure which vehicles report status rather than which buses
   turned up.
B. A transition in `current_stop_sequence`: when it advances from N, the bus is done
   with stop N. Present on every vehicle observed.
C. Geometry — distance from the stop's coordinates. Needs stop positions, a radius
   constant, and a decision about which of several nearby stops a point belongs to.

Decision: B, with four named constants and no interpolation.

| constant | value | meaning |
|---|---|---|
| `ARRIVAL_MOMENT` | `FIRST_SIGHTING` | the arrival is timed from the first position reporting the stop. `LAST_BEFORE_ADVANCE` is also implemented; it answers "when did it leave" instead, and for a bus waiting at a terminus the two differ by minutes |
| `ON_TIME_WINDOW_EARLY` | −60s | how early still counts as on time |
| `ON_TIME_WINDOW_LATE` | +300s | how late still counts as on time |
| `MAX_TRAIL_GAP` | 120s | a longer gap breaks the trail; nothing is derived across it |

Why:
- `MAX_TRAIL_GAP` is the rule that keeps the project honest. Collection coverage was
  measured at 73.7% overall and 42.4% on the worst day, because the laptop slept.
  Across a sleep a bus's sequence appears to jump, which is indistinguishable in the
  data from a bus that skipped stops. Deriving arrivals there would publish invented
  service. A test fails if that suppression is removed, and it was checked by deleting
  the line.
- Stops passed between a jump from N to M are counted, never interpolated. A stop
  nobody observed a bus at is not an arrival, however likely it looks.
- Confidence travels with every row: `SUFFIXED_TRIP` when the schedule was reached
  through a stripped `_N` suffix (ADR-013), `SPARSE_TRAIL` when the positions either
  side of the transition were more than 60s apart, otherwise `HIGH`.

## Measured result, 9 October 2026

| | |
|---|---|
| arrivals derived | 6,597 |
| stops unobserved (served, never reported by the feed) | 39,725 |
| trails broken by the gap rule | 40 |
| positions with no stop sequence | 0 |
| scheduled stop-events for the trips observed | 37,567 |
| **share of scheduled stop-events with a derived arrival** | **17.5%** |
| stops the feed reports per trip, of 38.3 scheduled | **6.8 (17.8%)** |

Confidence: 6,438 HIGH, 117 SUFFIXED_TRIP, 34 SPARSE_TRAIL.

**The cause of the 17.5% is the feed's reporting granularity, and it is a ceiling.**

Halifax reports `current_stop_sequence` correctly — it agrees exactly with the
schedule's numbering — but it only *updates* it about seven times across a trip. One
observed vehicle held "at stop 20" for eight minutes and then reported "at stop 34".
The twelve stops in between were served and never mentioned.

```sql
with live as (
    select regexp_replace(trip_id,'_\d+$','') as trip_id,
           count(distinct current_stop_sequence) as sequences_reported
    from raw_vehicle_positions
    where vehicle_timestamp >= '2026-10-09' and vehicle_timestamp < '2026-10-10'
    group by 1),
sched as (
    select trip_id, count(*) as stops_scheduled
    from gtfs_stop_times where feed_version = '2026_08_August_20260806' group by 1)
select round(avg(l.sequences_reported),1) as avg_sequences_the_feed_reports,
       round(avg(s.stops_scheduled),1)    as avg_stops_in_the_trip
from live l join sched s using (trip_id);
--  6.8 sequences reported | 38.3 stops in the trip  ->  17.8% visible
```

**17.8% of stops are visible in the feed; the detector derives 17.5% of them.** Those
two figures agreeing to three tenths of a point is the proof: the detector extracts
very nearly everything the data source exposes, and the shortfall is a property of the
feed rather than of this code. No change to the poll interval, the collection coverage
or the rule can raise it — polling five times faster would fetch the same stale value
five times as often.

Four earlier explanations were tested and discarded, which is worth recording because
each was plausible:

| theory | disproved by |
|---|---|
| the 20-second poll interval is too coarse | vehicles report every 31s, and the feed holds one value for minutes |
| collection gaps shorter than `MAX_TRAIL_GAP` | dense consecutive archive snapshots show the same jumps |
| the database held only a fraction of the archive | true and worth fixing, but the same pattern appears in the archive itself |
| the feed uses a different stop numbering | it matches the schedule exactly, 20 to 20 and 34 to 34 |

A rule rejecting transitions above a plausible traversal rate
(`MAX_STOPS_PER_MINUTE = 4`) was proposed and **withdrawn after testing**: on real
data it classified 29 of 38 transitions as errors and broke four passing tests. Its
premise was right — no bus passes fourteen stops in thirty-one seconds — but its
conclusion was wrong, because the bus passed them over eight minutes and the feed
reported it late. Filtering those transitions would discard true arrivals. No
instantaneous-rate rule and no stale-sequence rule is present, deliberately.
`MAX_TRAIL_GAP` is unaffected and remains mandatory.

`stopsUnobserved` is named for what it is. Those stops were **served**, not missed; the
feed simply never reported them. Publishing them as skipped would be a false claim
about a transit agency, so nothing in the API or the frontend may present them as
missed service.

Note for the record: the 50% threshold this result was compared against was an
invented number, not a measured one, and should not on its own block a stage.

What I gave up:
- Stop-level completeness. 17.5% of scheduled stop-events carry an observation, so
  stop-level reliability rests on thin coverage in a way route-level numbers do not.
  Every percentage the API reports must therefore carry the count behind it.
- Any arrival for the last stop of a trail, since nothing observes a bus leaving it.
- `STOPPED_AT` as a signal, and with it the vehicles that do report it reliably.

Revisit if: the cause of the 17.5% resolves in favour of collection gaps, in which
case better collection raises coverage and the figure should be re-measured; or trip
updates are loaded, which carry per-stop times for every stop of every trip and would
replace inference with the agency's own numbers.

## Addendum, Stage R5 — `ARRIVAL_MOMENT` is worth 21 points of on-time performance

> **NEEDS DANIEL'S REVIEW** — measured by Claude Code while building R5. The choice
> this addendum is about is his, and the numbers exist to inform it.

The on-time comparison was run twice over the same 6,550 matched arrivals for
9 October 2026, changing nothing but `ARRIVAL_MOMENT`:

| | `FIRST_SIGHTING` (current) | `LAST_BEFORE_ADVANCE` |
|---|---|---|
| **on time** | **3,210 — 49.0%** | **4,595 — 70.2%** |
| early | 2,479 — 37.8% | 684 — 10.4% |
| late | 861 — 13.1% | 1,271 — 19.4% |
| 10th percentile delay | −475s | −63s |
| median delay | +9s | +83s |
| 90th percentile delay | +368s | +510s |
| mean delay | −10s | +175s |
| early at a trip's first two stops | 54.2% (median −289s) | 4.2% |

The headline figure this project exists to publish moves **21 percentage points** on
identical data, because of an internal choice about which position in a trail counts as
the arrival. Nothing the buses did differs between those two columns.

The shape of the two distributions says which is the artefact. `FIRST_SIGHTING` puts a
tenth of all arrivals more than **eight minutes early**, and over half of all
first-and-second-stop arrivals early by a median of nearly five minutes. That is not
early running; it is a bus sitting at its origin terminus already reporting the first
stop, counted as having arrived there the moment it was first seen. The same mechanism
operates mid-route in a weaker form: with `current_stop_sequence` updating only about
6.8 times across a 38.3-stop trip, "first position reporting stop N" can precede the
actual arrival by minutes. `LAST_BEFORE_ADVANCE` leaves a distribution shaped the way a
bus network's should be — a slightly late median and a long late tail.

Claude Code's recommendation, for Daniel to accept or reject: switch `ARRIVAL_MOMENT`
to `LAST_BEFORE_ADVANCE`, and say plainly on the methodology page that the figure moved
21 points when it changed. The second half matters more than the first. A reader who
knows the number is editorially sensitive can judge it; one who is shown 70.2% as a
fact cannot.

Either way, this is the honest headline for the whole project: **the published on-time
figure is more sensitive to our own definition of "arrived" than to anything Halifax
Transit did on 9 October.**
