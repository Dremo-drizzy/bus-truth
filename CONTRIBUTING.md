# Contributing

## Conventions

- **Java 21 LTS**, current stable Spring Boot, Maven multi-module. Base package
  `io.github.dremo_drizzy.bustruth.<module>`.
- Records for immutable domain objects. Constructor injection only.
- Database migrations are Flyway only. Never edit a migration that has already been
  applied; add a new one.
- All timestamps are stored as UTC `timestamptz`; service dates are computed in
  `America/Halifax`. GTFS times can exceed `24:00:00`.
- Raw data is kept forever; everything else is derived and can be re-derived.
- Static GTFS is versioned by `feed_version` and never overwritten.
- Frontend: Vite + React + React Router, TypeScript `strict`, MapLibre GL.
- Tests: JUnit 5 + AssertJ, Testcontainers for integration tests. Decision logic
  (on-time, matched, ghost) gets unit tests, using real captured `.pb` fixtures rather
  than invented data.
- **Any test that boots the Spring context must set
  `bustruth.collector.polling-enabled=false`.** Otherwise the pollers fetch live data
  and write rows underneath the test's own assertions.
- Config goes in environment variables; keep `.env.example` current. Never commit
  secrets.
- Every non-trivial choice gets a record in `docs/decisions/` (see its README for the
  template).
- Commits: one logical change each; imperative subject line; the body says why.

## Data

Licence: Open Government Licence – Halifax. The README and the site footer carry the
required attribution:

> Contains information licenced under the Open Government Licence – Halifax.
