# scripts — Python utilities

`save_feeds.py` archives all three Halifax GTFS-Realtime feeds every 20 seconds to
`data/raw/<feed>/<date>/<HHMMSS>.pb.gz` in UTC, and downloads the static schedule zip
once a day. Standard library only, so it runs anywhere Python does. It keeps the machine
awake while it runs, reports gaps rather than hiding them, and never exits on a failed
poll — the next poll 20 seconds later is the retry.

```
python scripts/save_feeds.py
```

The archive it writes is the project's irreplaceable asset: Halifax never re-sends a
feed, so a snapshot not captured is gone permanently.

`load_gtfs.py` loads the published schedule into versioned `gtfs_*` tables. It reads
`feed_version` from `feed_info.txt`, falls back to the zip's SHA-256 if the publication
declares none, and refuses to touch a version that is already loaded — the schedule is
never overwritten, because trip ids are only stable within one publication (ADR-007).
Everything lands in one transaction, and `stop_times` is loaded with `COPY` because it
is hundreds of thousands of rows.

```
pip install -r scripts/requirements.txt
export POSTGRES_PASSWORD=...           # no default, anywhere in this project
python scripts/load_gtfs.py            # or --zip data/raw/static/<date>/google_transit.zip
```

Run it twice: the second run reports the version is already loaded and changes nothing.
