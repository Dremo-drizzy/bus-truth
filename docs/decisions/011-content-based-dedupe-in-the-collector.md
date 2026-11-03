# 011 — Skip publishing a feed whose content has not changed

Context: the collector polls each feed every 20 seconds and publishes one Kafka message per
entity. Most polls return the same information as the last one, so most of those messages
say nothing new. The first attempt compared the feed header timestamp and skipped a poll
whose header had not advanced.

That check could never fire. Stage 1 had already measured it: the header timestamp advances
on **every single poll**, which is also why byte-level deduplication in the Python saver
almost never skipped a snapshot. The alerts feed alone would have republished around 475
records every 20 seconds — roughly two million messages a day describing a handful of
disruptions that change a few times a day.

Options considered:
A. Compare the feed header timestamp.
B. Compare a hash of the decoded entities, ignoring the header.
C. Publish everything and let the loader's unique keys absorb the repeats (ADR-004).

Decision: B. A SHA-256 over the entities of the decoded snapshot. If the hash matches the
last published one, nothing is published; the raw bytes are still archived either way.

Why:
- It is the only one of the three that answers the question actually being asked: has
  anything changed? The header says when the feed was generated, which is always "now".
- Measured over 180 consecutive snapshots per feed: content changed on 92.7% of polls for
  vehicle positions and trip updates, and **0%** for alerts. So this removes an entire
  feed's worth of traffic and still trims 7% from the other two.
- The cost is one hash of data already in memory. This is also what the Python saver could
  not do: comparing content means decoding protobuf, and the saver deliberately has no
  dependencies (ADR-010). The collector has already decoded the message by that point, so
  what was infeasible in one place is nearly free in the other.
- C would work correctly — the loader would discard the duplicates — but it would pay for
  every one of those two million daily messages in network, broker storage and consumer
  time, to discover they were unnecessary.

One trap worth recording: the entities are hashed **individually** rather than by clearing
the header and re-serialising the message. `header` is a required field in the
GTFS-Realtime schema, so a message without one cannot be serialised at all — the obvious
implementation throws.

What I gave up:
- The archive and the topic no longer agree one-to-one. A snapshot exists on disk with no
  corresponding Kafka messages, so "how many times did the agency repeat this?" is a
  question only the archive can answer.
- The state lives in memory, so a restart republishes each feed once. That is harmless only
  because the loader dedupes on insert (ADR-004) — a real dependency between two services,
  noted in the code so nobody removes that dedupe without seeing what rests on it.
- Deduplication is at snapshot granularity, not per entity. One bus moving means every
  entity in that snapshot is republished.

Revisit if: per-entity deduplication becomes worth the bookkeeping — the trip-updates feed
is the candidate, where one changed prediction currently republishes several hundred trips.
