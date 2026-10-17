# 019 — Which service day an arrival belongs to

> **NEEDS DANIEL'S REVIEW** — part of the arrival rule, which the brief reserves for
> Daniel. Written by Claude Code; every figure below is measured and reproducible.

Context: a transit service day is not a calendar day. It starts in the morning and runs
past midnight, which is why the published schedule gives a late-night departure as
`24:32:00` rather than `00:32:00`. Until this record, the project took the feed's own
`start_date` as authoritative without qualification, and the feed is wrong about it
after midnight.

How it surfaced: the on-time figures were being inspected by hour of the local day, and
the 00:00 and 01:00 rows read **17.0% and 0.0% on time with a median delay of −86,358
seconds**. That is not a surprising number, it is an impossible one — minus twenty-four
hours — so it was a defect rather than a finding.

The case that showed what: trip **4649655**, labelled `start_date=20261004` by the
feed, observed at a stop at **00:16 on 4 October**, with the schedule publishing that
stop at **24:32:00**. A trip genuinely on the 4th's service day reaches that stop at
00:32 on the *5th*. The observation belongs to the **3rd's** service day, and the feed's
own label contradicts the agency's own schedule.

Scale: **2,660 arrivals** in the week carried a delay beyond minus twelve hours, 2.4% of
the matched total. They were all being scored as not-on-time, which dragged the
published figure down.

## Was it the obvious cause? No, and checking mattered

The first hypothesis was that the detector was falling back to deriving a date from the
timestamp, because `start_date` was missing. Measured before writing any fix:
`start_date` is present on **1,205,076 of 1,205,076** positions in the week — none
missing. The fallback path was never taken, and the fix prepared for it would have
changed nothing.

## Options considered

- **A — keep trusting `start_date`.** Leaves 2,660 rows asserting that buses ran a day
  early.
- **B — repair the service day from the clock**, using the fact that a service day
  cannot contain a stop before it starts.
- **C — refuse to match any arrival whose delay is implausible**, and publish nothing
  for it.
- **D — resolve the service day by choosing whichever candidate day puts the scheduled
  time nearest the observation.** Rejected: that decides the match using the delay it is
  supposed to be measuring, which ADR-015 already rejected as circular.

Decision: **B, with C behind it as a guard.**

## Why B works, and the evidence that it is right

Shifting those 2,660 arrivals to the previous service day gives:

| | |
|---|---|
| median delay after the shift | **+4 seconds** |
| 10th / 25th / 75th / 90th percentile | −108s / −46s / +79s / +233s |
| inside half an hour | **2,659 of 2,660** |
| cases in the opposite direction | **0** |

A mis-pairing would scatter. This lands on the minute, which is what a correct answer
looks like.

**The boundary is 04:00 local, and it is measured rather than chosen.** Across every
stop time in the feed version in force, the earliest is **04:20:00** and the latest
**26:01:00**. A service day therefore runs from about 04:20 to 02:01 the following
morning, and no trip on service date D has any scheduled stop before 04:20 on D — so an
observation before 04:00 cannot belong to that calendar date's service day, whatever the
feed says. 04:00 sits inside the twenty-minute gap below the earliest published stop, so
the rule cannot clip real early-morning service. `ServiceDayTest` asserts that
relationship, and is what will fail if Halifax ever publishes an earlier stop.

The rule only fires where the feed's label and the clock contradict each other. Where
the agency has itself labelled a post-midnight trip with the previous date — which it
does for some — that label is kept, because it is the agency asserting a service day
rather than stamping today's date.

Result: **2,660 bad rows became 3.**

## Why C is still needed

The three survivors are the mirror image: trips published at exactly `24:00:00`,
observed a minute or two *before* midnight, so the feed's label is a day ahead of the
clock rather than behind it. Chasing each variant would be a sequence of patches, so the
on-time stage now refuses to publish any delay of twelve hours or more, counts it as
`implausibleDelay`, and reports it.

Twelve hours comes from the same measurement: a service day spans about twenty-two
hours and the largest delay ever observed in the week is 4.5 hours, so a half-day delay
cannot be a late bus. It can only be a day-labelling disagreement — and the guard
catches whatever causes the next one, rather than letting a twenty-four-hour error into
an average where nobody would notice it.

## What I gave up

- **Unconditional trust in the feed's `start_date`.** The project now overrides an
  agency field on a measured basis, which is a bigger claim than it had been making
  anywhere else. The override is narrow, one-directional, and asserted in a test.
- **Three arrivals.** They are counted and not published, which is the right trade for
  figures that would otherwise be arithmetically impossible.
- **Rows outside the range being rebuilt.** A run now groups trails on the corrected
  service day, so a post-midnight trail is attributed to the day it belongs to rather
  than the day the feed stamped — which also keeps each run's output inside the range
  it clears.

## Effect on the published figure

| | before | after |
|---|---|---|
| on time, 4–10 October | 58.4% | **60.1%** |
| arrivals with a delay beyond ±12h | 2,660 | 3 (counted, not published) |
| on time at 00:00 | 17.0% | 74.8% |
| median delay at 00:00 | −86,358s | +4s |

## The guard publishes what it refuses

A guard whose refusals live only in a log line cannot be checked, and one refusing three
rows is indistinguishable from one refusing nine thousand. So the counts are persisted
per service date in `ontime_quality` — arrivals considered, matched, unmatched, unusable
schedule times and implausible delays refused — rewritten whenever a range is rebuilt,
and served from `/api/coverage` beside that day's collection figures. The same rule that
governs every percentage on this site governs the rows thrown away.

A day the on-time stage has never run over reports **null**, not zero: "we did not
compare" is not "we compared and found nothing".

**The threshold is 0.1% of a day's arrivals**, and it is measured rather than chosen:
the week produced 3 refusals out of 112,951 matched, or 0.003%, so 0.1% is about thirty
times the background rate. Below it the run logs the count as ordinary; at or above it
the run warns that the feed may have changed how it labels service days. Three in a week
is mopping up; a hundred would mean the figures for that day rest on a shrinking subset.

Revisit if: the feed starts labelling service days per the GTFS-Realtime specification,
in which case the override stops firing on its own and the counter should go to zero.
Watch `implausible_delays_refused` in `/api/coverage` — it going above a handful means a
new labelling fault.
