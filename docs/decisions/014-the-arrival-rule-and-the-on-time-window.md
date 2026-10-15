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
| stops skipped (passed unobserved) | 39,725 |
| trails broken by the gap rule | 40 |
| positions with no stop sequence | 0 |
| scheduled stop-events for the trips observed | 37,567 |
| **share of scheduled stop-events with a derived arrival** | **17.5%** |

Confidence: 6,438 HIGH, 117 SUFFIXED_TRIP, 34 SPARSE_TRAIL.

**The cause of the 17.5% is UNRESOLVED.** It was first attributed to the 20-second
poll interval, and that attribution does not survive arithmetic: 6,597 jumps covering
46,322 stop positions is an average of seven stops per jump, which at 20-second
polling would mean a bus passing a stop every 2.9 seconds. Something is making the
effective interval between usable observations much longer than 20 seconds, and the
candidate explanations — collection gaps shorter than `MAX_TRAIL_GAP`, which are
invisible in the trails-broken count, against a genuine sampling limit — lead to
opposite conclusions about whether better collection would raise the figure. It is
being measured rather than assumed.

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
