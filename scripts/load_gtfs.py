"""Load Halifax Transit's published schedule into versioned gtfs_* tables.

Downloads https://gtfs.halifax.ca/static/google_transit.zip (or reads a local copy),
identifies the publication, and loads it under its own feed_version. A version that
is already loaded is left alone: the schedule is never overwritten, because ids are
only stable within one publication and overwriting would silently re-point historical
arrivals at a different timetable (ADR-007).

    python scripts/load_gtfs.py                      # download the current zip
    python scripts/load_gtfs.py --zip path/to.zip    # load an archived one

Connection settings come from the environment, with the same defaults the Java
services use. POSTGRES_PASSWORD has no default anywhere in this project.
"""

import argparse
import csv
import hashlib
import io
import os
import sys
import urllib.request
import zipfile
from datetime import datetime, timezone

import psycopg

STATIC_URL = "https://gtfs.halifax.ca/static/google_transit.zip"
USER_AGENT = "bus-truth-gtfs-loader/1.0 (+https://github.com/Dremo-drizzy/bus-truth)"

# Each table: the GTFS file it comes from, its columns in order, and which of those
# are required by the schema. Declaring it this way keeps the loading loop identical
# for every table and puts the column mapping somewhere it can be read.
TABLES = (
    ("gtfs_routes", "routes.txt",
     ("route_id", "route_short_name", "route_long_name", "route_type")),
    ("gtfs_stops", "stops.txt",
     ("stop_id", "stop_code", "stop_name", "stop_lat", "stop_lon",
      "location_type", "parent_station")),
    ("gtfs_trips", "trips.txt",
     ("trip_id", "route_id", "service_id", "direction_id", "trip_headsign", "shape_id")),
    ("gtfs_calendar", "calendar.txt",
     ("service_id", "monday", "tuesday", "wednesday", "thursday", "friday",
      "saturday", "sunday", "start_date", "end_date")),
    ("gtfs_calendar_dates", "calendar_dates.txt",
     ("service_id", "date", "exception_type")),
    # stop_times is the large one and is loaded separately, with COPY.
)

STOP_TIMES_COLUMNS = ("trip_id", "stop_sequence", "stop_id", "arrival_time",
                      "departure_time", "pickup_type", "drop_off_type")

INTEGER_COLUMNS = {"route_type", "location_type", "direction_id", "exception_type",
                   "stop_sequence", "pickup_type", "drop_off_type"}
FLOAT_COLUMNS = {"stop_lat", "stop_lon"}
BOOLEAN_COLUMNS = {"monday", "tuesday", "wednesday", "thursday", "friday",
                   "saturday", "sunday"}


def connection_string() -> str:
    password = os.environ.get("POSTGRES_PASSWORD")
    if not password:
        sys.exit("POSTGRES_PASSWORD is not set. It has no default anywhere in this "
                 "project; export it, or source the values in .env.")
    return (
        f"host={os.environ.get('POSTGRES_HOST', 'localhost')} "
        # 55432, not 5432: native PostgreSQL services on the development machine own
        # 5432, and reaching the wrong server is a silent failure.
        f"port={os.environ.get('POSTGRES_PORT', '55432')} "
        f"dbname={os.environ.get('POSTGRES_DB', 'bustruth')} "
        f"user={os.environ.get('POSTGRES_USER', 'bustruth')} "
        f"password={password}"
    )


def fetch_zip(path: str | None) -> bytes:
    if path:
        with open(path, "rb") as f:
            return f.read()
    request = urllib.request.Request(STATIC_URL, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def read_rows(archive: zipfile.ZipFile, member: str) -> list[dict[str, str]]:
    """Returns [] for an absent file: calendar_dates.txt is optional in GTFS."""
    if member not in archive.namelist():
        return []
    with archive.open(member) as raw:
        # utf-8-sig: GTFS files are routinely published with a byte-order mark, which
        # would otherwise become part of the first column's name.
        return list(csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig")))


def identify(archive: zipfile.ZipFile, digest: str) -> tuple[str, bool]:
    """The publication's own version, or the zip's digest if it does not declare one."""
    for row in read_rows(archive, "feed_info.txt"):
        declared = (row.get("feed_version") or "").strip()
        if declared:
            return declared, True
    return digest[:16], False


def coerce(column: str, value: str | None):
    """GTFS is all text on the wire; empty means absent, not zero."""
    if value is None or value == "":
        return None
    if column in INTEGER_COLUMNS:
        return int(value)
    if column in FLOAT_COLUMNS:
        return float(value)
    if column in BOOLEAN_COLUMNS:
        return value == "1"
    return value


def load(zip_bytes: bytes) -> None:
    digest = hashlib.sha256(zip_bytes).hexdigest()
    archive = zipfile.ZipFile(io.BytesIO(zip_bytes))
    feed_version, declared = identify(archive, digest)

    print(f"zip: {len(zip_bytes):,} bytes, sha256 {digest[:16]}…")
    if declared:
        print(f"feed_version: {feed_version} (from feed_info.txt)")
    else:
        print(f"feed_version: {feed_version} (feed_info.txt declared none; using the "
              f"zip's digest)")

    with psycopg.connect(connection_string()) as connection:
        with connection.cursor() as cursor:
            cursor.execute("select loaded_at from gtfs_feed_versions where feed_version = %s",
                           (feed_version,))
            existing = cursor.fetchone()
            if existing:
                print(f"\nAlready loaded at {existing[0]}. Nothing changed — a loaded "
                      f"version is never overwritten.")
                return

            # One transaction: either the whole version lands or none of it. A
            # half-loaded schedule would look complete and produce wrong joins.
            cursor.execute(
                "insert into gtfs_feed_versions (feed_version, source_sha256, source_bytes) "
                "values (%s, %s, %s)",
                (feed_version, digest, len(zip_bytes)))

            counts: dict[str, int] = {"gtfs_feed_versions": 1}

            for table, member, columns in TABLES:
                rows = read_rows(archive, member)
                if not rows:
                    counts[table] = 0
                    continue
                placeholders = ", ".join(["%s"] * (len(columns) + 1))
                statement = (f"insert into {table} (feed_version, {', '.join(columns)}) "
                             f"values ({placeholders})")
                cursor.executemany(statement, [
                    [feed_version] + [coerce(c, row.get(c)) for c in columns]
                    for row in rows
                ])
                counts[table] = len(rows)

            counts["gtfs_stop_times"] = copy_stop_times(cursor, archive, feed_version)

        connection.commit()

    print("\nrows loaded")
    for table, count in counts.items():
        print(f"  {table:22} {count:>9,}")


def copy_stop_times(cursor, archive: zipfile.ZipFile, feed_version: str) -> int:
    """COPY rather than inserts: this file is hundreds of thousands of rows.

    Duplicate (trip_id, stop_sequence) pairs are dropped rather than failing the whole
    load — a malformed publication should be reported, not abandoned.
    """
    columns = ", ".join(("feed_version",) + STOP_TIMES_COLUMNS)
    written = 0
    seen: set[tuple[str, int]] = set()
    duplicates = 0
    with cursor.copy(f"copy gtfs_stop_times ({columns}) from stdin") as copy:
        for row in read_rows(archive, "stop_times.txt"):
            key = (row["trip_id"], int(row["stop_sequence"]))
            if key in seen:
                duplicates += 1
                continue
            seen.add(key)
            copy.write_row([feed_version] + [coerce(c, row.get(c)) for c in STOP_TIMES_COLUMNS])
            written += 1
    if duplicates:
        print(f"  note: {duplicates} duplicate (trip_id, stop_sequence) rows in "
              f"stop_times.txt were skipped")
    return written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip", help="load a local zip instead of downloading")
    arguments = parser.parse_args()

    print(f"{datetime.now(timezone.utc).isoformat(timespec='seconds')} loading the "
          f"published schedule")
    load(fetch_zip(arguments.zip))


if __name__ == "__main__":
    main()
