-- One row per vehicle report: the landing table for the hfx.vehicle-positions topic.
--
-- The primary key is the identity of an observation, not a surrogate. Delivery is
-- at-least-once, so the same message can arrive twice; the key plus
-- ON CONFLICT DO NOTHING is what makes a replay a no-op.

create table raw_vehicle_positions (
    -- The agency's id for the bus (GTFS-RT vehicle.id), not the feed's entity id:
    -- those differ, e.g. "2531" against "531".
    vehicle_id             text        not null,
    -- When the vehicle reported this position, which is the observation's identity.
    vehicle_timestamp      timestamptz not null,

    vehicle_label          text,
    trip_id                text,
    route_id               text,
    direction_id           smallint,
    -- As the agency sent it, "yyyyMMdd". Converting needs the America/Halifax
    -- service-date rule, which belongs to the processor.
    start_date             text,

    -- real, not double precision: the feed carries 32-bit floats, and widening would
    -- print digits the agency never reported. At Halifax's latitude float32 resolves
    -- to about half a metre, finer than GPS. Distance maths should cast to double
    -- precision inside the calculation rather than accumulate in real.
    latitude               real,
    longitude              real,
    bearing                real,
    speed                  real,
    -- Genuinely a double on the wire.
    odometer               double precision,

    current_stop_sequence  integer,
    stop_id                text,
    -- Enum names, never ordinals, so an unrecognised future value passes through as
    -- text instead of failing.
    current_status         text,
    occupancy_status       text,
    occupancy_percentage   integer,

    first_feed_timestamp   timestamptz not null,

    -- Nullable: a row can be written by a backfill or a test that has no Kafka
    -- delivery behind it, and a not-null constraint would force a fake value.
    kafka_partition        smallint,
    kafka_offset           bigint,

    -- clock_timestamp(), not now(): now() is transaction start time, which is
    -- identical at one row per transaction and wrong as soon as inserts are batched.
    inserted_at            timestamptz not null default clock_timestamp(),

    primary key (vehicle_id, vehicle_timestamp)
);

comment on column raw_vehicle_positions.first_feed_timestamp is
    'The feed snapshot that first carried this observation. A later snapshot carrying '
    'the same (vehicle_id, vehicle_timestamp) is discarded by ON CONFLICT DO NOTHING, '
    'and its feed timestamp with it.';

comment on column raw_vehicle_positions.kafka_offset is
    'Offset of the message that produced this row, so a row can be traced back to the '
    'delivery that created it. Null for rows not written from a Kafka message.';

-- (vehicle_id, vehicle_timestamp) is served by the primary key's own index.
-- This covers the other access path: one trip's positions in time order.
create index raw_vehicle_positions_trip_idx
    on raw_vehicle_positions (trip_id, vehicle_timestamp);
