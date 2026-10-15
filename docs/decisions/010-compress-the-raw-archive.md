# 010 — Compress the raw archive

Context: the saver writes about 468 KB every 20 seconds — roughly 2 GB a day and 60 GB a
month, and daytime is heavier than night. Raw data is kept forever (ADR-005), so the archive
only grows. Skipping byte-identical snapshots barely helps: the feed header carries a
timestamp that changes every poll, so consecutive snapshots are never byte-identical even
when the content is the same.

Options considered:
A. Store the snapshots as they arrive.
B. Gzip each snapshot as it is written.
C. Leave them raw and compress older days later, in a batch.

Decision: B. Each snapshot is gzipped as it is written. Files become
`data/raw/<feed>/<date>/<HHMMSS>.pb.gz`.

Why:
- Measured on real snapshots: trip updates compress to 20% of their size, alerts to 11%,
  vehicle positions to 50%. That takes about 2 GB a day down to about 0.4 GB.
- `gzip` is in the Python standard library, so it is not a new dependency.
- Compressing at write time means there is never an uncompressed backlog to deal with.

What I gave up:
- The files are no longer directly readable. Anything that reads them has to decompress
  first, including Stage 2.
- A little CPU on every poll.

Revisit if: the compression cost ever slows the 20-second cycle, or a better format for
long-term storage is chosen later.
