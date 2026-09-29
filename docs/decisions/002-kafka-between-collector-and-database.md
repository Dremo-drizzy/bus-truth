# 002 — Kafka between the collector and the database

Context: the collector polls the Halifax feeds every 20 seconds. Halifax never re-sends
anything, so anything missed is gone permanently. The database will not always be ready to
take a write: it can be full, slow, restarting, or in the middle of a migration.

Options considered:
A. The collector writes straight to PostgreSQL.
B. A queue between them: the collector publishes to Kafka, a loader reads from Kafka and
   writes to PostgreSQL.
C. The collector writes to local files, and something loads them later.

Decision: B. Kafka sits between the collector and the database.

Why:
- If the database is full or slow, we don't lose data coming from the Halifax feeds. Kafka
  holds the messages until the loader catches up.
- The collector's only job is to keep polling and publishing. Nothing about the database
  can stop it.
- With option A, a database problem means lost snapshots, and there is no way to get them
  back.

What I gave up:
- An extra system to run and understand.

Revisit if: the loader turns out never to fall behind and Kafka is only ever adding a hop —
but note that the risk being covered here is the rare bad day, not the normal one.
