package io.github.dremo_drizzy.bustruth.collector.mapper;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a decoded vehicle-positions snapshot into {@link VehiclePosition} records.
 *
 * <p><b>Not implemented — Daniel writes this one.</b> Returning an empty list keeps
 * the application startable and CI green until then; the poller simply publishes
 * nothing for this feed, and the raw snapshots are still archived.
 *
 * <p>Shape of the job, from a real snapshot of 69 vehicles:
 * <ul>
 *   <li>Skip entities where {@code hasVehicle()} is false.</li>
 *   <li>{@code entity.getVehicle()} gives the VehiclePosition; inside it,
 *       {@code getVehicle()} is the VehicleDescriptor with the bus's own id and
 *       label — those two ids differ ("2526" vs "526"), and the descriptor's is
 *       Halifax's id for the bus.</li>
 *   <li>Trip fields come from {@code getTrip()}: trip id, route id, start date, and
 *       direction id, which was present on 34 of 69.</li>
 *   <li>Position fields come from {@code getPosition()}: latitude and longitude
 *       always, bearing on 68, speed on 34, odometer on 67.</li>
 *   <li>Use {@code hasX()} before {@code getX()} for anything optional, and pass
 *       null when absent. Protobuf returns 0 or "" for unset fields, and a bus at
 *       0 m/s that never reported its speed is a different thing from one standing
 *       still.</li>
 *   <li>{@code currentStatus} was present on 11 of 69; absent means IN_TRANSIT_TO.
 *       Store null and let the processor apply the default.</li>
 *   <li>Timestamps: {@code Instant.ofEpochSecond(...)} for the vehicle's own
 *       timestamp, and the header's for feedTimestamp.</li>
 * </ul>
 *
 * <p>{@code VehiclePositionMapperTest} is written and currently disabled; remove the
 * {@code @Disabled} annotation when this method is filled in.
 */
@Component
public class VehiclePositionMapper {

    public List<VehiclePosition> map(FeedMessage feed) {
        return List.of(); // TODO(Daniel): map the feed's entities to VehiclePosition
    }
}
