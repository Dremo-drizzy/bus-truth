package io.github.dremo_drizzy.bustruth.processor.store;

import io.github.dremo_drizzy.bustruth.common.OntimeEvent;
import io.github.dremo_drizzy.bustruth.processor.ontime.ScheduledArrival;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads arrivals beside the schedule in force, and writes the comparison. */
@Repository
public class OntimeEventRepository {

    /**
     * The publication of the schedule that was in force on a service date.
     *
     * <p>Not "the newest publication": the newest one is the one most recently
     * downloaded, and comparing a 4 October arrival against a zip published in
     * November would measure a promise nobody had made yet. A publication is in force
     * on a date when its own calendar covers that date, and among those the most
     * recently loaded wins, since a later publication supersedes an earlier one over
     * the span they share.
     *
     * <p>The date comparison is on text because GTFS stores dates as "yyyyMMdd", which
     * is fixed-width and zero-padded, so lexicographic order is chronological order.
     * Casting to date in SQL would work too, and would hide the assumption.
     */
    private static final String FEED_VERSION_IN_FORCE = """
            select v.feed_version
            from gtfs_feed_versions v
            where exists (
                select 1 from gtfs_calendar c
                where c.feed_version = v.feed_version
                  and c.start_date <= :date and c.end_date >= :date)
            order by v.loaded_at desc
            limit 1
            """;

    /**
     * Every arrival on a service date, left-joined to its published time.
     *
     * <p><b>Matched on (trip_id, stop_id), not on the stop sequence.</b> The obvious
     * join is on the sequence number, and it is wrong. Measured on 6,525 matched
     * arrivals: the live feed's {@code current_stop_sequence} and the zip's
     * {@code stop_sequence} name the same physical stop only 79% of the time, and
     * where they disagree the feed's number runs one to three places ahead, because
     * the feed's trip contains stops the published zip does not list and the drift
     * accumulates along the trip. Joining on the number silently compares each arrival
     * against a neighbouring stop's time: measured on the mismatched rows, a median of
     * 94 seconds out and a worst case of 16 minutes, against a five-minute window, in
     * a consistent direction, on a fifth of all rows (ADR-015).
     *
     * <p>Stop ids, by contrast, agree: every mismatched arrival but 32 of 6,525 found
     * its own stop elsewhere in the same published trip.
     *
     * <p>A stop can appear twice in one trip — a loop, or a terminus served at both
     * ends — so the sequence is kept as a tie-breaker, choosing the occurrence nearest
     * the feed's number. That is the weakest step here, since it leans on the very
     * number just shown to drift, which is why those rows are counted apart rather
     * than mixed in.
     *
     * <p>A left join, so arrivals the schedule has nothing for still come back and can
     * be counted. An inner join would delete the problem from the result set and
     * report a match rate of 100% by construction.
     *
     * <p>arrival_time is preferred and departure_time is the fallback: both are
     * optional in GTFS at stops without a timepoint, and Halifax publishes the pair
     * identically at most stops, so the fallback costs nothing and saves the rows
     * where only one is present.
     */
    private static final String ARRIVALS_WITH_SCHEDULE = """
            select a.trip_id, a.stop_id, a.stop_sequence, a.service_date,
                   a.observed_arrival_at, a.confidence,
                   coalesce(st.arrival_time, st.departure_time) as scheduled_time,
                   st.stop_sequence as scheduled_stop_sequence,
                   coalesce(st.occurrences, 0) as scheduled_occurrences,
                   t.route_id
            from arrival_events a
            left join lateral (
                select s.stop_sequence, s.arrival_time, s.departure_time,
                       -- Counted over the whole match set, which the window sees
                       -- before the limit discards all but the nearest.
                       count(*) over () as occurrences
                from gtfs_stop_times s
                where s.feed_version = :feedVersion
                  and s.trip_id = a.trip_id
                  and s.stop_id = a.stop_id
                order by abs(s.stop_sequence - a.stop_sequence)
                limit 1
            ) st on true
            left join gtfs_trips t
                   on t.feed_version = :feedVersion
                  and t.trip_id = a.trip_id
            where a.service_date = :serviceDate
            order by a.trip_id, a.stop_sequence
            """;

    private static final String DELETE_RANGE = """
            delete from ontime_events where service_date >= :from and service_date <= :to
            """;

    private static final String INSERT = """
            insert into ontime_events
                (trip_id, stop_id, stop_sequence, service_date, feed_version, route_id,
                 observed_arrival_at, scheduled_arrival_at, scheduled_time_text,
                 delay_seconds, on_time, arrival_confidence,
                 scheduled_stop_sequence, stop_match_ambiguous)
            values (:tripId, :stopId, :stopSequence, :serviceDate, :feedVersion, :routeId,
                 :observedArrivalAt, :scheduledArrivalAt, :scheduledTimeText,
                 :delaySeconds, :onTime, :arrivalConfidence,
                 :scheduledStopSequence, :stopMatchAmbiguous)
            on conflict (trip_id, service_date, stop_sequence) do nothing
            """;

    private final JdbcClient jdbcClient;

    public OntimeEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> feedVersionInForce(LocalDate serviceDate) {
        return jdbcClient.sql(FEED_VERSION_IN_FORCE)
                .param("date", serviceDate.toString().replace("-", ""))
                .query(String.class)
                .optional();
    }

    public List<ScheduledArrival> arrivalsWithSchedule(LocalDate serviceDate, String feedVersion) {
        return jdbcClient.sql(ARRIVALS_WITH_SCHEDULE)
                .param("serviceDate", serviceDate)
                .param("feedVersion", feedVersion)
                .query((rs, rowNum) -> new ScheduledArrival(
                        rs.getString("trip_id"),
                        rs.getString("stop_id"),
                        rs.getInt("stop_sequence"),
                        rs.getObject("service_date", LocalDate.class),
                        rs.getObject("observed_arrival_at", OffsetDateTime.class).toInstant(),
                        rs.getString("confidence"),
                        rs.getString("scheduled_time"),
                        rs.getObject("scheduled_stop_sequence", Integer.class),
                        rs.getInt("scheduled_occurrences"),
                        rs.getString("route_id")))
                .list();
    }

    /** Clears a range so it can be rebuilt, for the same reason detection does. */
    public int deleteRange(LocalDate from, LocalDate to) {
        return jdbcClient.sql(DELETE_RANGE).param("from", from).param("to", to).update();
    }

    public int insertAll(List<OntimeEvent> events) {
        int written = 0;
        for (OntimeEvent event : events) {
            written += jdbcClient.sql(INSERT)
                    .param("tripId", event.tripId())
                    .param("stopId", event.stopId())
                    .param("stopSequence", event.stopSequence())
                    .param("serviceDate", event.serviceDate())
                    .param("feedVersion", event.feedVersion())
                    .param("routeId", event.routeId())
                    .param("observedArrivalAt", event.observedArrivalAt().atOffset(ZoneOffset.UTC))
                    .param("scheduledArrivalAt", event.scheduledArrivalAt().atOffset(ZoneOffset.UTC))
                    .param("scheduledTimeText", event.scheduledTimeText())
                    .param("delaySeconds", event.delaySeconds())
                    .param("onTime", event.onTime())
                    .param("arrivalConfidence", event.arrivalConfidence())
                    .param("scheduledStopSequence", event.scheduledStopSequence())
                    .param("stopMatchAmbiguous", event.stopMatchAmbiguous())
                    .update();
        }
        return written;
    }
}
