# 004 — At-least-once delivery with idempotent inserts

Context: Kafka sits between the collector and the database (ADR-002), so a message can be
delivered more than once — a consumer rebalance, or a crash between writing a row and
committing the offset, both end with the same message arriving again. Something has to
decide what happens then. The alternative framing, exactly-once delivery, would mean
coordinating the Kafka offset and the database write in one transaction.

Options considered:
A. At-least-once delivery, and make the insert idempotent so a repeat is harmless.
B. Exactly-once semantics: transactional Kafka plus an offset store in the same database,
   so the offset and the row commit together.
C. At-most-once: commit the offset before writing, accepting that a crash loses the message.

Decision: A. Offsets are committed after the database write, and every insert ends with
`ON CONFLICT DO NOTHING` on a natural key — for vehicle positions,
`(vehicle_id, vehicle_timestamp)`.

Why:
- The loader never has to ask whether it has seen a message before. The database answers
  that, once, as part of the write it was going to do anyway.
- Committing the offset after the write means the only possible failure is a repeat, never a
  loss — and a repeat costs nothing. C has the opposite failure, and the feeds cannot be
  re-requested: a lost snapshot is gone permanently.
- B is a lot of machinery for a problem that a unique key already solves. It also couples the
  consumer to the database's transaction boundaries, which would make replaying history
  harder rather than easier.
- The same key doubles as the size control. Idempotency is not only about replay safety
  here: the identical prediction or position is resent every 20 seconds until it changes, and
  one row per distinct observation is what keeps the tables tractable.
- It makes replay a normal operation. Rewinding a consumer group to the start of a topic is
  something to do on purpose, not a disaster to recover from.

Proof rather than assertion: the consumer group was rewound to offset 0 on all three
partitions and every message reconsumed. 663 rows before, 663 rows after, and
`max(inserted_at)` identical to the microsecond — the timestamp matters as much as the
count, because a count alone would also pass if rows had been deleted and rewritten. The
same guarantee is covered by an integration test against a real PostgreSQL 18, since
`ON CONFLICT DO NOTHING` is Postgres syntax and proving it on another engine would prove
nothing.

What I gave up:
- First write wins, silently. A second message with the same key but different content is
  discarded rather than merged or flagged. That is the right reading of this data — an
  identical vehicle timestamp is a repeat of one observation, not new information — but it is
  a real loss of information, and it is only acceptable because the raw `.pb` archive still
  holds every copy (ADR-005).
- The natural key has to be genuinely identifying. Choosing it wrongly means either
  duplicate rows or silently dropped observations, and neither announces itself.
- Duplicate work is still done: the row is built and the insert is executed before the
  database decides it was unnecessary.

Revisit if: a table's natural key turns out not to identify an observation, or a future
consumer needs to know that a duplicate arrived rather than just not storing it. Then the
answer is probably `ON CONFLICT DO UPDATE` with a counter, not exactly-once delivery.
