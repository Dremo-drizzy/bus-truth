package io.github.dremo_drizzy.bustruth.processor;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real vehicle trails, captured from the archive by scripts/make_detector_fixtures.py.
 *
 * <p>Real rather than invented, because the shapes that break a detector are the ones
 * nobody would think to write: a sequence jumping by fifteen, a trail that stops
 * mid-trip, a trip id with a suffix. Inventing fixtures tests the author's imagination
 * rather than the agency's data.
 */
public final class Trails {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Trails() {
    }

    public static List<VehiclePosition> load(String name) throws IOException {
        try (InputStream in = Trails.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture /fixtures/" + name);
            }
            JsonNode array = MAPPER.readTree(in);
            return array.valueStream().map(Trails::toPosition).toList();
        }
    }

    private static VehiclePosition toPosition(JsonNode node) {
        return new VehiclePosition(
                text(node, "vehicleId"),
                text(node, "vehicleLabel"),
                text(node, "tripId"),
                text(node, "routeId"),
                integer(node, "directionId"),
                text(node, "startDate"),
                real(node, "latitude"),
                real(node, "longitude"),
                real(node, "bearing"),
                real(node, "speed"),
                node.path("odometer").isNumber() ? node.path("odometer").asDouble() : null,
                integer(node, "currentStopSequence"),
                text(node, "stopId"),
                text(node, "currentStatus"),
                text(node, "occupancyStatus"),
                integer(node, "occupancyPercentage"),
                epochSeconds(node, "timestamp"),
                epochSeconds(node, "feedTimestamp"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static Float real(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? (float) value.asDouble() : null;
    }

    private static Instant epochSeconds(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? Instant.ofEpochSecond(value.asLong()) : null;
    }

    /** A copy of a position with one field replaced, for building the awkward cases. */
    public static VehiclePosition withSequenceAndTime(VehiclePosition base, Integer sequence,
                                                      String stopId, Instant at) {
        return new VehiclePosition(
                base.vehicleId(), base.vehicleLabel(), base.tripId(), base.routeId(),
                base.directionId(), base.startDate(), base.latitude(), base.longitude(),
                base.bearing(), base.speed(), base.odometer(), sequence, stopId,
                base.currentStatus(), base.occupancyStatus(), base.occupancyPercentage(),
                at, base.feedTimestamp());
    }

    public static VehiclePosition withTripId(VehiclePosition base, String tripId) {
        return new VehiclePosition(
                base.vehicleId(), base.vehicleLabel(), tripId, base.routeId(),
                base.directionId(), base.startDate(), base.latitude(), base.longitude(),
                base.bearing(), base.speed(), base.odometer(), base.currentStopSequence(),
                base.stopId(), base.currentStatus(), base.occupancyStatus(),
                base.occupancyPercentage(), base.timestamp(), base.feedTimestamp());
    }
}
