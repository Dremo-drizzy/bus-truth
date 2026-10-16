# 018 — Where the archive lives once it is hosted

> **NEEDS DANIEL'S REVIEW** — written by Claude Code. The ruling to keep collecting all
> three feeds is the senior reviewer's; the scope estimate and the fallback are mine.

Context: hosting the collector means the archive lands on a rented disk instead of a
laptop, and the arithmetic does not fit. Measured on the current archive:

| feed | mean snapshot | share of bytes | per day at full coverage |
|---|---|---|---|
| trip updates | 202.4 kB | **94%** | ~840 MB |
| alerts | 9.5 kB | 4% | ~39 MB |
| vehicle positions | 3.8 kB | 2% | ~16 MB |
| | | | **~910 MB/day** |

The recommended host (CPX31, Ashburn) has a 160 GB disk. After the operating system,
the images, PostgreSQL and the existing 16 GB of archive, roughly 130 GB is left:
**about 143 days at 910 MB a day.** The host fills before February. With only positions
and alerts — 6% of the bytes, about 55 MB a day — the same disk lasts over five years.

So something has to give, and there are only three candidates: stop collecting trip
updates, stop archiving them on the host, or put the archive somewhere other than the
host's disk.

Options considered:

- **A — pause the trip-updates feed.** Cheapest, and it was my suggestion.
- **B — write the archive to S3-compatible object storage** behind the existing
  archiver, so the host's disk stops being the constraint.
- **C — keep collecting all three feeds, but archive trip updates somewhere other than
  the host** until B exists.

Decision: **C now, B as soon as it is affordable in time. A is rejected.**

Why A is rejected — the reviewer's ruling, and it is right: trip updates are the
agency's own *predictions*, and they are the only possible basis for R9, the question
of how well Halifax predicts itself. **October 2026's predictions cannot be collected in
December.** Disk is replaceable; that data is not. Nothing is deleted and nothing is
paused.

## Why B is not being built this week

The reviewer described this as one implementation behind an existing interface. It is
not, and the difference is the whole estimate:

1. **There is no interface.** `RawArchiver` is a concrete class, `FeedPoller` depends on
   it directly, and `archive()` returns a `java.nio.file.Path` — which object storage
   has no equivalent of. Extracting an interface means changing that return type and
   everything that reads it.
2. **A new dependency.** An S3 client (AWS SDK v2 or MinIO) is several megabytes and
   the first third-party runtime dependency the collector would take on beyond Spring
   and the GTFS bindings.
3. **The archive is not write-only, which is the part that bites.** `ArchiveBackfill`
   replays the archive by walking the filesystem with `Files.list`, and that replay is
   what rebuilt 1.13 million rows after collection and the database fell out of step.
   Moving snapshots to object storage without also moving the *read* path means replay
   silently stops working for everything stored remotely — the capability ADR-005 and
   ADR-012 both lean on would quietly be gone.
4. **Tests.** A fake S3 means either an in-memory stub or another Testcontainers image.

Estimate: **a day and a half to two days**, most of it in (3) and (4), not in the
upload. That is over the reviewer's one-day threshold, and the deadline is 25 October.
A storage-tiering project ten days out is exactly the trade this project should not
make.

## What C means in practice

- The host collects and archives **vehicle positions and alerts**, and writes positions
  to PostgreSQL as now. ~55 MB of archive a day, so its disk is a non-issue.
- **Trip updates keep being collected on Daniel's laptop**, by the same collector with
  the other two feeds switched off, into the same `data/raw` layout. Coverage there will
  be whatever a laptop manages — the measured history is 73.7%, worst day 42.4% — and
  that is the honest cost of this decision: a partial record of the agency's
  predictions, rather than none.
- The switching is a per-feed flag, `bustruth.collector.<feed>.enabled`, default true,
  read by the same `@ConditionalOnProperty` that already gates the global
  `polling-enabled`. A disabled feed creates no poller at all rather than running a
  scheduled no-op.

What I gave up:

- **Continuous collection of the most interesting feed.** The predictions archive will
  have laptop-shaped holes in it for as long as this lasts, and those holes are
  permanent.
- **One archive in one place.** Until B is built, the archive is split across two
  machines, which is a thing to explain in an interview rather than something to be
  proud of. The honest version: the constraint was measured, the trade was named, and
  the irreplaceable data kept being collected.

Revisit if: B gets built, which is a day and a half whenever there is a day and a half —
at which point the host collects everything and the laptop stops being part of the
system. That is the intended end state and this record is a waypoint, not a design.
