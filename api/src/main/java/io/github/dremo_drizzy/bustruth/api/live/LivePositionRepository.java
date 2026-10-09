package io.github.dremo_drizzy.bustruth.api.live;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the latest position per vehicle. */
@Repository
public class LivePositionRepository {

    /**
     * Daniel's query, unchanged.
     *
     * <p>{@code distinct on (vehicle_id)} with {@code order by vehicle_id,
     * vehicle_timestamp desc} is the Postgres idiom for "one row per group, the
     * newest": the ordering decides which row survives, so the two clauses have to
     * agree or the result is arbitrary.
     *
     * <p>The ten-minute predicate is not cosmetic. Without it this scans every
     * vehicle's entire history to find rows it then throws away, and that cost grows
     * with the archive rather than with the number of buses.
     */
    private static final String LATEST_PER_VEHICLE = """
            select distinct on (vehicle_id)
                   vehicle_id, route_id, trip_id, latitude, longitude, bearing, vehicle_timestamp
            from raw_vehicle_positions
            where vehicle_timestamp > now() - interval '10 minutes'
            order by vehicle_id, vehicle_timestamp desc
            """;

    /**
     * Deliberately NOT filtered by the ten-minute window.
     *
     * <p>This is what separates "no buses are running right now" from "the pipeline
     * has stopped". If the newest position were read from the windowed query, both
     * would return nothing and look identical.
     */
    private static final String NEWEST_POSITION = """
            select max(vehicle_timestamp) from raw_vehicle_positions
            """;

    private final JdbcClient jdbcClient;

    public LivePositionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<LivePosition> latestPerVehicle() {
        return jdbcClient.sql(LATEST_PER_VEHICLE)
                .query((rs, rowNum) -> new LivePosition(
                        rs.getString("vehicle_id"),
                        rs.getString("route_id"),
                        rs.getString("trip_id"),
                        // real / float4 in the database: asking for a Double throws
                        // "conversion to Double from float4 not supported".
                        rs.getObject("latitude", Float.class),
                        rs.getObject("longitude", Float.class),
                        rs.getObject("bearing", Float.class),
                        toInstant(rs.getObject("vehicle_timestamp", OffsetDateTime.class))))
                .list();
    }

    /** Empty only when the table itself is empty. */
    public Optional<Instant> newestPositionAt() {
        return jdbcClient.sql(NEWEST_POSITION)
                .query(OffsetDateTime.class)
                .optional()
                .map(LivePositionRepository::toInstant);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
