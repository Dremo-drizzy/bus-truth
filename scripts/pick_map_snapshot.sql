-- Fills map_snapshot_positions with one named minute of the network.
--
-- Run it once against the local database, before the restore in docs/DEPLOY.md:
--
--   psql -h localhost -p 55432 -U bustruth -d bustruth -f scripts/pick_map_snapshot.sql
--
-- WHICH MINUTE, AND WHY. 17:08 local on Tuesday 6 October 2026 carried 290 distinct
-- vehicles, more than any other minute of any 17:00 hour in the published week.
--
-- The hour is the reason: 17:00 is the least punctual hour on the network, 45.3% on
-- time against 79.4% at 06:00, and that figure is computed across every day at once so
-- it does not depend on which days happened to be watched. The map therefore shows
-- Halifax Transit at its worst hour, which is what the findings are about, and being
-- the fullest minute available it is also the busiest the map will ever look.
--
-- The DAY is not part of the claim. 6 October recorded the week's lowest on-time share,
-- but day-to-day comparison is confounded by uneven coverage, so this project does not
-- publish it — see the README. The minute was chosen for its hour and its vehicle
-- count, and any other busy 17:00 minute in the week would have served.
--
-- Changing the minute means changing it here and in MapSnapshotRepository's javadoc,
-- and re-running this. The label the site shows is derived from snapshot_at, so it
-- cannot drift from the data.

begin;

delete from map_snapshot_positions;

insert into map_snapshot_positions
    (snapshot_at, vehicle_id, route_id, trip_id, latitude, longitude, bearing,
     vehicle_timestamp)
select distinct on (p.vehicle_id)
       timestamptz '2026-10-06 17:08:00-03' as snapshot_at,
       p.vehicle_id, p.route_id, p.trip_id, p.latitude, p.longitude, p.bearing,
       p.vehicle_timestamp
from raw_vehicle_positions p
where p.vehicle_timestamp >= timestamptz '2026-10-06 17:08:00-03'
  and p.vehicle_timestamp <  timestamptz '2026-10-06 17:09:00-03'
  and p.latitude is not null and p.longitude is not null
-- The newest position each vehicle reported inside the minute, which is the same rule
-- the live endpoint applies.
order by p.vehicle_id, p.vehicle_timestamp desc;

commit;

select count(*) as vehicles,
       min(vehicle_timestamp) as earliest,
       max(vehicle_timestamp) as latest,
       pg_size_pretty(pg_total_relation_size('map_snapshot_positions')) as size
from map_snapshot_positions;
