package io.github.dremo_drizzy.bustruth.processor.store;

import io.github.dremo_drizzy.bustruth.common.ArrivalEvent;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads positions for a date range and writes the arrivals derived from them. */
@Repository
public class ArrivalEventRepository {

    /**
     * Positions for one service day, grouped so a detector can walk each vehicle's
     * trail in order.
     *
     * <p>The window is widened by six hours on each side of the service date because
     * a service day is not a calendar day: a trip that starts at 23:50 finishes on the
     * next date, and one with start_date of yesterday can still be running at 02:00.
     * Filtering tightly on the calendar date would cut trails in half and
     * manufacture broken trails that are an artefact of the query, not the data.
     */
    private static final String POSITIONS_FOR_DAY = """
            select vehicle_id, vehicle_label, trip_id, route_id, direction_id, start_date,
                   latitude, longitude, bearing, speed, odometer, current_stop_sequence,
                   stop_id, current_status, occupancy_status, occupancy_percentage,
                   vehicle_timestamp, first_feed_timestamp
            from raw_vehicle_positions
            where vehicle_timestamp >= :from and vehicle_timestamp < :to
              and trip_id is not null and trip_id <> ''
            order by vehicle_id, trip_id, start_date, vehicle_timestamp
            """;

    private static final String DELETE_RANGE = """
            delete from arrival_events where service_date >= :from and service_date <= :to
            """;

    private static final String INSERT = """
            insert into arrival_events
                (trip_id, stop_id, stop_sequence, service_date, observed_arrival_at,
                 method, confidence)
            values (:tripId, :stopId, :stopSequence, :serviceDate, :observedArrivalAt,
                 :method, :confidence)
            on conflict (trip_id, service_date, stop_sequence) do nothing
            """;

    private final JdbcClient jdbcClient;

    public ArrivalEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<VehiclePosition> positionsForDay(LocalDate serviceDate) {
        return jdbcClient.sql(POSITIONS_FOR_DAY)
                .param("from", serviceDate.minusDays(1).atTime(18, 0).atOffset(ZoneOffset.UTC))
                .param("to", serviceDate.plusDays(1).atTime(12, 0).atOffset(ZoneOffset.UTC))
                .query((rs, rowNum) -> new VehiclePosition(
                        rs.getString("vehicle_id"),
                        rs.getString("vehicle_label"),
                        rs.getString("trip_id"),
                        rs.getString("route_id"),
                        rs.getObject("direction_id", Integer.class),
                        rs.getString("start_date"),
                        rs.getObject("latitude", Float.class),
                        rs.getObject("longitude", Float.class),
                        rs.getObject("bearing", Float.class),
                        rs.getObject("speed", Float.class),
                        rs.getObject("odometer", Double.class),
                        rs.getObject("current_stop_sequence", Integer.class),
                        rs.getString("stop_id"),
                        rs.getString("current_status"),
                        rs.getString("occupancy_status"),
                        rs.getObject("occupancy_percentage", Integer.class),
                        instant(rs.getObject("vehicle_timestamp", OffsetDateTime.class)),
                        instant(rs.getObject("first_feed_timestamp", OffsetDateTime.class))))
                .list();
    }

    /**
     * Clears a date range so it can be rebuilt.
     *
     * <p>Re-running must be safe, and "safe" here means the second run leaves exactly
     * what a first run would have: delete the range, then insert. Without the delete,
     * a rule change that produces fewer arrivals would leave the old surplus behind,
     * and the table would hold a mixture of two rules' output with no way to tell
     * which row came from which.
     */
    public int deleteRange(LocalDate from, LocalDate to) {
        return jdbcClient.sql(DELETE_RANGE).param("from", from).param("to", to).update();
    }

    public int insertAll(List<ArrivalEvent> arrivals) {
        int written = 0;
        for (ArrivalEvent arrival : arrivals) {
            written += jdbcClient.sql(INSERT)
                    .param("tripId", arrival.tripId())
                    .param("stopId", arrival.stopId())
                    .param("stopSequence", arrival.stopSequence())
                    .param("serviceDate", arrival.serviceDate())
                    .param("observedArrivalAt", arrival.observedArrivalAt().atOffset(ZoneOffset.UTC))
                    .param("method", arrival.method())
                    .param("confidence", arrival.confidence())
                    .update();
        }
        return written;
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
