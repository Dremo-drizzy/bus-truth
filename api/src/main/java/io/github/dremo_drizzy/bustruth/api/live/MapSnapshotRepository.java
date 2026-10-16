package io.github.dremo_drizzy.bustruth.api.live;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the one curated minute the map draws when there is no live feed.
 *
 * <p>The minute is <b>17:08 local on Tuesday 6 October 2026</b>, chosen by
 * {@code scripts/pick_map_snapshot.sql}: 290 distinct vehicles, more than any other
 * minute of any 17:00 hour in the published week. 17:00 is the network's worst hour
 * (45.3% on time against 79.4% at 06:00) and 6 October its worst day (48.7%), so the
 * map shows Halifax Transit at its least punctual hour of its least punctual day —
 * which is what the findings are about — and, being the fullest minute available, also
 * the busiest it will ever look.
 *
 * <p>The table holds one row per vehicle and the same {@code snapshot_at} on every
 * row, so the moment the page names comes from the data rather than from a constant
 * somebody has to keep in step.
 */
@Repository
public class MapSnapshotRepository {

    private static final String SNAPSHOT = """
            select snapshot_at, vehicle_id, route_id, trip_id,
                   latitude, longitude, bearing, vehicle_timestamp
            from map_snapshot_positions
            order by vehicle_id
            """;

    private final JdbcClient jdbcClient;

    public MapSnapshotRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<LivePosition> positions() {
        return jdbcClient.sql(SNAPSHOT)
                .query((rs, rowNum) -> new LivePosition(
                        rs.getString("vehicle_id"),
                        rs.getString("route_id"),
                        rs.getString("trip_id"),
                        rs.getObject("latitude", Float.class),
                        rs.getObject("longitude", Float.class),
                        rs.getObject("bearing", Float.class),
                        rs.getObject("vehicle_timestamp", OffsetDateTime.class).toInstant()))
                .list();
    }

    /**
     * The minute the loaded rows belong to, or empty when none are loaded.
     *
     * <p>Read from the rows rather than configured, so the label on the page cannot
     * disagree with the dots on it.
     */
    public Optional<Instant> snapshotAt() {
        return jdbcClient.sql("select max(snapshot_at) from map_snapshot_positions")
                .query(OffsetDateTime.class)
                .optional()
                .map(OffsetDateTime::toInstant);
    }
}
