# 015 — Matching an arrival to its published time

> **NEEDS DANIEL'S REVIEW** — written by Claude Code to unblock Stage R5. The
> measurements are real and reproducible; the words are not yet the author's own.

Context: every claim this project makes about reliability is a comparison between one
derived arrival and one published time. Three things had to be decided before that
comparison could be made at all, and each of them has an obvious answer that is wrong.

## 1. Which key joins an arrival to the schedule

Options considered:

- **A — (trip_id, stop_sequence).** The obvious join. Both sources carry the number,
  and it is unique within a trip by definition.
- **B — (trip_id, stop_id), with the sequence breaking ties.**
- **C — nearest published time to the observation.** Circular: it would decide the
  match using the delay it is supposed to be measuring, and could never find a bus
  more than a few minutes out.

Decision: **B**.

Why: option A is wrong on about a fifth of all arrivals, and silently. Measured over
the 6,589 arrivals derived for service date 9 October 2026, joined to feed version
`2026_08_August_20260806`:

| | count | share |
|---|---|---|
| arrivals matched by `(trip_id, stop_sequence)` | 6,525 | 99.0% of 6,589 arrivals |
| …where the two sources name the **same** stop | 5,120 | 78.5% of 6,525 matches |
| …where they name a **different** stop | 1,405 | 21.5% of 6,525 matches |

And the disagreement is not noise. For every mismatched arrival but 32, the stop the
feed named is present elsewhere in the same published trip, one to three places
earlier:

| the feed's number minus the schedule's | count |
|---|---|
| 1 place ahead | 416 |
| 2 places ahead | 626 |
| 3 places ahead | 331 |
| stop not on the published trip at all | 32 |

Trip 4659595 on 9 October, side by side, is the whole story:

| feed sequence | feed stop | schedule sequence | schedule stop |
|---|---|---|---|
| 1 | 8435 | 1 | 8435 |
| 3 | 8823 | — | *(not in this published trip)* |
| 4 | 8824 | 3 | 8824 |
| 6 | 8885 | 5 | 8885 |
| 7 | 7247 | 6 | 7247 |
| 9 | 8187 | 8 | 8187 |

The live feed's trip contains a stop (8823) the published zip does not list, so from
that point on the feed's numbering runs one ahead, and the drift accumulates as more
such stops appear. Neither source is wrong: they describe different versions of the
same trip, published weeks apart.

Joining on the number would therefore have compared a fifth of all arrivals against a
*neighbouring* stop's scheduled time. The size of that error is measurable, by taking
the 1,373 mismatched arrivals whose own stop was found elsewhere in the trip and
comparing the two candidate scheduled times:

| | |
|---|---|
| median error | **94 seconds** |
| mean error | 118 seconds |
| worst | 960 seconds (16 minutes) |

Against a five-minute on-time window, a median error of a minute and a half — always in
the same direction, on a fifth of all rows — is enough to move arrivals across the
window without anything looking wrong. That is exactly the kind of error this project
exists not to make.

What I gave up: stop ids are not quite unique within a trip. A loop route, or a
terminus served at both ends, calls at the same stop twice, and then the stop id alone
matches two published times. Those rows fall back on the sequence number — the very
number just shown to drift — so they are flagged `stop_match_ambiguous` and counted
apart rather than mixed in.

Every row also stores `scheduled_stop_sequence` beside the feed's own number, so the
disagreement is published as a figure rather than reconciled away. Deciding which
publisher is right about their own trip is not something this project is in a position
to do.

Revisit if: the static loader is re-run often enough that the zip in force is never
weeks old, which should shrink the 21.5% — if it does not, the cause is something other
than schedule drift and this record is wrong.

## 2. What instant a published time means

Options considered:

- **A — midnight on the service date, plus the published offset.**
- **B — noon on the service date minus twelve hours, plus the published offset.**
- **C — parse the published value as a local time.** Impossible: GTFS permits
  `25:10:00` for a trip that runs after midnight, and no time type accepts it. This is
  why `gtfs_stop_times.arrival_time` is stored as text (ADR-007).

Decision: **B**, which is what the GTFS specification says.

Why: on 363 days a year A and B are the same instant, and on two days they differ by an
hour. Halifax moves to ADT at 02:00 on 8 March 2026, making that service day 23 hours
long. A bus published for `08:00:00` that day:

- from midnight: 04:00 UTC + 8h = 12:00 UTC, which reads **09:00** locally — an hour
  after the printed time, so every bus that morning is recorded an hour early
- from noon minus twelve hours: 03:00 UTC + 8h = 11:00 UTC, which reads **08:00**
  locally, which is what the timetable says

1 November 2026, the 25-hour day, is wrong by an hour the other way. Noon is the anchor
because noon never falls inside a clock change, while midnight-adjacent local times can
be deleted or duplicated by one.

What I gave up: nothing, beyond the two lines of explanation that stop the next reader
"simplifying" it back to midnight. `ScheduledTimeTest` asserts both clock-change days
and the hour that happens twice, and one test walks all 365 days of 2026 to show that
midnight would have been right on every day except those two — which is precisely what
would have let the bug ship.

## 3. Which publication of the schedule to compare against

Options considered:

- **A — the newest publication loaded.**
- **B — the publication whose own calendar covers the service date; among those, the
  most recently loaded.**

Decision: **B**.

Why: A measures an arrival against a promise that may not have been made yet. Compare a
4 October arrival against a zip published in November and the delay is a fiction — the
timetable had changed in between. `feed_version` is stored on every row, so any
published delay can be traced back to the exact publication it was computed against.

What I gave up: a service date no publication covers is skipped, and its arrivals are
counted as `daysWithoutSchedule` rather than compared against the nearest zip to hand.
Silently falling back to the newest one would make a missing schedule look like a day
on which no buses ran.

Revisit if: Halifax starts republishing without updating `feed_info.txt` dates, which
would leave dates uncovered and quietly drop days.
