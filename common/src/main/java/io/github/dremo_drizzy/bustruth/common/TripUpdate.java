package io.github.dremo_drizzy.bustruth.common;

import java.time.Instant;
import java.util.List;

/**
 * The agency's live prediction for one trip: its stops and when it expects to reach
 * them.
 *
 * <p>{@code tripId} is never null. The feed does not always put it in
 * {@code trip.trip_id} — on a real snapshot 380 of 886 entities carried a
 * {@code modified_trip} instead — so the mapper resolves it in order from
 * {@code trip.trip_id}, {@code trip_properties.trip_id} and
 * {@code modified_trip.affected_trip_id}. All 886 resolved. The entity id is
 * deliberately not used: it repeats within a single snapshot, and one id was seen
 * describing a different trip, so it identifies nothing.
 *
 * @param tripId               the trip, resolved as described above; never null
 * @param routeId              the route, when the feed gives one
 * @param directionId          0 or 1
 * @param startDate            service date as sent, "yyyyMMdd"
 * @param startTime            scheduled start as sent, "HH:mm:ss"; GTFS allows times
 *                             past 24:00:00 for trips running after midnight, so this
 *                             stays a string rather than becoming a LocalTime
 * @param scheduleRelationship SCHEDULED, ADDED, CANCELED...; absent means SCHEDULED
 * @param modifiedTrip         true when the id came from a modified_trip, meaning the
 *                             agency is running a changed version of the trip
 * @param tripHeadsign         destination shown on the bus, from trip_properties
 * @param shapeId              the path the trip follows, from trip_properties
 * @param vehicleId            the vehicle serving the trip, when the feed says
 * @param vehicleLabel         its visible number
 * @param timestamp            when this prediction was made
 * @param feedTimestamp        when the feed was generated
 * @param stopTimeUpdates      one entry per stop, in feed order
 */
public record TripUpdate(
        String tripId,
        String routeId,
        Integer directionId,
        String startDate,
        String startTime,
        String scheduleRelationship,
        boolean modifiedTrip,
        String tripHeadsign,
        String shapeId,
        String vehicleId,
        String vehicleLabel,
        Instant timestamp,
        Instant feedTimestamp,
        List<StopTimeUpdate> stopTimeUpdates) {

    /**
     * Copies the list so the record cannot be changed through the caller's reference.
     * A record's fields are final, but a mutable List handed in would still be
     * mutable afterwards; List.copyOf also rejects nulls inside the list.
     */
    public TripUpdate {
        stopTimeUpdates = stopTimeUpdates == null ? List.of() : List.copyOf(stopTimeUpdates);
    }
}
