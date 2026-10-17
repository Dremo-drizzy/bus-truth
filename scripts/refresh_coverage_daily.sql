-- Recounts collection coverage per day from the raw positions.
--
--   psql -h localhost -p 55432 -U bustruth -d bustruth -f scripts/refresh_coverage_daily.sql
--
-- Run it before dumping for the hosted site (docs/DEPLOY.md, step 4), and whenever the
-- local figures should catch up with collection. Every row gets the same computed_at,
-- which /api/coverage publishes, so a stale copy says how stale it is.
--
-- The counting rule is unchanged from when the API did this on every request: distinct
-- first_feed_timestamp values per day in America/Halifax. The collector skips snapshots
-- whose contents have not changed, so this counts snapshots that reached the database.

begin;

delete from coverage_daily;

insert into coverage_daily (service_date, snapshots_captured, computed_at)
select (vehicle_timestamp at time zone 'America/Halifax')::date,
       count(distinct first_feed_timestamp),
       clock_timestamp()
from raw_vehicle_positions
group by 1;

commit;

select count(*) as days,
       min(service_date) as first_day,
       max(service_date) as last_day,
       max(computed_at) as computed_at
from coverage_daily;
