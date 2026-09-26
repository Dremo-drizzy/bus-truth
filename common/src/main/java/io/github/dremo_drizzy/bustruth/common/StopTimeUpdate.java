package io.github.dremo_drizzy.bustruth.common;

import java.time.Instant;

/**
 * One stop within a {@link TripUpdate}: when the vehicle is predicted to arrive and
 * depart, and when it was scheduled to.
 *
 * <p>Both the predicted time and the scheduled time are carried, and the delay is
 * left as the agency sent it. On a real snapshot the agency published
 * {@code delay} on 3 of 34,284 stop time updates but sent both times on nearly all
 * of them, so anything downstream that needs a delay should subtract rather than
 * rely on the field. Doing the subtraction here would bury a derived number inside
 * raw transport, and deriving belongs to the processor.
 *
 * @param stopSequence            position of this stop within the trip
 * @param stopId                  the stop
 * @param arrivalTime             predicted arrival
 * @param arrivalScheduledTime    scheduled arrival
 * @param arrivalDelaySeconds     delay as published, usually absent
 * @param departureTime           predicted departure
 * @param departureScheduledTime  scheduled departure
 * @param departureDelaySeconds   delay as published, usually absent
 * @param scheduleRelationship    SCHEDULED, SKIPPED, NO_DATA; absent means SCHEDULED
 */
public record StopTimeUpdate(
        Integer stopSequence,
        String stopId,
        Instant arrivalTime,
        Instant arrivalScheduledTime,
        Integer arrivalDelaySeconds,
        Instant departureTime,
        Instant departureScheduledTime,
        Integer departureDelaySeconds,
        String scheduleRelationship) {
}
