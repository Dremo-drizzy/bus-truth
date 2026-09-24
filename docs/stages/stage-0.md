# Stage 0 — Setup

Built the Maven multi-module scaffold (common as a plain library, four Spring Boot apps on ports 8081-8084, each with an actuator health endpoint and a `contextLoads()` startup test), the Maven Wrapper, `.gitignore`/`.gitattributes`/`.env.example`, a GitHub Actions CI workflow running `./mvnw -B verify`, and a Docker Compose stack with PostgreSQL, Kafka in KRaft mode and kafbat's Kafka UI.

Renamed the base package from `io.github.Dremo_drizzy` to lowercase early: Windows ignores filename case and Linux CI does not, so the mismatch would have built locally and failed in CI.

Found a silent data-loss bug in the Compose file: the Kafka image declares its own volume at `/var/lib/kafka/data`, but the named volume was mounted one level up at `/var/lib/kafka`, so Docker quietly created a throwaway anonymous volume for the real data path and every restart began from nothing. It was only caught by writing a topic and a row, then running `down`/`up` and checking they survived — the containers reported healthy the whole time.

Wrote `scripts/save_feeds.py`, which archives all three realtime feeds every 20 seconds and the static GTFS zip once a day; it writes gzipped snapshots (~2 GB/day raw becomes ~0.4 GB/day) after measuring that byte-identical deduplication almost never fires, because the feed header timestamp changes on every poll.

Decoded a real VehiclePositions snapshot before designing anything around it: `trip_id`, `route_id`, `position`, `stop_id` and `current_stop_sequence` are always present, but `current_status` appeared on only 11 of 69 vehicles — so arrival detection cannot depend on it and must work from `current_stop_sequence` and `stop_id`.

Recorded seven decisions in `docs/decisions/`, including the two that shape everything downstream: keep raw data forever (ADR-005) and version the static GTFS rather than overwriting it (ADR-007).
