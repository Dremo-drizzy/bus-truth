package io.github.dremo_drizzy.bustruth.api.live;

import java.time.Instant;

/**
 * One vehicle's most recent reported position, as the live query returns it.
 *
 * <p>Every field except the id may be null: the agency omits bearing on some
 * vehicles, and a bus not currently on a trip has no trip or route.
 *
 * @param vehicleId        the agency's id for the bus
 * @param routeId          the route it is running
 * @param tripId           the trip, exactly as the feed gives it — including the _N
 *                         suffix of a modified trip. The suffix is not interpreted
 *                         here: what it means for schedule matching is a later
 *                         question, and a map cannot answer it
 * @param latitude         degrees north. Float because the column is real (float4):
 *                         the feed sends 32-bit floats, and widening to Double turns
 *                         44.6492 into 44.64920043945312 in the JSON
 * @param longitude        degrees east
 * @param bearing          degrees clockwise from true north, when reported
 * @param vehicleTimestamp when the bus reported this position; drives how faded its
 *                         dot is drawn
 */
public record LivePosition(
        String vehicleId,
        String routeId,
        String tripId,
        Float latitude,
        Float longitude,
        Float bearing,
        Instant vehicleTimestamp) {
}
