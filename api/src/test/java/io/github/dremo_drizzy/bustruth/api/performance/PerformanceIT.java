package io.github.dremo_drizzy.bustruth.api.performance;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.dremo_drizzy.bustruth.api.ApiTestcontainers;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The five performance endpoints against a real PostgreSQL 18.
 *
 * <p>Every one of them is a SQL statement using `filter`, `percentile_disc`,
 * `distinct on`, a lateral `full join` or `at time zone` — all Postgres behaviour, none
 * of it meaningfully testable against a stub or another engine.
 *
 * <p>Beyond each endpoint's happy path, three cases get their own tests because they
 * are where an honest API differs from a flattering one: an empty date range, a route
 * too thinly observed to rank, and a known route that was never seen.
 */
@SpringBootTest(properties = {
        "POSTGRES_PASSWORD=unused",
        "spring.flyway.enabled=false"
})
@Import(ApiTestcontainers.class)
class PerformanceIT {

    private static final LocalDate DAY_ONE = LocalDate.of(2026, 10, 9);
    private static final LocalDate DAY_TWO = LocalDate.of(2026, 10, 10);
    private static final String FEED = "2026_08_August_20260806";

    /** The service day is the agency's, and the by-hour buckets are local. */
    private static final ZoneId HALIFAX = ZoneId.of("America/Halifax");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcClient jdbcClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // The collector owns this schema; the api only reads it. Kept to the columns
        // the queries touch, so a change there fails this test rather than passing.
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
                create table if not exists arrival_events (
                    trip_id text not null, stop_id text not null,
                    stop_sequence integer not null, service_date date not null,
                    observed_arrival_at timestamptz not null,
                    method text not null, confidence text not null,
                    primary key (trip_id, service_date, stop_sequence))
                """).update();
        jdbcClient.sql("""
                create table if not exists raw_vehicle_positions (
                    vehicle_id text not null, vehicle_timestamp timestamptz not null,
                    first_feed_timestamp timestamptz not null,
                    primary key (vehicle_id, vehicle_timestamp))
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_feed_versions (
                    feed_version text primary key,
                    loaded_at timestamptz not null default clock_timestamp(),
                    source_sha256 text not null, source_bytes bigint not null)
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_routes (
                    feed_version text not null, route_id text not null,
                    route_short_name text, route_long_name text, route_type integer,
                    primary key (feed_version, route_id))
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_stops (
                    feed_version text not null, stop_id text not null,
                    stop_code text, stop_name text,
                    stop_lat double precision, stop_lon double precision,
                    primary key (feed_version, stop_id))
                """).update();

        jdbcClient.sql("truncate table ontime_events, arrival_events, raw_vehicle_positions, "
                + "gtfs_routes, gtfs_stops, gtfs_feed_versions").update();

        jdbcClient.sql("""
                insert into gtfs_feed_versions (feed_version, loaded_at, source_sha256, source_bytes)
                values (:feed, '2026-08-06T00:00Z', 'test', 0)
                """).param("feed", FEED).update();
        route("1", "1", "Spring Garden");
        route("10", "10", "Dalhousie");
        route("2", "2", "Fairview");
        route("3", "3", "CrossTown");
        stop("8184", "Barrington St");
        stop("8185", "Spring Garden Rd");
        stop("8186", "Quinpool Rd");

        // Route 1: ten arrivals on day one, seven of them on time, and a spread of
        // delays wide enough for the median and p90 to differ.
        for (int i = 0; i < 7; i++) {
            ontime("1", "trip-1-" + i, "8184", 1, DAY_ONE, 60, true, 9);
        }
        ontime("1", "trip-1-7", "8185", 2, DAY_ONE, 420, false, 17);
        ontime("1", "trip-1-8", "8185", 2, DAY_ONE, 600, false, 17);
        ontime("1", "trip-1-9", "8186", 3, DAY_ONE, 900, false, 17);
        // And four on day two, all late, so by_day has something to show a difference.
        for (int i = 0; i < 4; i++) {
            ontime("1", "trip-1-d2-" + i, "8186", 3, DAY_TWO, 500, false, 8);
        }

        // Route 10: a hundred arrivals, ninety on time — the leaderboard's top entry.
        for (int i = 0; i < 100; i++) {
            ontime("10", "trip-10-" + i, "8185", 1, DAY_ONE, i < 90 ? 30 : 480, i < 90, 12);
        }

        // Route 2: observed exactly twice. Both late, which would rank it worst of all
        // on percentage alone.
        ontime("2", "trip-2-0", "8186", 1, DAY_ONE, 700, false, 15);
        ontime("2", "trip-2-1", "8186", 2, DAY_ONE, 800, false, 15);

        // Route 3: seen once, and on time. On share alone this is a perfect route and
        // tops the leaderboard above route 10's ninety-of-a-hundred. This is the real
        // shape of the live data — on 9 October the top three routes by share had
        // eleven, four and two arrivals between them.
        ontime("3", "trip-3-0", "8184", 1, DAY_ONE, 20, true, 11);

        // Collection record: two days, one of them thin.
        snapshots(DAY_ONE, 300);
        snapshots(DAY_TWO, 50);
        derivedArrival("trip-1-0", DAY_ONE);
        derivedArrival("trip-1-1", DAY_ONE);
    }

    @Test
    @DisplayName("GET /api/routes lists the schedule's routes, numerically ordered")
    void routes() throws Exception {
        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                // 2 before 10: the short names are numbers held as text, and a plain
                // text sort would put 10 first.
                .andExpect(jsonPath("$[0].route_id").value("1"))
                .andExpect(jsonPath("$[1].route_id").value("2"))
                .andExpect(jsonPath("$[2].route_id").value("3"))
                .andExpect(jsonPath("$[3].route_id").value("10"))
                .andExpect(jsonPath("$[3].route_long_name").value("Dalhousie"));
    }

    @Test
    @DisplayName("GET /api/routes/performance ranks routes and carries every denominator")
    void leaderboard() throws Exception {
        mockMvc.perform(get("/api/routes/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                // Route 10 first at 90%, then route 1 at 50% of 14, then route 2 at 0%.
                .andExpect(jsonPath("$[0].route_id").value("10"))
                .andExpect(jsonPath("$[0].on_time.count").value(90))
                .andExpect(jsonPath("$[0].on_time.of").value(100))
                .andExpect(jsonPath("$[0].on_time.percent").value(90.0))
                .andExpect(jsonPath("$[0].trips_observed").value(100))
                // Only route 10 clears the floor of twenty, so the others sort below it
                // by share: route 3 at 100% of one, route 1 at 50% of fourteen, route 2
                // at 0% of two.
                .andExpect(jsonPath("$[2].route_id").value("1"))
                .andExpect(jsonPath("$[2].on_time.count").value(7))
                .andExpect(jsonPath("$[2].on_time.of").value(14))
                .andExpect(jsonPath("$[2].on_time.percent").value(50.0))
                .andExpect(jsonPath("$[3].route_id").value("2"))
                .andExpect(jsonPath("$[3].on_time.percent").value(0.0))
                // Two observations, and the response says so rather than only "0%".
                .andExpect(jsonPath("$[3].on_time.of").value(2));
    }

    @Test
    @DisplayName("a route seen once cannot top the leaderboard, but is still returned")
    void aThinlyObservedRouteCannotOutrankAWellObservedOne() throws Exception {
        // Route 3 is 100% on time of one arrival. On share alone it ranks above route
        // 10's 90% of a hundred, which would make the leaderboard a ranking of how
        // little was observed. It sorts below both rankable routes instead — and is
        // still present, because a route observed once is a fact about our coverage.
        mockMvc.perform(get("/api/routes/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].route_id").value("10"))
                .andExpect(jsonPath("$[0].rankable").value(true))
                // Route 1 has fourteen arrivals, so at the measured floor of twenty it
                // cannot hold a position either: one bus would move it seven points.
                .andExpect(jsonPath("$[1].route_id").value("3"))
                .andExpect(jsonPath("$[1].rankable").value(false))
                .andExpect(jsonPath("$[2].route_id").value("1"))
                .andExpect(jsonPath("$[1].on_time.percent").value(100.0))
                .andExpect(jsonPath("$[1].on_time.of").value(1))
                .andExpect(jsonPath("$[2].rankable").value(false))
                .andExpect(jsonPath("$[3].route_id").value("2"))
                .andExpect(jsonPath("$[3].rankable").value(false));
    }

    @Test
    @DisplayName("lowering the leaderboard floor lets the thin route rank")
    void theLeaderboardThresholdIsOverridable() throws Exception {
        mockMvc.perform(get("/api/routes/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString())
                        .param("minArrivals", "1"))
                .andExpect(status().isOk())
                // At a threshold of one, every route is rankable and share alone decides
                // — which is exactly the misleading order, available on request.
                .andExpect(jsonPath("$[0].route_id").value("3"))
                .andExpect(jsonPath("$[0].rankable").value(true));
    }

    @Test
    @DisplayName("the leaderboard reports median and p90 delay, not just a share")
    void leaderboardReportsDelayPercentiles() throws Exception {
        // Route 1's fourteen delays: 60 seven times, then 420, 500 four times, 600, 900.
        // percentile_disc returns a delay some bus actually had — the 7th of 14 for the
        // median, the 13th for p90 — rather than interpolating a number no passenger
        // experienced, which is why these are 60 and 600 and not an average of pairs.
        mockMvc.perform(get("/api/routes/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].route_id").value("1"))
                .andExpect(jsonPath("$[2].median_delay_seconds").value(60))
                .andExpect(jsonPath("$[2].p90_delay_seconds").value(600));
    }

    @Test
    @DisplayName("GET /api/routes/{id}/performance breaks a route down by hour and by day")
    void routeDetail() throws Exception {
        mockMvc.perform(get("/api/routes/1/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route_short_name").value("1"))
                .andExpect(jsonPath("$.arrivals").value(14))
                .andExpect(jsonPath("$.on_time.percent").value(50.0))
                // Hours are local: the 09:00 and 17:00 arrivals were written as 12:00
                // and 20:00 UTC. In UTC these buckets would be three hours out and the
                // 20:00 one would fall on the wrong day.
                .andExpect(jsonPath("$.by_hour[0].hour").value(8))
                .andExpect(jsonPath("$.by_hour[1].hour").value(9))
                .andExpect(jsonPath("$.by_hour[1].on_time.count").value(7))
                .andExpect(jsonPath("$.by_day[0].service_date").value(DAY_ONE.toString()))
                .andExpect(jsonPath("$.by_day[0].on_time.count").value(7))
                .andExpect(jsonPath("$.by_day[1].service_date").value(DAY_TWO.toString()))
                .andExpect(jsonPath("$.by_day[1].on_time.count").value(0))
                .andExpect(jsonPath("$.by_day[1].arrivals").value(4));
    }

    @Test
    @DisplayName("the default floor is the measured one, and it holds stops back")
    void worstStopsAppliesTheMeasuredFloor() throws Exception {
        // Route 1's stops have seven, five and two arrivals, so none clears the measured
        // floor of twenty. The response returns an empty ranking and says how many stops
        // it held back, rather than ranking three stops on a handful of observations and
        // calling one of them the route's worst.
        mockMvc.perform(get("/api/routes/1/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.min_arrivals_applied").value(20))
                .andExpect(jsonPath("$.stops_below_threshold").value(3))
                .andExpect(jsonPath("$.worst_stops.length()").value(0));
    }

    @Test
    @DisplayName("the floor is overridable, and the response states the one it used")
    void theThresholdIsOverridable() throws Exception {
        // At five, 8185's two arrivals are still held back; at one, everything ranks.
        mockMvc.perform(get("/api/routes/1/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString())
                        .param("minArrivals", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.min_arrivals_applied").value(5))
                .andExpect(jsonPath("$.stops_below_threshold").value(1))
                .andExpect(jsonPath("$.worst_stops.length()").value(2))
                // 8186: five arrivals, none on time. Worst of the rankable stops.
                .andExpect(jsonPath("$.worst_stops[0].stop_id").value("8186"))
                .andExpect(jsonPath("$.worst_stops[0].stop_name").value("Quinpool Rd"))
                .andExpect(jsonPath("$.worst_stops[0].on_time.of").value(5))
                .andExpect(jsonPath("$.worst_stops[1].stop_id").value("8184"));

        mockMvc.perform(get("/api/routes/1/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString())
                        .param("minArrivals", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.min_arrivals_applied").value(1))
                .andExpect(jsonPath("$.stops_below_threshold").value(0))
                .andExpect(jsonPath("$.worst_stops.length()").value(3));
    }

    @Test
    @DisplayName("GET /api/stops/{id}/reliability breaks a stop down by route")
    void stopReliability() throws Exception {
        mockMvc.perform(get("/api/stops/8185/reliability")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stop_name").value("Spring Garden Rd"))
                // 100 arrivals from route 10 and 2 from route 1.
                .andExpect(jsonPath("$.arrivals").value(102))
                .andExpect(jsonPath("$.on_time.count").value(90))
                .andExpect(jsonPath("$.by_route[0].route_id").value("10"))
                .andExpect(jsonPath("$.by_route[0].on_time.of").value(100))
                .andExpect(jsonPath("$.by_route[1].route_id").value("1"))
                .andExpect(jsonPath("$.by_route[1].on_time.count").value(0))
                .andExpect(jsonPath("$.by_route[1].on_time.of").value(2));
    }

    @Test
    @DisplayName("GET /api/coverage publishes the project's own gaps")
    void coverage() throws Exception {
        mockMvc.perform(get("/api/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].service_date").value(DAY_ONE.toString()))
                .andExpect(jsonPath("$[0].snapshots_captured").value(300))
                // 4,320 snapshots is a full day at one every 20 seconds.
                .andExpect(jsonPath("$[0].snapshots_expected").value(4320))
                .andExpect(jsonPath("$[0].coverage.percent").value(6.9))
                .andExpect(jsonPath("$[0].arrivals_derived").value(2))
                .andExpect(jsonPath("$[1].snapshots_captured").value(50))
                // A day with snapshots but no derived arrivals is visible as such: the
                // detector has not been run over it, which is a different failure from
                // no buses running.
                .andExpect(jsonPath("$[1].arrivals_derived").value(0));
    }

    @Test
    @DisplayName("an empty date range answers with zeros and a null percentage, not 0%")
    void emptyRange() throws Exception {
        LocalDate quiet = LocalDate.of(2026, 1, 1);

        mockMvc.perform(get("/api/routes/performance")
                        .param("from", quiet.toString()).param("to", quiet.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/routes/1/performance")
                        .param("from", quiet.toString()).param("to", quiet.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivals").value(0))
                // Null, not zero. 0% on time is a claim about the service; null says
                // the question has no answer for this range, and a page must be able to
                // tell those apart.
                .andExpect(jsonPath("$.on_time.percent").value(nullValue()))
                .andExpect(jsonPath("$.median_delay_seconds").value(nullValue()))
                .andExpect(jsonPath("$.by_hour.length()").value(0))
                .andExpect(jsonPath("$.worst_stops.length()").value(0));
    }

    @Test
    @DisplayName("a route in the schedule that was never observed is zeros, not a 404")
    void aRouteThatRanButWasNeverSeen() throws Exception {
        route("99", "99", "Never observed");

        // "No such route" and "this route exists and we recorded nothing" are different
        // facts, and the second is about our coverage, not about the route.
        mockMvc.perform(get("/api/routes/99/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route_long_name").value("Never observed"))
                .andExpect(jsonPath("$.arrivals").value(0))
                .andExpect(jsonPath("$.on_time.percent").value(nullValue()));
    }

    @Test
    @DisplayName("a route or stop the schedule has never heard of is 404")
    void unknownRouteOrStop() throws Exception {
        mockMvc.perform(get("/api/routes/does-not-exist/performance")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/stops/does-not-exist/reliability")
                        .param("from", DAY_ONE.toString()).param("to", DAY_TWO.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a reversed range is a 400, not a confident 'nothing was on time'")
    void reversedRange() throws Exception {
        mockMvc.perform(get("/api/routes/performance")
                        .param("from", DAY_TWO.toString()).param("to", DAY_ONE.toString()))
                .andExpect(status().isBadRequest());
    }

    private void route(String routeId, String shortName, String longName) {
        jdbcClient.sql("""
                        insert into gtfs_routes
                            (feed_version, route_id, route_short_name, route_long_name, route_type)
                        values (:feed, :id, :short, :long, 3)
                        on conflict do nothing
                        """)
                .param("feed", FEED).param("id", routeId)
                .param("short", shortName).param("long", longName).update();
    }

    private void stop(String stopId, String name) {
        jdbcClient.sql("""
                        insert into gtfs_stops (feed_version, stop_id, stop_name)
                        values (:feed, :id, :name)
                        on conflict do nothing
                        """)
                .param("feed", FEED).param("id", stopId).param("name", name).update();
    }

    /**
     * One on-time row. {@code localHour} is the hour in Halifax that the arrival should
     * land in, converted here so the by-hour test asserts local bucketing.
     */
    private void ontime(String routeId, String tripId, String stopId, int sequence,
                        LocalDate serviceDate, int delaySeconds, boolean onTime, int localHour) {
        Instant observed = serviceDate.atTime(localHour, 0)
                .atZone(HALIFAX).toInstant();
        jdbcClient.sql("""
                        insert into ontime_events
                            (trip_id, stop_id, stop_sequence, service_date, feed_version,
                             route_id, observed_arrival_at, scheduled_arrival_at,
                             scheduled_time_text, delay_seconds, on_time, arrival_confidence,
                             scheduled_stop_sequence, stop_match_ambiguous)
                        values (:trip, :stop, :sequence, :date, :feed, :route, :observed,
                             :scheduled, '08:00:00', :delay, :onTime, 'HIGH', :sequence, false)
                        """)
                .param("trip", tripId).param("stop", stopId).param("sequence", sequence)
                .param("date", serviceDate).param("feed", FEED).param("route", routeId)
                .param("observed", observed.atOffset(ZoneOffset.UTC))
                .param("scheduled", observed.minusSeconds(delaySeconds).atOffset(ZoneOffset.UTC))
                .param("delay", delaySeconds).param("onTime", onTime)
                .update();
    }

    private void derivedArrival(String tripId, LocalDate serviceDate) {
        jdbcClient.sql("""
                        insert into arrival_events
                            (trip_id, stop_id, stop_sequence, service_date,
                             observed_arrival_at, method, confidence)
                        values (:trip, '8184', 1, :date, :at, 'POSITION_SEQUENCE', 'HIGH')
                        """)
                .param("trip", tripId).param("date", serviceDate)
                .param("at", serviceDate.atTime(12, 0).atOffset(ZoneOffset.UTC))
                .update();
    }

    /** Distinct feed timestamps on a day, which is what coverage counts. */
    private void snapshots(LocalDate day, int count) {
        for (int i = 0; i < count; i++) {
            Instant at = day.atTime(12, 0).atZone(HALIFAX).toInstant()
                    .plusSeconds(i * 20L);
            jdbcClient.sql("""
                            insert into raw_vehicle_positions
                                (vehicle_id, vehicle_timestamp, first_feed_timestamp)
                            values (:vehicle, :at, :at)
                            on conflict do nothing
                            """)
                    .param("vehicle", "bus-" + day + "-" + i)
                    .param("at", at.atOffset(ZoneOffset.UTC))
                    .update();
        }
    }
}
