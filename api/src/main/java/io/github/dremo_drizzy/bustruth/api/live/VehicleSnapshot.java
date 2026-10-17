package io.github.dremo_drizzy.bustruth.api.live;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * One named minute of vehicle positions, as GeoJSON.
 *
 * <p>Deliberately not {@link VehicleFeatureCollection}. That shape carries
 * {@code generated_at} and {@code newest_position_at} so a page can work out whether it
 * is looking at live data; this one carries {@code snapshot_at}, which is the moment
 * being shown. Reusing the live shape would have meant filling freshness fields with a
 * historical time, and a map that reports a two-week-old position as the newest it has
 * is indistinguishable from a broken pipeline — the exact confusion the live endpoint's
 * three states exist to prevent.
 *
 * <p>So the two are separate types with separate endpoints, and the page that draws
 * this one says in words which minute it is.
 *
 * @param snapshotAt   the minute these positions are from, which is what the page
 *                     labels itself with
 * @param vehicleCount how many vehicles reported inside that minute
 */
public record VehicleSnapshot(
        String type,
        @JsonProperty("snapshot_at") Instant snapshotAt,
        @JsonProperty("vehicle_count") int vehicleCount,
        List<VehicleFeatureCollection.Feature> features) {

    public static VehicleSnapshot of(
            Instant snapshotAt, List<VehicleFeatureCollection.Feature> features) {
        return new VehicleSnapshot("FeatureCollection", snapshotAt, features.size(), features);
    }

    /** No curated minute loaded — the deployed site should not reach this. */
    public static VehicleSnapshot empty() {
        return new VehicleSnapshot("FeatureCollection", null, 0, List.of());
    }
}
