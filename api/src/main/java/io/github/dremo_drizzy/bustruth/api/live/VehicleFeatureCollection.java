package io.github.dremo_drizzy.bustruth.api.live;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * A GeoJSON FeatureCollection of live vehicles, with freshness alongside the features.
 *
 * <p>GeoJSON rather than a bespoke shape because MapLibre consumes it natively as a
 * source: the browser adds the response to the map without transforming it, so a
 * translation layer that could disagree with the server simply does not exist.
 *
 * <p>The freshness members sit at the top level so the page never has to scan features
 * to work out whether it is looking at live data. That matters because the three
 * states it must distinguish are not all visible in the features: an empty collection
 * means "no buses running" when {@code newestPositionAt} is recent, and "the pipeline
 * is down" when it is not.
 */
public record VehicleFeatureCollection(
        String type,
        @JsonProperty("generated_at") Instant generatedAt,
        @JsonProperty("newest_position_at") Instant newestPositionAt,
        @JsonProperty("vehicle_count") int vehicleCount,
        List<Feature> features) {

    public static VehicleFeatureCollection of(List<Feature> features, Instant newestPositionAt) {
        return new VehicleFeatureCollection(
                "FeatureCollection", Instant.now(), newestPositionAt, features.size(), features);
    }

    /** One vehicle. */
    public record Feature(String type, Point geometry, Properties properties) {

        public static Feature of(LivePosition position) {
            return new Feature("Feature", Point.of(position), Properties.of(position));
        }
    }

    /**
     * GeoJSON coordinates are [longitude, latitude] — in that order, which is the
     * reverse of how they are usually spoken. Getting it backwards puts Halifax's
     * buses in Somalia, and the map renders happily either way.
     */
    public record Point(String type, List<Float> coordinates) {

        public static Point of(LivePosition position) {
            return new Point("Point", List.of(position.longitude(), position.latitude()));
        }
    }

    /**
     * What the map draws with. {@code vehicle_timestamp} is per feature because each
     * dot's opacity is driven by its own age: five of 119 vehicles were around ten
     * minutes stale in a live check, and a bus that stopped reporting nine minutes ago
     * must not look identical to one reporting now.
     */
    public record Properties(
            @JsonProperty("vehicle_id") String vehicleId,
            @JsonProperty("route_id") String routeId,
            @JsonProperty("trip_id") String tripId,
            Float bearing,
            @JsonProperty("vehicle_timestamp") Instant vehicleTimestamp) {

        public static Properties of(LivePosition position) {
            return new Properties(position.vehicleId(), position.routeId(), position.tripId(),
                    position.bearing(), position.vehicleTimestamp());
        }
    }
}
