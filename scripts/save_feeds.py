"""Archive the Halifax Transit GTFS-Realtime feeds.

Polls the three realtime feeds every 20 seconds and writes each response to
data/raw/<feed>/<YYYY-MM-DD>/<HHMMSS>.pb.gz, skipping snapshots identical to the
previous one. Downloads the static GTFS zip once per UTC day.

Standard library only. Runs until interrupted; failures are logged, never fatal.

    python scripts/save_feeds.py
"""

import gzip
import hashlib
import logging
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

# --- configuration ---------------------------------------------------------

# Folder name -> URL. The folder names match the Kafka topic suffixes
# (hfx.vehicle-positions etc.) so the same vocabulary is used everywhere.
FEEDS = {
    "vehicle-positions": "https://gtfs.halifax.ca/realtime/Vehicle/VehiclePositions.pb",
    "trip-updates": "https://gtfs.halifax.ca/realtime/TripUpdate/TripUpdates.pb",
    "alerts": "https://gtfs.halifax.ca/realtime/Alert/Alerts.pb",
}

STATIC_URL = "https://gtfs.halifax.ca/static/google_transit.zip"

POLL_SECONDS = 20
STATUS_SECONDS = 300  # one "still alive" line every 5 minutes
HTTP_TIMEOUT = 15  # per request; must stay below POLL_SECONDS
USER_AGENT = "bus-truth-saver/1.0 (+https://github.com/Dremo-drizzy/bus-truth)"

# Paths are derived from this file's location, so the script works from any
# working directory: scripts/save_feeds.py -> repo root -> data/raw.
REPO_ROOT = Path(__file__).resolve().parent.parent
RAW_DIR = REPO_ROOT / "data" / "raw"
LOG_FILE = RAW_DIR / "saver.log"

log = logging.getLogger("saver")


# --- helpers ---------------------------------------------------------------


def configure_logging() -> None:
    """Log to data/raw/saver.log and to the console, both with UTC timestamps."""
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    formatter = logging.Formatter(
        "%(asctime)sZ %(levelname)s %(message)s", datefmt="%Y-%m-%dT%H:%M:%S"
    )
    formatter.converter = time.gmtime  # UTC everywhere, not the machine's zone

    file_handler = logging.FileHandler(LOG_FILE, encoding="utf-8")
    file_handler.setFormatter(formatter)
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)

    log.setLevel(logging.INFO)
    log.addHandler(file_handler)
    log.addHandler(console_handler)


def fetch(url: str) -> bytes:
    """Download a URL and return its bytes. Raises on any failure."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=HTTP_TIMEOUT) as response:
        return response.read()


def write_atomically(path: Path, payload: bytes) -> None:
    """Write bytes via a temporary file, then rename into place.

    A rename is atomic on the same filesystem, so a reader (or a crash) never
    sees a half-written .pb file.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_bytes(payload)
    os.replace(temp_path, path)


def read_snapshot(path: Path) -> bytes:
    """Return a snapshot's original bytes, whether or not it is gzipped."""
    if path.suffix == ".gz":
        return gzip.decompress(path.read_bytes())
    return path.read_bytes()


def last_saved_digest(feed: str) -> str | None:
    """SHA-256 of the newest snapshot already on disk for this feed, if any.

    Called once at start-up so that a restart does not re-save a snapshot
    identical to the last one from the previous run. The digest is always of the
    uncompressed bytes, so it stays comparable across the change to gzip.
    """
    feed_dir = RAW_DIR / feed
    if not feed_dir.is_dir():
        return None
    day_dirs = sorted((d for d in feed_dir.iterdir() if d.is_dir()), reverse=True)
    for day_dir in day_dirs:
        # Sorted by name, which is HHMMSS, so the last entry is the newest.
        files = sorted(list(day_dir.glob("*.pb.gz")) + list(day_dir.glob("*.pb")))
        if files:
            return hashlib.sha256(read_snapshot(files[-1])).hexdigest()
    return None


def save_snapshot(feed: str, payload: bytes, now: datetime) -> Path:
    """Write one gzipped snapshot under data/raw/<feed>/<date>/<time>.pb.gz (UTC).

    Compressing at write time keeps the archive about a fifth of its raw size
    (ADR-010) and avoids ever having an uncompressed backlog to clean up.
    """
    path = RAW_DIR / feed / now.strftime("%Y-%m-%d") / f"{now.strftime('%H%M%S')}.pb.gz"
    write_atomically(path, gzip.compress(payload, 6))
    return path


def download_static_once_per_day(now: datetime, counters: dict) -> None:
    """Fetch the static GTFS zip if today's copy is not already on disk.

    The file's existence is the record of "done today", so a restart does not
    download it again.
    """
    target = RAW_DIR / "static" / now.strftime("%Y-%m-%d") / "google_transit.zip"
    if target.exists():
        return
    try:
        payload = fetch(STATIC_URL)
        write_atomically(target, payload)
        log.info("static GTFS saved (%d bytes) -> %s", len(payload), target)
    except Exception as error:  # noqa: BLE001 - a bad zip must not stop polling
        counters["errors"] += 1
        log.error("static GTFS download failed: %s", error)


def poll_feed(feed: str, url: str, digests: dict, counters: dict) -> None:
    """Fetch one feed and save it unless it is byte-identical to the last one."""
    try:
        payload = fetch(url)
    except urllib.error.HTTPError as error:
        counters["errors"] += 1
        log.error("%s: HTTP %s %s", feed, error.code, error.reason)
        return
    except Exception as error:  # noqa: BLE001 - timeouts, DNS, resets, anything
        counters["errors"] += 1
        log.error("%s: %s", feed, error)
        return

    counters["polls"] += 1

    if not payload:
        counters["errors"] += 1
        log.error("%s: empty response", feed)
        return

    # Compare hashes rather than the bytes themselves: a digest is 32 bytes to
    # hold in memory instead of a whole snapshot, and equal digests here mean
    # equal content for any realistic purpose.
    digest = hashlib.sha256(payload).hexdigest()
    if digest == digests.get(feed):
        counters["skipped"] += 1
        return

    try:
        path = save_snapshot(feed, payload, datetime.now(timezone.utc))
    except Exception as error:  # noqa: BLE001 - disk full, permissions, etc.
        counters["errors"] += 1
        log.error("%s: could not write snapshot: %s", feed, error)
        return

    digests[feed] = digest
    counters["saved"] += 1
    counters["last_saved"] = path.name


def log_status(started: float, counters: dict) -> None:
    """One line proving the loop is alive, with totals since start-up."""
    uptime = time.monotonic() - started
    hours, remainder = divmod(int(uptime), 3600)
    minutes, seconds = divmod(remainder, 60)
    log.info(
        "alive %02d:%02d:%02d | polls=%d saved=%d skipped=%d errors=%d | last=%s",
        hours,
        minutes,
        seconds,
        counters["polls"],
        counters["saved"],
        counters["skipped"],
        counters["errors"],
        counters["last_saved"],
    )


# --- main loop -------------------------------------------------------------


def main() -> None:
    configure_logging()
    log.info("saver starting | writing to %s | every %ds", RAW_DIR, POLL_SECONDS)

    # Seed from disk so a restart does not duplicate the last snapshot.
    digests = {feed: last_saved_digest(feed) for feed in FEEDS}
    counters = {"polls": 0, "saved": 0, "skipped": 0, "errors": 0, "last_saved": "-"}

    started = time.monotonic()
    next_poll = started
    next_status = started + STATUS_SECONDS

    while True:
        try:
            now = datetime.now(timezone.utc)
            download_static_once_per_day(now, counters)

            for feed, url in FEEDS.items():
                poll_feed(feed, url, digests, counters)

            if time.monotonic() >= next_status:
                log_status(started, counters)
                next_status += STATUS_SECONDS

            # Schedule from a fixed grid rather than sleeping 20s after the work
            # finishes, so the interval does not drift by the request time. If a
            # cycle overruns, skip ahead instead of trying to catch up.
            next_poll += POLL_SECONDS
            delay = next_poll - time.monotonic()
            if delay < 0:
                next_poll = time.monotonic()
                delay = 0
            time.sleep(delay)

        except KeyboardInterrupt:
            log_status(started, counters)
            log.info("saver stopped by user")
            return
        except Exception as error:  # noqa: BLE001 - the loop itself must survive
            counters["errors"] += 1
            log.exception("unexpected error in main loop: %s", error)
            time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    main()
