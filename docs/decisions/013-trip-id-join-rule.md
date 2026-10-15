# 013 — Joining live trip ids to the published schedule

> **NEEDS DANIEL'S REVIEW** — written by Claude Code to unblock the build; the
> reasoning here is not yet the author's own.

Context: every metric in this project rests on matching a trip id from the realtime
feed to a row in `gtfs_trips`. Live ids sometimes carry an `_N` suffix — `4659595_1`,
`4660053_3` — from the agency's trip-modification extension, and not one of the 9,239
trip ids in the published schedule contains an underscore. A trip that fails to join
is dropped, and a dropped trip is indistinguishable from a bus that never ran: the
ghost-bus metric would report a service failure that did not happen.

Options considered:
A. Join on `trip_id` as sent, and accept that modified trips are lost.
B. Strip a trailing `_\d+` and join on the base id.
C. Treat suffixed trips as unjoinable and exclude them from every metric explicitly.

Decision: B, with the suffix recorded rather than erased. Arrivals derived from a
suffixed trip id carry lower confidence.

Why — measured against `feed_version 2026_08_August_20260806`, over 1,708 distinct
live trip ids and 152,047 position rows:

| | ids | % of ids | % of rows |
|---|---|---|---|
| join `gtfs_trips` directly | 1,676 | 98.13% | 98.67% |
| join only after stripping `_N` | 28 | 1.64% | 1.07% |
| join neither way | 4 | 0.23% | 0.26% |

So stripping recovers every suffixed id observed, and 99.77% of ids join overall.
Option A would silently discard 28 trips; option C would discard them loudly, which is
honest but throws away recoverable data.

The four that join neither way are consecutive ids — 4661747, 4661748, 4661755,
4661756 — which is the shape of trips added after the 6 August publication rather than
a fault in the rule.

> Stripping the suffix assumes a modified trip visits the same stops in the same
> order as the trip it modifies. That is unverified. Arrivals derived from suffixed
> trip ids are marked with lower confidence.

What I gave up:
- The assumption above, unverified. A modified trip may skip stops or reorder them, in
  which case its scheduled times are wrong rather than missing — a quieter error than
  a failed join.
- `trip_id` alone stops being a join key; every query needs the base id, and the
  distinction has to survive into `arrival_events` as a confidence value or it is lost.

Revisit if: the share joining only after stripping grows beyond a few percent, or a
modified trip is observed visiting a different stop sequence from its base trip — at
which point modified trips need their own schedule source, not a derived one.
