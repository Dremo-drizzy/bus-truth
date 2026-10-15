-- The published schedule, versioned and never overwritten.
--
-- Every table is keyed on (feed_version, ...) because Halifax republishes the zip
-- every few weeks and ids are only stable within one publication: a trip id can be
-- retired and reused for a different trip. Overwriting would silently re-point old
-- arrivals at a different schedule, and nothing would error (ADR-007).

create table gtfs_feed_versions (
    feed_version  text primary key,
    loaded_at     timestamptz not null default clock_timestamp(),
    -- The zip's digest, so an unversioned feed can still be identified, and two
    -- loads of the same bytes are recognisable as such.
    source_sha256 text not null,
    source_bytes  bigint not null
);

create table gtfs_routes (
    feed_version text not null references gtfs_feed_versions,
    route_id text not null,
    route_short_name text,
    route_long_name text,
    route_type integer,
    primary key (feed_version, route_id)
);

create table gtfs_stops (
    feed_version text not null references gtfs_feed_versions,
    stop_id text not null,
    stop_code text,
    stop_name text,
    stop_lat double precision,
    stop_lon double precision,
    location_type integer,
    parent_station text,
    primary key (feed_version, stop_id)
);

create table gtfs_trips (
    feed_version text not null references gtfs_feed_versions,
    trip_id text not null,
    route_id text not null,
    service_id text not null,
    direction_id smallint,
    trip_headsign text,
    shape_id text,
    primary key (feed_version, trip_id)
);

create table gtfs_stop_times (
    feed_version text not null references gtfs_feed_versions,
    trip_id text not null,
    stop_sequence integer not null,
    stop_id text not null,
    -- TEXT, not time: GTFS permits times past 24:00:00 for trips that run after
    -- midnight, e.g. "25:10:00". Converting here would reject valid data.
    arrival_time text,
    departure_time text,
    pickup_type integer,
    drop_off_type integer,
    primary key (feed_version, trip_id, stop_sequence)
);

create table gtfs_calendar (
    feed_version text not null references gtfs_feed_versions,
    service_id text not null,
    monday boolean, tuesday boolean, wednesday boolean, thursday boolean,
    friday boolean, saturday boolean, sunday boolean,
    start_date text not null,
    end_date text not null,
    primary key (feed_version, service_id)
);

create table gtfs_calendar_dates (
    feed_version text not null references gtfs_feed_versions,
    service_id text not null,
    date text not null,
    exception_type integer not null,
    primary key (feed_version, service_id, date)
);

create index gtfs_stop_times_trip_idx on gtfs_stop_times (feed_version, trip_id);
create index gtfs_trips_route_idx on gtfs_trips (feed_version, route_id);
