# 005 — Keep the raw data forever

Context: the feeds are only available live. A snapshot not saved at 08:04 this morning is
gone for good — there is no archive to go back to. At the same time, the rules that turn
that data into published numbers are judgement calls I have not made yet, and will not get
right first time.

Options considered:
A. Keep every raw snapshot forever; derive everything else from it.
B. Keep raw snapshots for a retention window (say 30 days), then delete.
C. Keep only the derived output — `arrival_events` and the metric marts — and discard the
   raw bytes once processed.

Decision: A. Raw data is kept forever and is the source of truth. Everything downstream is
derived and re-derivable: `arrival_events`, the dbt marts, every published metric.

Why:
- The on-time window and the ghost-trip rule are judgement calls that will change once I
  see real data. "On time" might start as −1/+3 minutes and later become −1/+5. With the
  raw data kept, I re-run six weeks of history under the new rule and get a consistent
  series. Under B or C, the old numbers were computed under the old rule and cannot be
  recomputed — the history would have a seam in it.
- Bugs. When the processor's arrival detection gets something wrong, the fix is to correct
  the code and reprocess. That is only possible if the input still exists.
- New questions. Raw snapshots contain fields I am not using yet — occupancy, bearing,
  odometer. Keeping them means a future feature can look backwards, not just forwards.
- Honesty. The project's claim is that it measures what actually happened. Keeping the
  original bytes means any number on the site can be traced back to the evidence.

What I gave up:
- Disk. Measured from the Stage 1 saver, the three feeds are about 468 KB every 20 seconds,
  which is roughly 2 GB a day and 60 GB a month, and daytime is heavier than the night I
  measured. "Forever" is a real commitment, not a throwaway line.
- The need for a compression or archiving plan. Gzip cuts it to roughly a fifth (trip
  updates compress to about 20% of their size), so this is manageable, but it is work.
- Backup responsibility: the one thing that cannot be recreated now has to be looked after.

Revisit if: storage cost stops being trivial; at that point the answer is compression or
cold storage for old months, not deletion. Deleting raw data should be the last resort,
because it is the only part of the system that cannot be rebuilt.
