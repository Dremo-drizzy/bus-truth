package io.github.dremo_drizzy.bustruth.common;

import java.time.Instant;

/**
 * Where one vehicle was at one moment, as reported by the vehicle positions feed.
 *
 * <p>Boxed types are used for every field the feed may omit, so {@code null} means
 * "the agency did not send this" rather than a made-up zero. Measured on a real
 * snapshot, {@code currentStatus} was present on 11 of 69 vehicles and
 * {@code speed} on half, so those omissions are normal, not errors.
 *
 * @param vehicleId          the agency's id for the vehicle (GTFS-RT vehicle.id)
 * @param vehicleLabel       the number shown on the bus, usually
 * @param tripId             the trip the vehicle is running, if it is on one
 * @param routeId            the route of that trip
 * @param directionId        0 or 1, the direction of travel along the route
 * @param startDate          the trip's service date as the agency sent it, "yyyyMMdd"
 * @param latitude           degrees north
 * @param longitude          degrees east
 * @param bearing            degrees clockwise from true north
 * @param speed              metres per second
 * @param odometer           metres travelled, as reported by the vehicle
 * @param currentStopSequence position of the stop being approached within the trip
 * @param stopId             the stop being approached
 * @param currentStatus      INCOMING_AT, STOPPED_AT or IN_TRANSIT_TO; absent means
 *                           IN_TRANSIT_TO, which is the GTFS-RT default
 * @param occupancyStatus    how full the vehicle is, as an enum name
 * @param occupancyPercentage how full the vehicle is, as a percentage
 * @param timestamp          when the vehicle reported this position
 * @param feedTimestamp      when the feed itself was generated; identifies the
 *                           snapshot this record came from
 */
public record VehiclePosition(
        String vehicleId,
        String vehicleLabel,
        String tripId,
        String routeId,
        Integer directionId,
        String startDate,
        Double latitude,
        Double longitude,
        Float bearing,
        Float speed,
        Double odometer,
        Integer currentStopSequence,
        String stopId,
        String currentStatus,
        String occupancyStatus,
        Integer occupancyPercentage,
        Instant timestamp,
        Instant feedTimestamp) {
}
