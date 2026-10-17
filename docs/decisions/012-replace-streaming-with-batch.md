# 012 — Replace streaming with batch

> **NEEDS DANIEL'S REVIEW** — written by Claude Code to unblock Phase R. The brief
> reserved this record for Daniel, because the decision was his and the reasoning
> should be in his words. Everything below is accurate; none of it is yet defended by
> its author.

Context: the first version of this project put Kafka between the collector and the
database. Three decision records rested on that — ADR-002 (Kafka in the middle),
ADR-003 (one entity per message, JSON, keyed by vehicle or trip) and ADR-004 (offsets
committed after the database write). After four stages of building on it, the project
had a live map and no reliability measurement at all: the feature the whole thing was
designed around had not been started, and the queue was the reason.

Options considered:

- **A — keep Kafka.** Defensible on a CV, and real experience of a tool that appears in
  job descriptions.
- **B — remove the queue; the collector writes rows directly.** Fewer moving parts,
  and the stages that were deferred get built.
- **C — keep Kafka, defer the analysis again.** What had already happened twice.

Decision: **B**.

Why: the question a queue answers is "what decouples the producer from the consumer,
and what happens when the consumer is slower, offline or needs replaying?" This
project's honest answer is that nothing needs decoupling.

- **The analysis is historical, not reactive.** On-time performance, bunching and ghost
  buses are computed over days and weeks of data after the fact. There is no consumer
  that must see a position within a second of its arrival.
- **The raw archive already is the log.** ADR-005 keeps every snapshot forever as
  compressed protobuf, and Kafka's replayable log was the second copy of something the
  project already had on disk. Stage R4 proved the point in the other direction: the
  archive held 152,036 snapshots and the database 3,374, and `ArchiveBackfill` replays
  the gap through the same mapper and writer the live path uses. That is the replay
  capability, and it reads from the archive, not from a queue.
- **The guarantee survived the removal.** ADR-004's at-least-once delivery rested on
  the insert being idempotent on its natural key — `(vehicle_id, vehicle_timestamp)` —
  not on Kafka's offsets. The `on conflict do nothing` insert is still there, still the
  thing that makes a re-run safe, and it is now the only thing that has to be true.
- **The operational cost was not hypothetical.** KRaft broker, topics, partitions, two
  deserializer layers, a UI container, and a volume mounted one level too high that
  silently discarded every message on restart. All of it in service of a hop between
  two processes on the same machine.

What I gave up:

- **Kafka on the CV**, which was the real argument for keeping it. The honest version of
  that argument is still available: the project used it, measured what it cost, and
  removed it for stated reasons — and ADR-002, ADR-003 and ADR-004 are kept rather than
  deleted, struck through in `CLAUDE.md` and pointing here. A decision reversed with
  its reasoning intact is better evidence of judgement than a decision never examined.
- **Back-pressure if the writer falls behind.** With a 20-second poll and roughly 240
  positions a snapshot, it does not.
- **Decoupled restarts.** Restarting the database now interrupts collection for as long
  as it takes. The archive is written before the database insert, so nothing is lost —
  only delayed, and recoverable by replay.

Revisit if: a second consumer appears that genuinely needs the same stream at the same
time — a live alerting path, or another service outside this repo. One process writing
rows for later analysis is not that, and adding a queue before a second consumer exists
is building for an imagined future instead of a measured one.

Superseded by this record: ADR-002, ADR-003, ADR-004. The `v1-streaming` work is
preserved in the private `bus-truth-v1-streaming` repository with all nine branches,
because the old repo going private is what keeps that history (amendment §5).
