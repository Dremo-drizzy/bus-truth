package io.github.dremo_drizzy.bustruth.api.live;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The live map's only endpoint. */
@RestController
@RequestMapping("/api/vehicles")
public class LiveVehiclesController {

    private final LivePositionRepository repository;

    public LiveVehiclesController(LivePositionRepository repository) {
        this.repository = repository;
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
}
