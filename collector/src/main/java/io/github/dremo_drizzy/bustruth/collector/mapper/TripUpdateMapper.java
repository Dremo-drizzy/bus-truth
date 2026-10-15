package io.github.dremo_drizzy.bustruth.collector.mapper;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import io.github.dremo_drizzy.bustruth.common.StopTimeUpdate;
import io.github.dremo_drizzy.bustruth.common.TripUpdate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Turns a decoded trip-updates snapshot into {@link TripUpdate} records. */
@Component
public class TripUpdateMapper {

    private static final Logger log = LoggerFactory.getLogger(TripUpdateMapper.class);

    public List<TripUpdate> map(FeedMessage feed) {
        Instant feedTimestamp = Instant.ofEpochSecond(feed.getHeader().getTimestamp());
        List<TripUpdate> mapped = new ArrayList<>(feed.getEntityCount());

        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasTripUpdate()) {
                continue;
            }
            GtfsRealtime.TripUpdate source = entity.getTripUpdate();
            String tripId = resolveTripId(source);
            if (tripId == null) {
                // Nothing to key on and nothing to join to the schedule with, so the
                // record would be unusable. Logged rather than dropped silently.
                log.warn("trip update with no resolvable trip id, entity {}", entity.getId());
                continue;
            }

            TripDescriptor trip = source.getTrip();
            GtfsRealtime.TripUpdate.TripProperties properties = source.getTripProperties();

            mapped.add(new TripUpdate(
                    tripId,
                    emptyToNull(trip.getRouteId()),
                    trip.hasDirectionId() ? trip.getDirectionId() : null,
                    firstNonEmpty(trip.getStartDate(), properties.getStartDate()),
                    firstNonEmpty(trip.getStartTime(), properties.getStartTime()),
                    trip.hasScheduleRelationship() ? trip.getScheduleRelationship().name() : null,
                    trip.hasModifiedTrip(),
                    emptyToNull(properties.getTripHeadsign()),
                    emptyToNull(properties.getShapeId()),
                    emptyToNull(source.getVehicle().getId()),
                    emptyToNull(source.getVehicle().getLabel()),
                    source.hasTimestamp() ? Instant.ofEpochSecond(source.getTimestamp()) : null,
                    feedTimestamp,
                    mapStopTimeUpdates(source)));
        }
        return mapped;
    }

    /**
     * Finds the trip id, which the feed puts in one of three places.
     *
     * <p>Measured on a real snapshot: 506 of 886 entities had {@code trip.trip_id};
     * the other 380 carried a {@code modified_trip} instead, and every one of those
     * still had the id in {@code trip_properties}. All 886 resolved.
     *
     * <p>The entity id is deliberately not a fallback: it repeats within one
     * snapshot, and one id was observed describing a different trip, so it identifies
     * the record rather than the trip.
     */
    static String resolveTripId(GtfsRealtime.TripUpdate source) {
        TripDescriptor trip = source.getTrip();
        String fromTrip = emptyToNull(trip.getTripId());
        if (fromTrip != null) {
            return fromTrip;
        }
        String fromProperties = emptyToNull(source.getTripProperties().getTripId());
        if (fromProperties != null) {
            return fromProperties;
        }
        return emptyToNull(trip.getModifiedTrip().getAffectedTripId());
    }

    private static List<StopTimeUpdate> mapStopTimeUpdates(GtfsRealtime.TripUpdate source) {
        List<StopTimeUpdate> stops = new ArrayList<>(source.getStopTimeUpdateCount());
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stop : source.getStopTimeUpdateList()) {
            stops.add(new StopTimeUpdate(
                    stop.hasStopSequence() ? stop.getStopSequence() : null,
                    emptyToNull(stop.getStopId()),
                    time(stop.getArrival()),
                    scheduledTime(stop.getArrival()),
                    delay(stop.getArrival()),
                    time(stop.getDeparture()),
                    scheduledTime(stop.getDeparture()),
                    delay(stop.getDeparture()),
                    stop.hasScheduleRelationship() ? stop.getScheduleRelationship().name() : null));
        }
        return stops;
    }

    private static Instant time(GtfsRealtime.TripUpdate.StopTimeEvent event) {
        return event.hasTime() ? Instant.ofEpochSecond(event.getTime()) : null;
    }

    private static Instant scheduledTime(GtfsRealtime.TripUpdate.StopTimeEvent event) {
        return event.hasScheduledTime() ? Instant.ofEpochSecond(event.getScheduledTime()) : null;
    }

    /**
     * The published delay, left exactly as sent. Halifax populated it on 3 of 34,284
     * stop time updates, so this is normally null; subtracting the scheduled time
     * from the predicted one is the processor's job, not the collector's.
     */
    private static Integer delay(GtfsRealtime.TripUpdate.StopTimeEvent event) {
        return event.hasDelay() ? event.getDelay() : null;
    }

    private static String firstNonEmpty(String first, String second) {
        String value = emptyToNull(first);
        return value != null ? value : emptyToNull(second);
    }

    /** Protobuf returns "" for an unset string; null says "absent" without ambiguity. */
    static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
