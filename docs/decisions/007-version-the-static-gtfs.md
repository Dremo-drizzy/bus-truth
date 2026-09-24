# 007 — Version the static GTFS, never overwrite it

Context: Halifax republishes the schedule zip every few weeks, and trip ids are only
meaningful within one publication — they can be retired and reused. The realtime feeds refer
to the schedule by id, so an id on its own does not identify a trip.

Options considered:
A. Overwrite the `gtfs_*` tables on every import — one current schedule, always the latest.
B. Give every import its own `feed_version` and never overwrite.
C. Keep the zip files only and parse them at query time.

Decision: B. Every import gets its own `feed_version`, nothing is overwritten, and queries
join on `(feed_version, trip_id)` for the service date in question.

Why:
- If I overwrite the schedule tables, a bus that arrived at 8:23 in March against an 8:15
  promise shows up as 2 minutes early once September's 8:25 schedule lands. Nothing errors.
  The number is just wrong.
- Keeping every version is what makes ADR-005 work: re-running old history under a new rule
  only gives the right answer if each day is compared against the schedule that was actually
  published for it.

What I gave up:
- More complex queries, and `trip_id` alone is no longer a key.
- More storage: every publication is kept in full, and most rows are unchanged copies.

Revisit if: the number of kept versions becomes a storage problem, or picking "the version
in force on this service date" turns out to need a more careful rule than the latest import
on or before that date.
