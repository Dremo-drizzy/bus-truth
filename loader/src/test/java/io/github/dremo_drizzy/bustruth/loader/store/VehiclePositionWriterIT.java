package io.github.dremo_drizzy.bustruth.loader.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.github.dremo_drizzy.bustruth.loader.Fixtures;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.github.dremo_drizzy.bustruth.loader.TestcontainersConfig;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The idempotency proof, against a real PostgreSQL 18 started by Testcontainers.
 *
 * <p>Not H2: {@code ON CONFLICT DO NOTHING} is Postgres syntax, so a test on another
 * engine would prove nothing about the statement that actually runs. Flyway applies
 * the real migration, so the constraint under test is the one shipped.
 *
 * <p>No broker. Idempotency is a property of the write, and the proof should not
 * depend on Kafka being up.
 */
@SpringBootTest(properties = {
        // The listener would otherwise try to reach a broker that is not part of
        // this test.
        "spring.kafka.listener.auto-startup=false",
        // application.yml deliberately leaves the password without a default;
        // @ServiceConnection overrides the datasource, but the placeholder still has
        // to resolve.
        "POSTGRES_PASSWORD=unused"
})
@Import(TestcontainersConfig.class)
class VehiclePositionWriterIT {

    @Autowired
    private VehiclePositionWriter writer;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void emptyTheTable() {
        jdbcClient.sql("truncate table raw_vehicle_positions").update();
    }

    @Test
    void storesOneRowPerDistinctObservation() throws IOException {
        List<VehiclePosition> positions = Fixtures.positions();
        long distinctObservations = positions.stream()
                .map(p -> p.vehicleId() + "|" + p.timestamp())
                .distinct()
                .count();

        positions.forEach(position -> writer.insert(position, 0, 1L));

        assertThat(rowCount()).isEqualTo(distinctObservations);
    }

    @Test
    void replayingTheSameMessagesInsertsNothingTheSecondTime() throws IOException {
        List<VehiclePosition> positions = Fixtures.positions();
        positions.forEach(position -> writer.insert(position, 0, 1L));

        long countAfterFirstPass = rowCount();
        OffsetDateTime newestInsertBefore = newestInsertedAt();

        // Exactly what a consumer-group reset, a rebalance or a crash before the
        // offset commit produces.
        positions.forEach(position -> writer.insert(position, 0, 2L));

        assertThat(rowCount()).isEqualTo(countAfterFirstPass);
        // A count alone would also pass if rows had been deleted and rewritten;
        // an unchanged newest inserted_at proves nothing was touched.
        assertThat(newestInsertedAt()).isEqualTo(newestInsertBefore);
    }

    @Test
    void insertReportsWhetherItStoredTheRow() throws IOException {
        VehiclePosition position = Fixtures.positions().getFirst();

        assertThat(writer.insert(position, 0, 1L)).isTrue();
        assertThat(writer.insert(position, 0, 2L)).isFalse();
    }

    @Test
    void firstWriteWinsWhenContentChangesUnderTheSameKey() throws IOException {
        VehiclePosition original = Fixtures.positions().getFirst();
        VehiclePosition contradicting = new VehiclePosition(
                original.vehicleId(), original.vehicleLabel(), original.tripId(),
                original.routeId(), original.directionId(), original.startDate(),
                original.latitude(), original.longitude(), original.bearing(),
                999.0f, original.odometer(), original.currentStopSequence(),
                original.stopId(), original.currentStatus(), original.occupancyStatus(),
                original.occupancyPercentage(), original.timestamp(), original.feedTimestamp());

        writer.insert(original, 0, 1L);
        writer.insert(contradicting, 0, 2L);

        // Documents what DO NOTHING discards: the same vehicle timestamp is a repeat
        // of one observation, so the later message is dropped rather than merged.
        Float storedSpeed = jdbcClient.sql("select speed from raw_vehicle_positions")
                .query(Float.class).single();
        assertThat(storedSpeed).isEqualTo(original.speed());
    }

    @Test
    void nullsSurviveTheRoundTripInsteadOfBecomingZero() throws IOException {
        VehiclePosition withNulls = Fixtures.positions().stream()
                .filter(p -> p.currentStatus() == null)
                .findFirst()
                .orElseThrow();

        writer.insert(withNulls, 0, 1L);

        String storedStatus = jdbcClient
                .sql("select current_status from raw_vehicle_positions where vehicle_id = :id")
                .param("id", withNulls.vehicleId())
                .query(String.class).optional().orElse(null);
        assertThat(storedStatus).isNull();
    }

    @Test
    void kafkaCoordinatesAreStoredSoARowCanBeTracedBack() throws IOException {
        VehiclePosition position = Fixtures.positions().getFirst();

        writer.insert(position, 2, 12345L);

        Long offset = jdbcClient.sql("select kafka_offset from raw_vehicle_positions")
                .query(Long.class).single();
        assertThat(offset).isEqualTo(12345L);
    }

    @Test
    void storedTimestampMatchesTheInstantTheAgencyReported() throws IOException {
        VehiclePosition position = Fixtures.positions().getFirst();

        writer.insert(position, 0, 1L);

        Instant stored = jdbcClient.sql("select vehicle_timestamp from raw_vehicle_positions")
                .query(OffsetDateTime.class).single().toInstant();
        assertThat(stored).isEqualTo(position.timestamp());
    }

    private long rowCount() {
        return jdbcClient.sql("select count(*) from raw_vehicle_positions").query(Long.class).single();
    }

    private OffsetDateTime newestInsertedAt() {
        return jdbcClient.sql("select max(inserted_at) from raw_vehicle_positions")
                .query(OffsetDateTime.class).single();
    }
}
