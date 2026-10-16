-- Fills map_snapshot_positions with one named minute of the network.
--
-- Run it once against the local database, before the restore in docs/DEPLOY.md:
--
--   psql -h localhost -p 55432 -U bustruth -d bustruth -f scripts/pick_map_snapshot.sql
--
-- WHICH MINUTE, AND WHY. 17:08 local on Tuesday 6 October 2026 carried 290 distinct
-- vehicles, more than any other minute of any 17:00 hour in the published week. The
-- hour matters: 17:00 is the worst hour on the network (45.3% on time against 79.4% at
-- 06:00), and 6 October is the worst day of the week (48.7%). So the map shows Halifax
-- Transit at its least punctual hour of its least punctual day, which is what the
-- findings on this site are about — and it is the fullest minute available, so it is
-- also the busiest the map will ever look.
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
