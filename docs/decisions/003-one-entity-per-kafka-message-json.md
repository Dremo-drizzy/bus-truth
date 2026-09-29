# 003 — One entity per Kafka message, as JSON

Context: each poll of a realtime feed returns one protobuf blob containing hundreds of
entities — one per vehicle, or one per trip. Before the collector could publish anything,
two things had to be settled: how much goes in a single Kafka message, and in what format.

Options considered:
A. One message per poll — the whole feed snapshot, as the raw protobuf bytes.
B. One message per entity, as JSON, keyed by the entity it describes.
C. One message per entity in a binary schema format (Avro or protobuf) with a schema
   registry enforcing the shape.

Decision: B. One entity per message, serialised as JSON. The message key is `vehicle_id`
for vehicle positions and `trip_id` for trip updates.

Why:
- The key is the important half of this decision. Kafka guarantees ordering **within a
  partition**, not across a topic, and a message's key determines its partition. Keying on
  `vehicle_id` puts every message about one bus in one partition, in order. Reconstructing
  what a single bus did is the entire point of this project, so that is the ordering I need.
  Without a key, messages are spread across partitions and a vehicle's history can be read
  out of order.
- One entity per message is the unit the consumer actually works in. The loader writes one
  row per entity and dedupes per entity; a whole-snapshot message (A) would have to be
  unpacked before any of that, and a single bad entity would poison the whole batch.
- JSON is readable. I can open kafka-ui, look at a message and understand it without any
  tooling, which matters most while I am still learning what the feed contains.
- No extra infrastructure. Option C needs a schema registry running and managed, which is
  another service to learn, run and reason about, for a project where one broker is enough.

What I gave up:
- Size. JSON is bulky next to protobuf or Avro: field names repeat in every message, and
  numbers are stored as text.
- Safety. Nothing enforces the message shape. If I rename a field in the producer, nothing
  fails at compile time; the consumer just stops seeing the value at runtime. A schema
  registry (C) would have caught it before it was published.
- Per-message overhead: hundreds of small messages per poll rather than one large one.

Revisit if: message volume or storage becomes a real cost; or a shape change breaks a
consumer silently, which is the point at which Avro or protobuf with a registry has earned
its extra complexity.
