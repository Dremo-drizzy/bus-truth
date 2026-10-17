-- Collection coverage per day, stored as a published figure rather than recomputed.
--
-- /api/coverage used to count snapshots straight from raw_vehicle_positions. The
-- hosted database does not hold that table — 1.4 million rows that nothing else in the
-- cloud reads — so on the deployed site the endpoint failed with a 500. Coverage is a
-- figure this project publishes, so it is stored as one: one small row per day,
-- computed where the raw positions live and shipped with the other derived tables.
--
-- computed_at is the point. Collection carries on locally after a table like this is
-- loaded, so the moment it is copied anywhere it is a snapshot that drifts from the
-- truth one day at a time. Every other figure on the site carries its period; this one
-- carries the date it was counted, and the API publishes it beside every row.
--
-- Refreshed by scripts/refresh_coverage_daily.sql.

create table coverage_daily (
    -- The day in America/Halifax, the same bucketing the raw query used.
    service_date date primary key,
    -- Distinct feed snapshots that reached the database that day.
    snapshots_captured integer not null,
    computed_at timestamptz not null default clock_timestamp()
);
