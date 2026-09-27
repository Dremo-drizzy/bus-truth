package io.github.dremo_drizzy.bustruth.collector.mapper;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a decoded vehicle-positions snapshot into {@link VehiclePosition} records.
 *
 * <p>Nothing is derived or corrected here: the collector's job is to carry what the
 * agency said, faithfully, and let the processor decide what it means.
 *
 * <p>Every optional field is read through {@code hasX()} and becomes null when the
 * agency omitted it. That distinction is the whole reason the record uses boxed
 * types: protobuf returns 0 for an absent number, so a bus that never reported its
 * speed would otherwise be indistinguishable from one standing still. Measured on a
 * real 69-vehicle snapshot, {@code speed} and {@code directionId} were present on 34,
 * {@code currentStatus} on 11, and {@code bearing} on 68.
 */
@Component
public class VehiclePositionMapper {

    public List<VehiclePosition> map(FeedMessage feed) {
        Instant feedTimestamp = Instant.ofEpochSecond(feed.getHeader().getTimestamp());
        List<VehiclePosition> mapped = new ArrayList<>(feed.getEntityCount());

        for (FeedEntity entity : feed.getEntityList()) {
            // The alerts and trip-updates feeds use the same envelope type, and a
            // vehicle-positions feed can carry other entity kinds too.
            if (!entity.hasVehicle()) {
                continue;
            }
            mapped.add(toRecord(entity.getVehicle(), feedTimestamp));
        }
        return mapped;
    }

    private static VehiclePosition toRecord(GtfsRealtime.VehiclePosition source,
                                            Instant feedTimestamp) {
        // Reading these three sub-messages once keeps the constructor call readable.
        // Asking protobuf for an absent sub-message returns an empty one rather than
        // null, so this is safe even when the agency omitted the whole block — every
        // field inside then reports has...() == false.
        VehicleDescriptor descriptor = source.getVehicle();
        TripDescriptor trip = source.getTrip();
        Position position = source.getPosition();

        return new VehiclePosition(
                // The descriptor's id, not entity.getId(): in a real snapshot the
                // entity id was "526" while this was "2526". This one is Halifax's
                // id for the bus; the entity id names the record in the feed.
                TripUpdateMapper.emptyToNull(descriptor.getId()),
                TripUpdateMapper.emptyToNull(descriptor.getLabel()),
                TripUpdateMapper.emptyToNull(trip.getTripId()),
                TripUpdateMapper.emptyToNull(trip.getRouteId()),
                trip.hasDirectionId() ? trip.getDirectionId() : null,
                // Left as the agency's "yyyyMMdd" string. Turning it into a date
                // needs the America/Halifax service-date rule, which is the
                // processor's business, not the collector's.
                TripUpdateMapper.emptyToNull(trip.getStartDate()),
                position.hasLatitude() ? position.getLatitude() : null,
                position.hasLongitude() ? position.getLongitude() : null,
                position.hasBearing() ? position.getBearing() : null,
                position.hasSpeed() ? position.getSpeed() : null,
                position.hasOdometer() ? position.getOdometer() : null,
                source.hasCurrentStopSequence() ? source.getCurrentStopSequence() : null,
                TripUpdateMapper.emptyToNull(source.getStopId()),
                // Absent means IN_TRANSIT_TO by the GTFS-Realtime default. Stored as
                // null so the processor applies that default knowingly, instead of
                // this mapper inventing a status the agency never sent.
                source.hasCurrentStatus() ? source.getCurrentStatus().name() : null,
                source.hasOccupancyStatus() ? source.getOccupancyStatus().name() : null,
                source.hasOccupancyPercentage() ? source.getOccupancyPercentage() : null,
                // When the vehicle reported this position, which can lag the feed's
                // own timestamp by a few seconds.
                source.hasTimestamp() ? Instant.ofEpochSecond(source.getTimestamp()) : null,
                feedTimestamp);
    }
}
