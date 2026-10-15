-- Derived arrivals: when a bus is judged to have reached a stop.
--
-- Every row is a conclusion, not a report — the feed never says "arrived" — so the
-- method and the confidence travel with it. A consumer that ignores confidence will
-- mix observations with inferences, which is how a measurement becomes a claim.

create table arrival_events (
    -- The schedule-joinable id: any modified-trip _N suffix is already stripped, and
    -- confidence records that it was (ADR-013).
    trip_id text not null,
    stop_id text not null,
    stop_sequence integer not null,
    -- The agency's service date, not the calendar date of the arrival: a trip running
    -- at 00:30 in Halifax belongs to the previous service day.
    service_date date not null,
    observed_arrival_at timestamptz not null,
    method text not null,
    -- HIGH, SUFFIXED_TRIP or SPARSE_TRAIL.
    confidence text not null,
    detected_at timestamptz not null default clock_timestamp(),

    -- One arrival per stop per trip per service date. Re-running a date range
    -- therefore cannot double-count, and the delete-then-insert the processor does is
    -- belt and braces on top of it.
    primary key (trip_id, service_date, stop_sequence)
);

-- The stop-reliability page asks "how did this stop do on this day", which this
-- serves; the primary key already serves lookups by trip.
create index arrival_events_stop_idx on arrival_events (stop_id, service_date);
