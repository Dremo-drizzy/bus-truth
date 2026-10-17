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

## Settled by measurement: `ARRIVAL_MOMENT` is the end of the window

The senior reviewer refused to lock the constant on the strength of the comparison
above, and was right to: both columns are endpoints of the same window, and neither
being obviously better is not the same as one of them being correct. He proposed a
third option that would not be an inference at all — GTFS-Realtime's `current_status`,
which can say `STOPPED_AT`, the agency asserting the bus is *at* the stop rather than
us deducing it — on condition that it be measured first rather than adopted because he
suggested it.

**It is measured, and it cannot be the rule.** Across 966,775 stored positions:

| `current_status` | positions | share |
|---|---|---|
| field absent | 846,750 | 87.58% |
| `INCOMING_AT` | 116,236 | 12.02% |
| `STOPPED_AT` | 3,898 | **0.40%** |

`IN_TRANSIT_TO` never appears explicitly; the GTFS-RT spec makes it the default when the
field is absent, so the 87.58% is silence rather than an assertion. Read against derived
arrivals the figure is worse still: of the 6,589 arrivals on 9 October, only **13
(0.20%)** have any `STOPPED_AT` position at that stop. A rule built on it would measure
the fraction of the fleet that reports it, which is the same mistake the original rule
avoided by not using `currentStatus` in the first place.

Checked against the archive rather than only the database, in case the collector's
mapper was dropping the field: 24 snapshots from 9 October, 2,103 vehicle entities,
86.78% absent / 12.84% `INCOMING_AT` / 0.38% `STOPPED_AT`. The database matches the
protobuf, so the sparsity is the agency's.

### But it is enough to referee the two endpoints

0.40% is useless as a rule and perfectly adequate as ground truth. Where the agency
does say `STOPPED_AT`, that is its own claim about when the bus was at the stop, and
both candidate definitions can be scored against it.

First, the shape of the problem, measured. Because the feed holds one stop sequence for
minutes, each stop is reported as a *window* of positions:

| | |
|---|---|
| stop windows containing an agency `STOPPED_AT` ping | 4,072 |
| where that ping falls within the window | median **80% through**, mean 71%, p75 94% |

The bus is at the stop near the **end** of its window, not its middle and not its
start — which follows from the spec's own semantics: under `IN_TRANSIT_TO` the stop
sequence names the stop being *approached*, so the window is the approach plus the
dwell, and the arrival sits at its far end.

Scored directly, on the 597 windows where a `STOPPED_AT` time and a full window are
both available:

| definition | median error | p90 | mean |
|---|---|---|---|
| **end of window** (`LAST_BEFORE_ADVANCE`) | **61s late** | 664s | 217s |
| start of window (`FIRST_SIGHTING`) | **544s early** | 936s | 646s |

**Decision: `ARRIVAL_MOMENT = LAST_BEFORE_ADVANCE`.** It is nine times closer to the
agency's own assertion at the median. Its residual bias is **late by a median of 61
seconds**, and that is stated rather than corrected: a correction factor derived from
597 samples of a 0.40% subset would be a guess wearing a number's clothes, and an
unmeasured correction is worse than a named bias.

`FIRST_SIGHTING` stays implemented. The 21-point gap between them is a published
finding, and anyone disputing the headline should be able to re-run the range the other
way and see it.

### One number that looked wrong and was not

The two definitions differ by a whole window, and the median window is minutes wide — so
the on-time medians should have been minutes apart, and they differ by 74 seconds
(+9s against +83s). Checking that rather than publishing it resolved it: of 7,143
windows that produced an arrival on 9 October, **3,612 — 50.6% — contain a single
position.** For half of all arrivals the two definitions are the *same timestamp*, so
the medians barely move while the tails move enormously (10th percentile delay −475s
against −63s). The 21 points come almost entirely from the other half.

The earlier figure of a 392-second median window measured only windows with more than
one position, and both numbers are correct about different populations. Stating only the
first would have made the rest of this record incoherent.

### What the project says out loud

This is the honest headline for the whole project, and it goes on the method page and
in the README permanently, not as a footnote: **the published on-time figure moves 21
percentage points on our own definition of "arrived" — further than anything Halifax
Transit did on 9 October.** Both percentages stay side by side, with the chosen
definition named in plain words. A reader who knows the figure is definition-sensitive
can judge it; one shown 70.2% as a fact cannot.

## Is the calibration sample fair? Tested, and it is not — but not in the direction feared

The decision above rests on 597 windows where the agency happened to report
`STOPPED_AT`, out of roughly 7,100 that produce an arrival on a day. The senior reviewer
asked whether that 8% is representative, on the grounds that if it is not, "61 seconds
late" is a number about unusual stops rather than about Halifax. Two tests, both cheap.

### (a) Window width — badly skewed, by a factor of 25

| 9–10 October | windows | median width | p90 width | single-position windows |
|---|---|---|---|---|
| windows with a `STOPPED_AT` | 217 | **815s** | 1,473s | 0.9% |
| all other windows | 41,778 | **32s** | 753s | 45.1% |

So `STOPPED_AT` appears overwhelmingly on long windows. The reviewer's hypothesis was
right that the sample is unrepresentative.

### (b) Fleet concentration — not an artefact of a few buses

| | |
|---|---|
| `STOPPED_AT` positions | 5,342 |
| distinct vehicles reporting it | **234 of 366 ever seen (64%)** |
| busiest single vehicle's share | 3.6% |
| distinct stops where it is reported | **45 of 1,571 (2.9%)** |
| of those stops, terminals or bays | **27 of 45** |

So it is not one AVL firmware version on a handful of buses — most of the fleet reports
it. It is concentrated by **place**: terminals, bays and park-and-rides. The top four
are Fall River Park and Ride, a Marketplace Drive bay, Novalea Drive, and a Barrington
Street bay.

That concentration is itself a fact about the feed, not merely a caveat about our
sample: **`STOPPED_AT` appears where buses stop long enough to be noticed.** 45 stops of
1,571 ever report it, 27 of them terminals or bays. The field is not a reliable
assertion that a bus is at a stop — it is a trace of dwell time, which is why it works
as ground truth for calibration and fails as a detection rule.

### Which way the skew runs, which is the part that matters

The reviewer expected the skew to mean the true bias is *worse* than 61 seconds. It runs
the other way, and the direction is measurable rather than arguable — bias against
window width, on the windows where both are known:

| window width | windows | median bias of the window's end | mean |
|---|---|---|---|
| under 60s | 21 | **0s** | 10s |
| 60–180s | 73 | 30s | 36s |
| 180–420s | 95 | 30s | 66s |
| 420–900s | 280 | 32s | 105s |
| over 900s | 293 | **211s** | 440s |

The bias grows with the width of the window, and the calibration sample is composed
almost entirely of wide windows. A typical window is 32 seconds wide, where the measured
bias is **0 to 30 seconds**. So the pooled 61 seconds is measured on the awkward cases
and is **conservative for an ordinary stop, not optimistic.**

Which makes sense mechanically: the window for stop N is the approach to N plus the
dwell at N, so its end is the departure. At an ordinary stop the dwell is seconds; at a
terminal where a bus lays over for ten minutes, the end of the window is ten minutes
after the arrival. The large biases are layovers, and they are exactly where the agency
bothers to report `STOPPED_AT`.

**So: `LAST_BEFORE_ADVANCE` stands, and the bias is published as a range rather than a
single number** — near zero at an ordinary stop, minutes at a terminal, 61 seconds
pooled across what can be checked. No correction is applied. What is honest to say is
that the figure can only be verified where the agency reports being stopped, and that
is at terminals.

## The published week, both ways

Re-derived over 4–10 October 2026 — 110,439 arrivals, 109,584 matched to a published
time (99.2%) — changing nothing but the arrival moment:

| | start of window | **end of window** (in force) |
|---|---|---|
| on time | 40.9% | **58.4%** |
| early | 35.5% | 11.1% |
| late | 23.6% | 30.5% |

**17.5 percentage points**, on the published range, with no bus behaving differently.
The single-day figure quoted earlier in this record (49.0% against 70.2%) was one
partly-collected day and is superseded by the week; it is kept because the reconciliation
below was derived from it.

Two distinct margins now get published, and conflating them would be the easiest
mistake a reader could make:

- **17.5 points** — the gap between the two candidate definitions. A finding about how
  much the question "what counts as arriving?" is worth.
- **8.7 points** — the margin of error that remains after the question was answered:
  58.4% as published against 49.7% with the named 61-second bias removed. This is the
  figure the observation floors are derived from (ADR-017), because it is what the
  project still does not know rather than a choice it has already made.

And the reconciliation, restated because without it the two look contradictory: the
definitions sit at opposite ends of a window often minutes wide, yet the median delay
moves only 74 seconds. **50.5% of the 115,810 arrival-producing windows in the week
contain a single position**, so for half of all arrivals both definitions are the same
timestamp, and everything the choice changes comes from the other half.
