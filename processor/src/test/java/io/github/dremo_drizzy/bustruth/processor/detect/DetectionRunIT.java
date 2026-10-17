package io.github.dremo_drizzy.bustruth.processor.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.github.dremo_drizzy.bustruth.processor.Trails;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
 * Re-running a date range must leave exactly what one run would have left.
 *
 * <p>That is the property the whole design rests on: the arrival rule will change, and
 * when it does the fix is to run the range again rather than patch rows. Tested
 * against a real PostgreSQL because delete-then-insert inside a transaction is SQL
 * behaviour, not Java behaviour.
 */
@SpringBootTest(properties = {
        "POSTGRES_PASSWORD=unused",
        "spring.flyway.enabled=false"
})
@Import(DetectionRunIT.Containers.class)
class DetectionRunIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired
    private DetectionRun detectionRun;

    @Autowired
    private JdbcClient jdbcClient;

    private LocalDate serviceDate;

    @BeforeEach
    void setUp() throws IOException {
        // The collector owns these tables; the test creates the columns it reads so a
        // schema change there fails this test rather than silently passing.
        jdbcClient.sql("""
                create table if not exists raw_vehicle_positions (
                    vehicle_id text not null,
                    vehicle_timestamp timestamptz not null,
                    vehicle_label text, trip_id text, route_id text,
                    direction_id smallint, start_date text,
                    latitude real, longitude real, bearing real, speed real,
                    odometer double precision, current_stop_sequence integer,
                    stop_id text, current_status text, occupancy_status text,
                    occupancy_percentage integer,
                    first_feed_timestamp timestamptz not null,
                    primary key (vehicle_id, vehicle_timestamp))
                """).update();
        jdbcClient.sql("""
                create table if not exists arrival_events (
                    trip_id text not null, stop_id text not null,
                    stop_sequence integer not null, service_date date not null,
                    observed_arrival_at timestamptz not null,
                    method text not null, confidence text not null,
                    detected_at timestamptz not null default clock_timestamp(),
                    primary key (trip_id, service_date, stop_sequence))
                """).update();
        jdbcClient.sql("truncate table raw_vehicle_positions").update();
        jdbcClient.sql("truncate table arrival_events").update();

        List<VehiclePosition> trail = Trails.load("ordinary-trail.json");
        serviceDate = LocalDate.parse(trail.getFirst().startDate(),
                java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        trail.forEach(this::insertPosition);
    }

    @Test
    void derivesArrivalsFromStoredPositions() {
        DetectionCounts counts = detectionRun.run(serviceDate, serviceDate);

        assertThat(counts.arrivalsDerived()).isPositive();
        assertThat(rowCount()).isEqualTo(counts.arrivalsDerived());
    }

    @Test
    void rerunningTheSameDateRangeProducesIdenticalRows() {
        detectionRun.run(serviceDate, serviceDate);
        long countAfterFirstRun = rowCount();
        List<Map<String, Object>> firstRun = allArrivals();

        detectionRun.run(serviceDate, serviceDate);

        assertThat(rowCount()).isEqualTo(countAfterFirstRun);
        // Content, not just count: a run that deleted and rewrote different rows would
        // keep the count identical while changing the data underneath it.
        assertThat(allArrivals()).isEqualTo(firstRun);
    }

    @Test
    void rerunningAfterARuleChangeLeavesNoRowsFromTheOldRule() {
        detectionRun.run(serviceDate, serviceDate);
        long underDefaultRule = rowCount();

        // A stricter gap rule derives fewer arrivals. The surplus from the old rule
        // must be gone, not left behind to mix two rules' output in one table.
        DetectionRun stricter = new DetectionRun(
                new io.github.dremo_drizzy.bustruth.processor.store.ArrivalEventRepository(jdbcClient),
                new DetectionSettings(DetectionSettings.ArrivalMoment.FIRST_SIGHTING,
                        java.time.Duration.ofSeconds(-60), java.time.Duration.ofSeconds(300),
                        java.time.Duration.ofSeconds(21)));
        stricter.run(serviceDate, serviceDate);

        assertThat(rowCount()).isLessThan(underDefaultRule);
    }

    private void insertPosition(VehiclePosition position) {
        jdbcClient.sql("""
                        insert into raw_vehicle_positions
                            (vehicle_id, vehicle_timestamp, trip_id, route_id, start_date,
                             current_stop_sequence, stop_id, first_feed_timestamp)
                        values (:vehicleId, :at, :tripId, :routeId, :startDate,
                             :sequence, :stopId, :at)
                        on conflict do nothing
                        """)
                .param("vehicleId", position.vehicleId())
                .param("at", position.timestamp().atOffset(ZoneOffset.UTC))
                .param("tripId", position.tripId())
                .param("routeId", position.routeId())
                .param("startDate", position.startDate())
                .param("sequence", position.currentStopSequence())
                .param("stopId", position.stopId())
                .update();
    }

    private long rowCount() {
        return jdbcClient.sql("select count(*) from arrival_events").query(Long.class).single();
    }

    private List<Map<String, Object>> allArrivals() {
        return jdbcClient.sql("""
                select trip_id, stop_id, stop_sequence, service_date, observed_arrival_at,
                       method, confidence
                from arrival_events
                order by stop_sequence
                """).query().listOfRows();
    }
}
