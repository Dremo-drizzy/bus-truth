-- What each on-time run refused, per service date.
--
-- These counts existed only in a log line, which makes them unpublishable: a guard
-- that silently refuses three rows looks exactly like a guard silently refusing nine
-- thousand. The same rule that governs every percentage on this site — report the
-- count it was computed from — applies to the rows that were thrown away.
--
-- One row per service date, rewritten whenever the range is rebuilt, so it always
-- describes the ontime_events currently in the table rather than some earlier run.

create table ontime_quality (
    service_date date primary key,
    -- The publication the comparison was made against, so a disputed count can be
    -- traced to the schedule it used (ADR-007).
    feed_version text not null,

    arrivals_considered integer not null,
    matched integer not null,
    -- No published time for this trip and stop in the schedule in force.
    unmatched integer not null,
    -- A published time that could not be parsed at all. A bug here, not a
    -- disagreement between sources, which is why it is counted separately.
    unusable_schedule_time integer not null,
    -- Delay beyond twelve hours: the service date and the published time disagreeing
    -- about which day the trip belongs to. Counted, never stored as a delay — a bus
    -- is never twenty-four hours early (ADR-019).
    implausible_delays_refused integer not null,

    computed_at timestamptz not null default clock_timestamp()
);
