package io.github.dremo_drizzy.bustruth.api.live;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the map draws: either what is happening now, or one named minute that happened.
 *
 * <p>Two endpoints rather than one with a fallback, and that is the whole point. A
 * deployment with no collector has nothing live to serve, and a live endpoint quietly
 * answering with a fortnight-old snapshot would be the one dishonest thing on this
 * site. So the historical minute has its own endpoint, its own response type and its
 * own timestamp field, and the page that draws it says which minute it is.
 */
@RestController
@RequestMapping("/api/vehicles")
public class LiveVehiclesController {

    private final LivePositionRepository repository;
    private final MapSnapshotRepository snapshots;

    public LiveVehiclesController(LivePositionRepository repository,
                                  MapSnapshotRepository snapshots) {
        this.repository = repository;
        this.snapshots = snapshots;
    }

    /**
     * Every vehicle that has reported in the last ten minutes, newest position each.
     *
     * <p>An empty feature list is a valid answer, not an error: buses stop running at
     * night. The caller tells that apart from a broken pipeline by reading
     * {@code newest_position_at}.
     */
    @Operation(summary = "Latest reported position per vehicle, as GeoJSON",
            description = "One Point feature per vehicle seen in the last ten minutes. "
                    + "An empty collection with a recent newest_position_at means no "
                    + "buses are running; an old newest_position_at means no data is "
                    + "arriving.")
    @GetMapping(value = "/live", produces = "application/geo+json")
    public VehicleFeatureCollection live() {
        List<VehicleFeatureCollection.Feature> features = repository.latestPerVehicle().stream()
                // A row with no coordinates cannot be drawn. Dropping it here keeps the
                // GeoJSON valid rather than emitting a Point with nulls in it.
                .filter(position -> position.latitude() != null && position.longitude() != null)
                .map(VehicleFeatureCollection.Feature::of)
                .toList();

        return VehicleFeatureCollection.of(features, repository.newestPositionAt().orElse(null));
    }

    /**
     * One curated minute of positions, with the minute it is.
     *
     * <p>For deployments where nothing is collecting: the hosted site holds only the
     * derived tables, so {@code /live} is permanently empty there and a map showing
     * "no recent data" for ever reads as broken rather than honest. This answers with
     * a named historical moment instead — 290 vehicles at 17:08 on 6 October 2026, the
     * busiest minute of the network's least punctual hour.
     *
     * <p>It never pretends to be live. The response carries {@code snapshot_at} and no
     * freshness fields at all, and the page labels itself with the date and time in
     * words.
     */
    @Operation(summary = "One named minute of vehicle positions, as GeoJSON",
            description = "For deployments with no live collector. Carries snapshot_at "
                    + "— the moment being shown — and never a freshness timestamp: this "
                    + "is history, and the page says so.")
    @GetMapping(value = "/snapshot", produces = "application/geo+json")
    public VehicleSnapshot snapshot() {
        return snapshots.snapshotAt()
                .map(at -> VehicleSnapshot.of(at, snapshots.positions().stream()
                        .filter(p -> p.latitude() != null && p.longitude() != null)
                        .map(VehicleFeatureCollection.Feature::of)
                        .toList()))
                .orElseGet(VehicleSnapshot::empty);
    }
}
