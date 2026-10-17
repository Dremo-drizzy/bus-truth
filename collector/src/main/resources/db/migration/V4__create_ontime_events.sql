-- Derived punctuality: each observed arrival set against the time it was published for.
--
-- One row per matched arrival. Arrivals that could not be matched to a scheduled time
-- are deliberately absent rather than stored with a null delay: a table where some
-- rows mean "late by 90 seconds" and others mean "we don't know" is one careless
-- average away from a false claim. The unmatched count is reported by the run and
-- published alongside every percentage instead.

create table ontime_events (
    -- Same key as arrival_events, because this is that row plus a comparison.
    -- stop_sequence here is the live feed's number, which is the arrival's identity.
    trip_id text not null,
    stop_id text not null,
    stop_sequence integer not null,
    service_date date not null,

    -- Which publication of the schedule the comparison was made against. Halifax
    -- republishes every few weeks and trip ids are only stable within one
    -- publication, so a delay without a feed_version is not reproducible (ADR-007).
    feed_version text not null,
    -- Denormalised from gtfs_trips so the route page is one table scan rather than a
    -- join to a versioned schedule. Derived, and re-derived by every run.
    route_id text,

    observed_arrival_at timestamptz not null,
    scheduled_arrival_at timestamptz not null,
    -- The schedule's own text, e.g. "25:10:00". Kept so a disputed row can be traced
    -- back to the published value without re-deriving the timezone arithmetic.
    scheduled_time_text text not null,

    -- Observed minus scheduled. Negative is early. Signed integer, not an interval:
    -- every consumer wants to average it.
    delay_seconds integer not null,
    -- Inside the published on-time window. Stored rather than computed on read so a
    -- window change is a visible re-run, not a silent redefinition of past rows.
    on_time boolean not null,

    -- Carried through from the arrival: an on-time judgement about a SPARSE_TRAIL
    -- arrival is weaker than one about a HIGH arrival, and the distinction must
    -- survive into anything that aggregates these rows.
    arrival_confidence text not null,

    -- The sequence number the schedule gives this stop, which is routinely NOT the
    -- number the live feed reported for it. Measured on 6,525 matched arrivals: the
    -- two agree on 79% and differ by one to three places on the rest, because the
    -- feed's trip contains stops the published zip does not list and the drift
    -- accumulates along the trip. So the match is made on (trip_id, stop_id) and this
    -- column records the disagreement rather than hiding it (ADR-015).
    scheduled_stop_sequence integer not null,
    -- True when the trip calls at this stop more than once — a loop, or a terminus
    -- served at both ends — and the occurrence nearest the feed's number was taken.
    -- The match then rests on a sequence number this table has just shown to be
    -- unreliable, so these rows are weaker evidence and are counted apart.
    stop_match_ambiguous boolean not null,

    computed_at timestamptz not null default clock_timestamp(),

    primary key (trip_id, service_date, stop_sequence)
);

-- Route on-time performance for a day, the headline page.
create index ontime_events_route_idx on ontime_events (route_id, service_date);
-- Stop reliability for a day.
create index ontime_events_stop_idx on ontime_events (stop_id, service_date);
