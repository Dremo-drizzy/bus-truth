"""Measure whether live trip ids can be joined to the published schedule.

This is the highest-risk assumption in the project. Arrival detection, on-time
performance and ghost-trip counting all rest on matching a trip id seen in the
realtime feed to a row in gtfs_trips. Live ids sometimes carry an `_N` suffix —
`4659595_1` — from the agency's trip-modification extension, and no schedule trip id
contains an underscore. A trip that fails to join is dropped, and a dropped trip is
indistinguishable from a bus that never ran: the metric would report a service
failure that did not happen.

So the number is measured before anything is built on top of it.

    python scripts/check_trip_join.py [--feed-version X] [--examples 20]
"""

import argparse
import os
import sys

import psycopg

# Counted both ways on purpose. Distinct ids say how much of the schedule we can
# reach; rows say how much of the observed data we can use, and a single id with
# thousands of positions weighs very differently in each.
QUERY = """
with live as (
    select trip_id,
           regexp_replace(trip_id, '_\\d+$', '') as base_trip_id,
           count(*) as positions
    from raw_vehicle_positions
    where trip_id is not null and trip_id <> ''
    group by trip_id
),
scheduled as (
    select trip_id from gtfs_trips where feed_version = %(feed_version)s
)
select l.trip_id,
       l.base_trip_id,
       l.positions,
       exists (select 1 from scheduled s where s.trip_id = l.trip_id)      as joins_directly,
       exists (select 1 from scheduled s where s.trip_id = l.base_trip_id) as joins_stripped
from live l
"""


def connection_string() -> str:
    password = os.environ.get("POSTGRES_PASSWORD")
    if not password:
        sys.exit("POSTGRES_PASSWORD is not set. It has no default anywhere in this project.")
    return (
        f"host={os.environ.get('POSTGRES_HOST', 'localhost')} "
        f"port={os.environ.get('POSTGRES_PORT', '55432')} "
        f"dbname={os.environ.get('POSTGRES_DB', 'bustruth')} "
        f"user={os.environ.get('POSTGRES_USER', 'bustruth')} "
        f"password={password}"
    )


def newest_feed_version(cursor) -> str:
    cursor.execute("select feed_version from gtfs_feed_versions order by loaded_at desc limit 1")
    row = cursor.fetchone()
    if not row:
        sys.exit("No schedule is loaded. Run scripts/load_gtfs.py first.")
    return row[0]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--feed-version", help="defaults to the most recently loaded")
    parser.add_argument("--examples", type=int, default=20)
    arguments = parser.parse_args()

    with psycopg.connect(connection_string()) as connection, connection.cursor() as cursor:
        feed_version = arguments.feed_version or newest_feed_version(cursor)
        cursor.execute(QUERY, {"feed_version": feed_version})
        rows = cursor.fetchall()

    if not rows:
        sys.exit("No live trip ids in raw_vehicle_positions.")

    total_ids = len(rows)
    total_positions = sum(r[2] for r in rows)
    direct = [r for r in rows if r[3]]
    stripped_only = [r for r in rows if not r[3] and r[4]]
    unmatched = [r for r in rows if not r[3] and not r[4]]
    suffixed = [r for r in rows if r[0] != r[1]]

    def share(subset) -> tuple[float, float]:
        ids = len(subset) / total_ids * 100
        positions = sum(r[2] for r in subset) / total_positions * 100
        return ids, positions

    print(f"feed_version: {feed_version}")
    print(f"live trip ids: {total_ids:,} distinct, over {total_positions:,} position rows\n")

    print(f"{'':34} {'ids':>12} {'% ids':>8} {'% rows':>8}")
    for label, subset in (
            ("1. distinct live trip ids", rows),
            ("2. join gtfs_trips directly", direct),
            ("3. join only after stripping _N", stripped_only),
            ("4. do not join either way", unmatched),
            ("   (of which: carry an _N suffix)", suffixed),
    ):
        ids_pct, rows_pct = share(subset)
        print(f"  {label:32} {len(subset):>12,} {ids_pct:>7.2f}% {rows_pct:>7.2f}%")

    joinable = len(direct) + len(stripped_only)
    joinable_ids_pct = joinable / total_ids * 100
    joinable_rows_pct = (sum(r[2] for r in direct) + sum(r[2] for r in stripped_only)) \
        / total_positions * 100
    print(f"\n  joinable after stripping: {joinable:,} of {total_ids:,} ids "
          f"({joinable_ids_pct:.2f}%), {joinable_rows_pct:.2f}% of rows")

    if unmatched:
        print(f"\nunmatched examples (up to {arguments.examples}):")
        for trip_id, base, positions, _, _ in sorted(
                unmatched, key=lambda r: -r[2])[:arguments.examples]:
            note = f"  base={base}" if base != trip_id else ""
            print(f"  {trip_id:<18} {positions:>7,} positions{note}")

    # The gate from the build brief's amendment: below this, the detector would be
    # built on a join that silently loses trips.
    if joinable_ids_pct < 90:
        print(f"\nSTOP: only {joinable_ids_pct:.2f}% of live trip ids join the schedule "
              f"after stripping the suffix. The brief's threshold is 90%.")
        sys.exit(1)
    print(f"\nAbove the 90% threshold: safe to build arrival detection on this join.")


if __name__ == "__main__":
    main()
