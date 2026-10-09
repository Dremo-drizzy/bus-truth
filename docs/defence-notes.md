# Defence notes

Answer these in your own words. Not Claude Code's, not the reviewer's — yours.
Short is fine. A sentence you can say out loud beats a paragraph you can only read.

The rule that produced this file: in the Stage 0 exam, the only two questions passed
were the two ADRs written personally. Everything written by someone else did not stick.

---

## 1. The project in thirty seconds

What does Bus Truth do, and why does the feed need recording rather than just reading?

> 

What does it NOT do? (Say this out loud before anyone asks. Owning a scope boundary
reads as judgement; being caught at one reads as a gap.)

> 

---

## 2. The architecture

Why is there a queue between the collector and the database, and what breaks without it?

> 

Why are the collector, loader and api separate services instead of one application?

> 

Why is arrival detection meant to live in Java and metrics in SQL?

> 

---

## 3. Stage 1 — the archive

Why keep the raw `.pb` bytes forever when the database holds the same data?

> 

The saver's dedupe fired 121 times in 112,050 polls. Why did it not work, and why is
that the same bug as the collector's original header-timestamp dedupe?

> 

---

## 4. Stage 2 — the collector

`entity.id` is not used as the identifier. Why not? (One snapshot had 909 entities and
541 distinct entity ids, and one modified trip carried a different trip's entity id.)

> 

39.8% of trip updates carry no `trip_id` in the `TripDescriptor`. Where is the identity
instead, and what happens to the data if you do not look there?

> 

The archiver silently overwrote same-second snapshots on Windows. Why did the first fix
not work, and why would CI never have caught it?

> 

---

## 5. Stage 3 — the loader

What exactly does the replay test prove? (Load N messages, assert counts, reset the
consumer group to offset 0, consume again, assert counts identical.)

> 

Why `ON CONFLICT DO NOTHING` rather than an upsert, and what does that discard?

> 

Why are `kafka_partition` and `kafka_offset` nullable?

> 

Why Testcontainers against real PostgreSQL instead of H2?

> 

Flyway was on the classpath, the app started, health said UP, and the database was
empty. Why? And why is that the same lesson as `spring-kafka` versus
`spring-boot-starter-kafka` in Stage 2?

> 

---

## 6. Stage 4 — the API and the map

Why does the endpoint return GeoJSON rather than a plain JSON array?

> 

Why does the page distinguish "no buses running" from "no recent data"?

> 

Coordinates are stored as `real`, not `double precision`. What did that decision cause
downstream, and why was it still right?

> 

Why `DISTINCT ON (vehicle_id) ... ORDER BY vehicle_id, vehicle_timestamp DESC`, and why
must `vehicle_id` come first in the ORDER BY?

> 

---

## 7. The things that went wrong

Pick the three you can tell best. For each: what happened, how you noticed, what you
did, what you would do differently.

a)

> 

b)

> 

c)

> 

Candidates, all real, all yours: the Kafka volume mounted one directory too high so
every restart silently lost data while reporting healthy; the archiver's Windows
overwrite; the laptop sleeping and taking half the collection with it; the disk filling
at 700 MB/day because the storage was never planned; four ecosystems (Boot 4,
Testcontainers 2, maplibre 6, springdoc 3) each breaking in a way no documentation
warned about.

---

## 8. What you would do next, and why you stopped where you did

> 
