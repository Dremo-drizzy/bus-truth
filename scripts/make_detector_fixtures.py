"""Build detector test fixtures from the real archive.

Writes one JSON file per scenario into processor/src/test/resources/fixtures. The
scenarios are found in captured data rather than invented, so the tests exercise
shapes Halifax actually produces — including the awkward ones.

    python scripts/make_detector_fixtures.py
"""

import gzip
import json
from collections import defaultdict
from pathlib import Path

from google.transit import gtfs_realtime_pb2

ARCHIVE = Path("data/raw/vehicle-positions")
OUT = Path("processor/src/test/resources/fixtures")


def positions_from_day(day: Path, limit: int) -> list[dict]:
    rows = []
    for f in sorted(day.glob("*.pb.gz"))[:limit]:
        message = gtfs_realtime_pb2.FeedMessage()
        message.ParseFromString(gzip.decompress(f.read_bytes()))
        for entity in message.entity:
            if not entity.HasField("vehicle"):
                continue
            v = entity.vehicle
            if not v.HasField("current_stop_sequence") or not v.HasField("timestamp"):
                continue
            rows.append({
                "vehicleId": v.vehicle.id or None,
                "vehicleLabel": v.vehicle.label or None,
                "tripId": v.trip.trip_id or None,
                "routeId": v.trip.route_id or None,
                "directionId": v.trip.direction_id if v.trip.HasField("direction_id") else None,
                "startDate": v.trip.start_date or None,
                "latitude": round(v.position.latitude, 5) if v.HasField("position") else None,
                "longitude": round(v.position.longitude, 5) if v.HasField("position") else None,
                "bearing": None,
                "speed": None,
                "odometer": None,
                "currentStopSequence": v.current_stop_sequence,
                "stopId": v.stop_id or None,
                "currentStatus": v.current_status if v.HasField("current_status") else None,
                "occupancyStatus": None,
                "occupancyPercentage": None,
                "timestamp": v.timestamp,
                "feedTimestamp": message.header.timestamp,
            })
    return rows


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    days = sorted(d for d in ARCHIVE.iterdir() if d.is_dir())
    # A dense run of snapshots so trails are continuous rather than full of holes.
    rows = positions_from_day(days[len(days) // 2], limit=180)

    trails: dict[tuple, list[dict]] = defaultdict(list)
    for r in rows:
        if r["tripId"] and r["vehicleId"]:
            trails[(r["vehicleId"], r["tripId"])].append(r)
    for trail in trails.values():
        trail.sort(key=lambda r: r["timestamp"])

    def transitions(trail: list[dict]) -> int:
        return sum(1 for a, b in zip(trail, trail[1:])
                   if b["currentStopSequence"] > a["currentStopSequence"])

    def jumps(trail: list[dict]) -> int:
        return sum(1 for a, b in zip(trail, trail[1:])
                   if b["currentStopSequence"] > a["currentStopSequence"] + 1)

    def max_gap(trail: list[dict]) -> int:
        return max((b["timestamp"] - a["timestamp"] for a, b in zip(trail, trail[1:])), default=0)

    candidates = sorted(trails.items(), key=lambda kv: -transitions(kv[1]))

    written = {}
    # A trail with many clean transitions: the ordinary case.
    ordinary = next(t for _, t in candidates if transitions(t) >= 3 and max_gap(t) <= 60)
    written["ordinary-trail.json"] = ordinary

    # A trail whose sequence jumps, so stops were passed unobserved.
    jumping = next((t for _, t in candidates if jumps(t) >= 1), ordinary)
    written["jumping-sequence.json"] = jumping

    # A trail with a real collection gap in it.
    gapped = next((t for _, t in candidates if max_gap(t) > 120), None)
    if gapped:
        written["gapped-trail.json"] = gapped

    # A suffixed trip id, the modified-trip case.
    suffixed = next((t for (v, trip), t in trails.items() if "_" in trip), None)
    if suffixed:
        written["suffixed-trip.json"] = suffixed

    for name, trail in written.items():
        (OUT/name).write_text(json.dumps(trail, indent=1), encoding="utf-8")
        print(f"{name}: {len(trail)} positions, {transitions(trail)} transitions, "
              f"{jumps(trail)} jumps, max gap {max_gap(trail)}s, trip {trail[0]['tripId']}")


if __name__ == "__main__":
    main()
