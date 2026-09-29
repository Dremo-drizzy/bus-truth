package io.github.dremo_drizzy.bustruth.loader.store;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Writes one vehicle position to raw_vehicle_positions, at most once.
 *
 * <p>Delivery is at-least-once: a rebalance or a crash between the insert and the
 * offset commit means the same message arrives again. {@code ON CONFLICT DO NOTHING}
 * on the natural key is what turns that redelivery into a no-op, so the loader never
 * has to ask whether it has seen a message before.
 *
 * <p>First write wins. A second message with the same
 * {@code (vehicle_id, vehicle_timestamp)} but different content is discarded, not
 * merged — an identical vehicle timestamp means a repeat of one observation, not new
 * information, and the .pb archive still holds every copy.
 */
@Component
public class VehiclePositionWriter {

    private static final String INSERT = """
            insert into raw_vehicle_positions (
                vehicle_id, vehicle_timestamp, vehicle_label, trip_id, route_id,
                direction_id, start_date, latitude, longitude, bearing, speed,
                odometer, current_stop_sequence, stop_id, current_status,
                occupancy_status, occupancy_percentage, first_feed_timestamp,
                kafka_partition, kafka_offset)
            values (
                :vehicleId, :vehicleTimestamp, :vehicleLabel, :tripId, :routeId,
                :directionId, :startDate, :latitude, :longitude, :bearing, :speed,
                :odometer, :currentStopSequence, :stopId, :currentStatus,
                :occupancyStatus, :occupancyPercentage, :firstFeedTimestamp,
                :kafkaPartition, :kafkaOffset)
            on conflict (vehicle_id, vehicle_timestamp) do nothing
            """;

    private final JdbcClient jdbcClient;

    public VehiclePositionWriter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * @return true when a row was written, false when the observation was already
     *         stored. The caller logs the ratio: a loader whose skip rate suddenly
     *         goes to 100% is being replayed, and one at 0% after a restart is not
     *         deduping at all.
     */
    public boolean insert(VehiclePosition position, Integer kafkaPartition, Long kafkaOffset) {
        int rowsWritten = jdbcClient.sql(INSERT)
                .param("vehicleId", position.vehicleId())
                .param("vehicleTimestamp", utc(position.timestamp()))
                .param("vehicleLabel", position.vehicleLabel())
                .param("tripId", position.tripId())
                .param("routeId", position.routeId())
                .param("directionId", position.directionId())
                .param("startDate", position.startDate())
                .param("latitude", position.latitude())
                .param("longitude", position.longitude())
                .param("bearing", position.bearing())
                .param("speed", position.speed())
                .param("odometer", position.odometer())
                .param("currentStopSequence", position.currentStopSequence())
                .param("stopId", position.stopId())
                .param("currentStatus", position.currentStatus())
                .param("occupancyStatus", position.occupancyStatus())
                .param("occupancyPercentage", position.occupancyPercentage())
                .param("firstFeedTimestamp", utc(position.feedTimestamp()))
                .param("kafkaPartition", kafkaPartition)
                .param("kafkaOffset", kafkaOffset)
                .update();
        return rowsWritten == 1;
    }

    /**
     * The driver takes an OffsetDateTime for timestamptz. Converting explicitly at
     * UTC keeps the stored instant identical to the one the agency reported and
     * independent of the JVM's default zone.
     */
    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
