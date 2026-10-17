-- One named minute of vehicle positions, for the map.
--
-- The deployed site has no collector: nothing in the cloud writes positions, so
-- /api/vehicles/live would answer empty forever and the map would permanently show its
-- "no recent data" notice. That is honest and it reads as broken, on the one page that
-- is worth looking at.
--
-- So the deployed map shows a historical moment and says which one, the way every
-- other figure on this site carries its period. This table holds that moment: one row
-- per vehicle, the newest position each reported inside the chosen minute.
--
-- It is curated rather than derived, so it is populated deliberately by
-- scripts/pick_map_snapshot.sql and travels to the cloud in the one-time restore.
-- raw_vehicle_positions itself does not: 1.4 million rows that nothing in the cloud
-- reads.

create table map_snapshot_positions (
    -- The minute this is, in UTC, identical on every row. Published with the response
    -- so the page can name the moment instead of implying it is live.
    snapshot_at timestamptz not null,

    vehicle_id text not null,
    route_id text,
    trip_id text,
    latitude real not null,
    longitude real not null,
    bearing real,
    -- The vehicle's own timestamp, which is what the map fades each dot by.
    vehicle_timestamp timestamptz not null,

    primary key (snapshot_at, vehicle_id)
);
