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
 * minute of any 17:00 hour in the published week. The hour is the claim — 17:00 runs
 * 45.3% on time against 79.4% at 06:00, computed across every day at once, so it does
 * not depend on which days were watched. The map therefore shows Halifax Transit at
 * its least punctual hour, which is what the findings are about, and being the fullest
 * minute available it is also the busiest the map will ever look.
 *
 * <p><b>The day is deliberately not claimed.</b> 6 October did record the week's lowest
 * on-time share, but day-to-day comparison is confounded by uneven coverage — one day
 * of the week has no observed arrivals at all between 07:00 and 18:59, and each day's
 * share of the evening peak ranges from 0% to 29.8% — so this project publishes the
 * hourly pattern and not the daily one. Describing this minute as "the worst day" would
 * assert exactly the comparison the README refuses to make.
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
