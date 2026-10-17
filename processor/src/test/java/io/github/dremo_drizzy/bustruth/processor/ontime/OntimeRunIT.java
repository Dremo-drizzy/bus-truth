package io.github.dremo_drizzy.bustruth.processor.ontime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The join from observation to promise, against a real PostgreSQL.
 *
 * <p>Most of what matters here is SQL behaviour — a left join that keeps unmatched
 * rows, a feed version chosen by what its calendar covers rather than by when it was
 * downloaded — so testing it in Java with stubs would test the stubs.
 */
@SpringBootTest(properties = {
        "POSTGRES_PASSWORD=unused",
        "spring.flyway.enabled=false"
})
@Import(OntimeRunIT.Containers.class)
class OntimeRunIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 10, 14);
    private static final String IN_FORCE = "2026_08_August_20260806";

    @Autowired
    private OntimeRun ontimeRun;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        // The collector owns these tables; creating the columns this reads means a
        // schema change there fails this test rather than silently passing.
        jdbcClient.sql("""
                create table if not exists arrival_events (
                    trip_id text not null, stop_id text not null,
                    stop_sequence integer not null, service_date date not null,
                    observed_arrival_at timestamptz not null,
                    method text not null, confidence text not null,
                    detected_at timestamptz not null default clock_timestamp(),
                    primary key (trip_id, service_date, stop_sequence))
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_feed_versions (
                    feed_version text primary key,
                    loaded_at timestamptz not null default clock_timestamp(),
                    source_sha256 text not null, source_bytes bigint not null)
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_calendar (
                    feed_version text not null, service_id text not null,
                    monday boolean, tuesday boolean, wednesday boolean, thursday boolean,
                    friday boolean, saturday boolean, sunday boolean,
                    start_date text not null, end_date text not null,
                    primary key (feed_version, service_id))
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_trips (
                    feed_version text not null, trip_id text not null,
                    route_id text not null, service_id text not null,
                    direction_id smallint, trip_headsign text, shape_id text,
                    primary key (feed_version, trip_id))
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_stop_times (
                    feed_version text not null, trip_id text not null,
                    stop_sequence integer not null, stop_id text not null,
                    arrival_time text, departure_time text,
                    pickup_type integer, drop_off_type integer,
                    primary key (feed_version, trip_id, stop_sequence))
                """).update();
        jdbcClient.sql("""
                create table if not exists ontime_events (
                    trip_id text not null, stop_id text not null,
                    stop_sequence integer not null, service_date date not null,
                    feed_version text not null, route_id text,
                    observed_arrival_at timestamptz not null,
                    scheduled_arrival_at timestamptz not null,
                    scheduled_time_text text not null,
                    delay_seconds integer not null, on_time boolean not null,
                    arrival_confidence text not null,
                    scheduled_stop_sequence integer not null,
                    stop_match_ambiguous boolean not null,
                    computed_at timestamptz not null default clock_timestamp(),
                    primary key (trip_id, service_date, stop_sequence))
                """).update();

        jdbcClient.sql("""
                create table if not exists ontime_quality (
                    service_date date primary key, feed_version text not null,
                    arrivals_considered integer not null, matched integer not null,
                    unmatched integer not null, unusable_schedule_time integer not null,
                    implausible_delays_refused integer not null,
                    computed_at timestamptz not null default clock_timestamp())
                """).update();

        jdbcClient.sql("truncate table arrival_events, ontime_events, ontime_quality, "
                + "gtfs_stop_times, gtfs_trips, gtfs_calendar, gtfs_feed_versions").update();

        feedVersion(IN_FORCE, "20260824", "20261129", OffsetDateTime.parse("2026-08-06T00:00Z"));
        trip(IN_FORCE, "4659595", "1");
        stopTime(IN_FORCE, "4659595", 1, "8184", "08:00:00");
        stopTime(IN_FORCE, "4659595", 2, "8185", "08:05:00");
        stopTime(IN_FORCE, "4659595", 3, "8186", "08:10:00");
    }

    @Test
    @DisplayName("an arrival inside the window is on time, and the delay is signed")
    void comparesAnArrivalAgainstItsPublishedTime() {
        // 08:02:30 local against a published 08:00:00: 150 seconds late, inside the
        // five-minute window.
        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.matched()).isEqualTo(1);
        assertThat(counts.onTime()).isEqualTo(1);
        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("delay_seconds", 150)
                .containsEntry("on_time", true)
                .containsEntry("route_id", "1")
                .containsEntry("feed_version", IN_FORCE)
                .containsEntry("scheduled_time_text", "08:00:00")
                .containsEntry("scheduled_stop_sequence", 1)
                .containsEntry("stop_match_ambiguous", false);
    }

    @Test
    @DisplayName("early and late are counted separately, not lumped as 'not on time'")
    void sortsArrivalsIntoEarlyOnTimeAndLate() {
        // -90s: earlier than the window's one minute. +150s: inside. +400s: beyond five
        // minutes. Early and late are different failures for a passenger — an early bus
        // is one you missed — so they are never merged.
        arrival("4659595", 1, "8184", "2026-10-14T10:58:30Z", "HIGH");
        arrival("4659595", 2, "8185", "2026-10-14T11:07:30Z", "HIGH");
        arrival("4659595", 3, "8186", "2026-10-14T11:16:40Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.early()).isEqualTo(1);
        assertThat(counts.onTime()).isEqualTo(1);
        assertThat(counts.late()).isEqualTo(1);
        assertThat(counts.matched()).isEqualTo(3);
        assertThat(counts.onTimeRate()).isEqualTo(1.0 / 3);
    }

    @Test
    @DisplayName("an arrival with no published time is counted, not stored")
    void countsUnmatchedArrivalsWithoutStoringThem() {
        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");
        // A trip the published zip has never heard of, and a stop the zip does not have
        // on a trip it does. Both are real: the live feed runs trips and calls at stops
        // the published zip does not contain, which is the project's whole subject.
        arrival("9999999", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");
        arrival("4659595", 9, "8190", "2026-10-14T11:40:00Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.arrivalsConsidered()).isEqualTo(3);
        assertThat(counts.matched()).isEqualTo(1);
        assertThat(counts.unmatched()).isEqualTo(2);
        assertThat(counts.matchRate()).isEqualTo(1.0 / 3);
        // The unmatched two are absent rather than stored with a null delay: a table
        // mixing "late by 90 seconds" with "we don't know" is one average away from a
        // false claim.
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the schedule in force is chosen by its calendar, not by what loaded last")
    void prefersTheFeedVersionInForceOverTheNewestOne() {
        // A publication downloaded later, covering a period that starts after the
        // service date — the normal state of affairs a few weeks on. Its times are
        // deliberately an hour out, so using it would be visible in the delay.
        feedVersion("2026_12_December_20261201", "20261130", "20270228",
                OffsetDateTime.parse("2026-12-01T00:00Z"));
        trip("2026_12_December_20261201", "4659595", "1");
        stopTime("2026_12_December_20261201", "4659595", 1, "8184", "09:00:00");

        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");

        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(onlyRow()).containsEntry("feed_version", IN_FORCE)
                .containsEntry("delay_seconds", 150);
    }

    @Test
    @DisplayName("a service date no publication covers is skipped, not measured anyway")
    void skipsDatesWithNoScheduleInForce() {
        LocalDate beforeAnySchedule = LocalDate.of(2026, 1, 5);
        jdbcClient.sql("""
                        insert into arrival_events
                            (trip_id, stop_id, stop_sequence, service_date,
                             observed_arrival_at, method, confidence)
                        values ('4659595', '8184', 1, :date, :at, 'POSITION_SEQUENCE', 'HIGH')
                        """)
                .param("date", beforeAnySchedule)
                .param("at", OffsetDateTime.parse("2026-01-05T12:00:00Z"))
                .update();

        OntimeCounts counts = ontimeRun.run(beforeAnySchedule, beforeAnySchedule);

        assertThat(counts.daysWithoutSchedule()).isEqualTo(1);
        assertThat(counts.arrivalsConsidered()).isZero();
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("the stop decides the match, even when the two sources number it differently")
    void matchesOnTheStopRatherThanTheSequenceNumber() {
        // Taken from trip 4659595 on 9 October, where the feed reported stop 8885 at
        // sequence 6 and the zip lists it at 5, because the feed's trip contains a stop
        // the published zip does not. Matching on the number would have compared this
        // arrival against stop 7247's 08:10, making a punctual bus 5 minutes early and
        // pushing it outside the window; matching on the stop compares it against its
        // own 08:05.
        stopTime(IN_FORCE, "4659595", 5, "8885", "08:05:00");
        stopTime(IN_FORCE, "4659595", 6, "7247", "08:10:00");
        arrival("4659595", 6, "8885", "2026-10-14T11:07:30Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.matched()).isEqualTo(1);
        assertThat(counts.sequenceDisagreements()).isEqualTo(1);
        assertThat(onlyRow()).containsEntry("scheduled_time_text", "08:05:00")
                .containsEntry("scheduled_stop_sequence", 5)
                .containsEntry("stop_sequence", 6)
                .containsEntry("delay_seconds", 150)
                .containsEntry("on_time", true);
    }

    @Test
    @DisplayName("a stop served twice in one trip falls back to the nearest sequence")
    void breaksATieWithTheSequenceAndSaysSo() {
        // A terminus served at both ends of the trip. The stop id alone matches two
        // published times 40 minutes apart, so the feed's number — unreliable, but the
        // only evidence there is — picks the nearer one, and the row is marked as
        // resting on it.
        stopTime(IN_FORCE, "4659595", 20, "8184", "08:45:00");
        arrival("4659595", 19, "8184", "2026-10-14T11:46:30Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.ambiguousStopMatches()).isEqualTo(1);
        assertThat(onlyRow()).containsEntry("scheduled_time_text", "08:45:00")
                .containsEntry("scheduled_stop_sequence", 20)
                .containsEntry("stop_match_ambiguous", true)
                .containsEntry("delay_seconds", 90);
    }

    @Test
    @DisplayName("a stop the published trip never calls at is unmatched, not nearest-matched")
    void doesNotMatchAStopThatIsNotOnTheTrip() {
        arrival("4659595", 2, "7777", "2026-10-14T11:07:30Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.unmatched()).isEqualTo(1);
        assertThat(counts.matched()).isZero();
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("the arrival's confidence travels into the on-time row")
    void carriesArrivalConfidenceThrough() {
        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "SPARSE_TRAIL");

        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        // An on-time verdict about a blurred arrival is itself blurred. Losing this
        // here would let the strongest claim on the site rest on the weakest rows
        // without saying so.
        assertThat(onlyRow()).containsEntry("arrival_confidence", "SPARSE_TRAIL");
    }

    @Test
    @DisplayName("a delay beyond half a day is counted and not published")
    void refusesAnImplausibleDelay() throws Exception {
        // The real shape of this: a trip published at 24:00:00 whose arrival was
        // observed a minute before midnight the evening before, so the service date and
        // the published time disagree by a day. Three such rows survived the
        // service-day repair in the published week. A bus is never twenty-four hours
        // early, and storing one would poison every average computed over it.
        stopTime(IN_FORCE, "4659595", 7, "8191", "24:00:00");
        arrival("4659595", 7, "8191", "2026-10-14T02:58:48Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.implausibleDelay()).isEqualTo(1);
        assertThat(counts.matched()).isZero();
        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("what the run refused is recorded, not just logged")
    void recordsWhatItRefused() {
        stopTime(IN_FORCE, "4659595", 7, "8191", "24:00:00");
        arrival("4659595", 7, "8191", "2026-10-14T02:58:48Z", "HIGH");  // implausible
        arrival("9999999", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");  // unmatched
        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");  // matched

        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        // A guard whose refusals live only in a log line cannot be published, and a
        // guard refusing three looks like one refusing nine thousand.
        Map<String, Object> quality = jdbcClient.sql("""
                        select feed_version, arrivals_considered, matched, unmatched,
                               implausible_delays_refused
                        from ontime_quality where service_date = :date
                        """).param("date", SERVICE_DATE).query().singleRow();

        assertThat(quality).containsEntry("feed_version", IN_FORCE)
                .containsEntry("arrivals_considered", 3)
                .containsEntry("matched", 1)
                .containsEntry("unmatched", 1)
                .containsEntry("implausible_delays_refused", 1);
    }

    @Test
    @DisplayName("re-running rewrites the record rather than accumulating rows")
    void rerunningRewritesTheQualityRecord() {
        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");

        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);
        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(jdbcClient.sql("select count(*) from ontime_quality")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("re-running a range leaves exactly what one run would have left")
    void rerunningIsIdempotent() {
        arrival("4659595", 1, "8184", "2026-10-14T11:02:30Z", "HIGH");
        arrival("4659595", 2, "8185", "2026-10-14T11:16:40Z", "HIGH");

        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);
        List<Map<String, Object>> firstRun = allRows();

        ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(allRows()).isEqualTo(firstRun);
    }

    @Test
    @DisplayName("widening the window moves rows from late to on time on a re-run")
    void aWindowChangeIsARerunNotARewrite() {
        arrival("4659595", 1, "8184", "2026-10-14T11:06:40Z", "HIGH");

        OntimeCounts strict = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);
        assertThat(strict.late()).isEqualTo(1);

        // The same arrival under a ten-minute window. The old verdict must be gone
        // rather than sitting alongside the new one: two windows' output in one table
        // is indistinguishable from a data error.
        OntimeRun lenient = new OntimeRun(
                new io.github.dremo_drizzy.bustruth.processor.store.OntimeEventRepository(jdbcClient),
                new io.github.dremo_drizzy.bustruth.processor.detect.DetectionSettings(
                        io.github.dremo_drizzy.bustruth.processor.detect.DetectionSettings
                                .ArrivalMoment.FIRST_SIGHTING,
                        java.time.Duration.ofSeconds(-60), java.time.Duration.ofSeconds(600),
                        java.time.Duration.ofSeconds(120)));

        OntimeCounts relaxed = lenient.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(relaxed.late()).isZero();
        assertThat(relaxed.onTime()).isEqualTo(1);
        assertThat(rowCount()).isEqualTo(1);
        assertThat(onlyRow()).containsEntry("on_time", true);
    }

    @Test
    @DisplayName("a trip past midnight is compared against the service day it belongs to")
    void comparesAPastMidnightTripAgainstItsOwnServiceDay() {
        stopTime(IN_FORCE, "4659595", 4, "8187", "24:40:00");
        // 00:41:00 local on the 15th, which is 03:41 UTC, against 24:40:00 on the 14th.
        arrival("4659595", 4, "8187", "2026-10-15T03:41:00Z", "HIGH");

        OntimeCounts counts = ontimeRun.run(SERVICE_DATE, SERVICE_DATE);

        assertThat(counts.matched()).isEqualTo(1);
        assertThat(onlyRow()).containsEntry("delay_seconds", 60)
                .containsEntry("scheduled_time_text", "24:40:00");
    }

    private void feedVersion(String version, String startDate, String endDate,
                             OffsetDateTime loadedAt) {
        jdbcClient.sql("""
                        insert into gtfs_feed_versions
                            (feed_version, loaded_at, source_sha256, source_bytes)
                        values (:version, :loadedAt, 'test', 0)
                        """)
                .param("version", version).param("loadedAt", loadedAt).update();
        jdbcClient.sql("""
                        insert into gtfs_calendar
                            (feed_version, service_id, start_date, end_date)
                        values (:version, 'weekday', :startDate, :endDate)
                        """)
                .param("version", version).param("startDate", startDate)
                .param("endDate", endDate).update();
    }

    private void trip(String version, String tripId, String routeId) {
        jdbcClient.sql("""
                        insert into gtfs_trips (feed_version, trip_id, route_id, service_id)
                        values (:version, :tripId, :routeId, 'weekday')
                        """)
                .param("version", version).param("tripId", tripId)
                .param("routeId", routeId).update();
    }

    private void stopTime(String version, String tripId, int sequence, String stopId,
                          String arrivalTime) {
        jdbcClient.sql("""
                        insert into gtfs_stop_times
                            (feed_version, trip_id, stop_sequence, stop_id,
                             arrival_time, departure_time)
                        values (:version, :tripId, :sequence, :stopId, :time, :time)
                        """)
                .param("version", version).param("tripId", tripId)
                .param("sequence", sequence).param("stopId", stopId)
                .param("time", arrivalTime).update();
    }

    private void arrival(String tripId, int sequence, String stopId, String observedAt,
                         String confidence) {
        jdbcClient.sql("""
                        insert into arrival_events
                            (trip_id, stop_id, stop_sequence, service_date,
                             observed_arrival_at, method, confidence)
                        values (:tripId, :stopId, :sequence, :date, :at,
                             'POSITION_SEQUENCE', :confidence)
                        """)
                .param("tripId", tripId).param("stopId", stopId).param("sequence", sequence)
                .param("date", SERVICE_DATE)
                .param("at", Instant.parse(observedAt).atOffset(ZoneOffset.UTC))
                .param("confidence", confidence)
                .update();
    }

    private long rowCount() {
        return jdbcClient.sql("select count(*) from ontime_events").query(Long.class).single();
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = allRows();
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private List<Map<String, Object>> allRows() {
        return jdbcClient.sql("""
                select trip_id, stop_id, stop_sequence, service_date, feed_version, route_id,
                       observed_arrival_at, scheduled_arrival_at, scheduled_time_text,
                       delay_seconds, on_time, arrival_confidence,
                       scheduled_stop_sequence, stop_match_ambiguous
                from ontime_events
                order by trip_id, stop_sequence
                """).query().listOfRows();
    }
}
