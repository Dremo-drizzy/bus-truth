package io.github.dremo_drizzy.bustruth.api.live;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final LivePositionRepository repository;
    private final MapSnapshotRepository snapshots;
    private final boolean liveFeedEnabled;

    /**
     * @param liveFeedEnabled whether anything feeds this deployment live positions.
     *                        True by default, which is right wherever a collector is
     *                        running — local development included. The hosted site sets
     *                        it false: it holds only the derived tables, and no
     *                        collector writes to it. Both spellings are accepted for the
     *                        same reason as in WebOriginConfig: a silently ignored
     *                        environment variable here would put the endpoint back to
     *                        answering 500.
     */
    public LiveVehiclesController(
            LivePositionRepository repository,
            MapSnapshotRepository snapshots,
            @Value("${bustruth.api.live-feed-enabled:${BUSTRUTH_API_LIVE_FEED_ENABLED:true}}")
            boolean liveFeedEnabled) {
        this.repository = repository;
        this.snapshots = snapshots;
        this.liveFeedEnabled = liveFeedEnabled;
    }

    /**
     * Every vehicle that has reported in the last ten minutes, newest position each.
     *
     * <p>An empty feature list is a valid answer, not an error: buses stop running at
     * night. The caller tells that apart from a broken pipeline by reading
     * {@code newest_position_at}.
     *
     * <h2>When this deployment has no live feed</h2>
     *
     * The answer is <b>503 with a problem document</b>, not an empty list and not a 500.
     *
     * <p>An empty list would be a false zero: {@code 200} with no vehicles reads as "we
     * looked at Halifax and there are no buses". There are buses; nothing is looking.
     * That is the same lie this project refuses everywhere else — {@code ontime_quality}
     * answers null rather than zero for a day never compared, and coverage answers null
     * for a day never counted. A 500 was what it did before this was configured, and it
     * is wrong in the other direction: it says something broke, when the deployment is
     * doing exactly what it was set up to do.
     *
     * <p>503 Service Unavailable says the true thing: this service does not provide a
     * live feed here. The body is RFC 9457 {@code application/problem+json} and names
     * {@code /api/vehicles/snapshot} as the endpoint that does answer. The database is
     * never queried on this path — there is nothing in it to find.
     */
    @Operation(summary = "Latest reported position per vehicle, as GeoJSON",
            description = "One Point feature per vehicle seen in the last ten minutes. "
                    + "An empty collection with a recent newest_position_at means no "
                    + "buses are running; an old newest_position_at means no data is "
                    + "arriving. On a deployment with no collector feeding it — such as "
                    + "the hosted site — this answers 503 with a problem document rather "
                    + "than an empty collection, because an empty collection would claim "
                    + "there are no buses when the truth is that nothing is looking. "
                    + "Use /api/vehicles/snapshot there.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Live positions, possibly none if no buses are running.",
                    content = @Content(mediaType = "application/geo+json",
                            schema = @Schema(implementation = VehicleFeatureCollection.class))),
            @ApiResponse(responseCode = "503",
                    description = "This deployment has no live feed: no collector writes "
                            + "to it, so there is nothing current to report. Not an error "
                            + "and not 'no buses' — /api/vehicles/snapshot serves a named "
                            + "historical minute instead.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/live")
    public ResponseEntity<?> live() {
        if (!liveFeedEnabled) {
            return liveFeedUnavailable();
        }

        List<VehicleFeatureCollection.Feature> features = repository.latestPerVehicle().stream()
                // A row with no coordinates cannot be drawn. Dropping it here keeps the
                // GeoJSON valid rather than emitting a Point with nulls in it.
                .filter(position -> position.latitude() != null && position.longitude() != null)
                .map(VehicleFeatureCollection.Feature::of)
                .toList();

        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .body(VehicleFeatureCollection.of(
                        features, repository.newestPositionAt().orElse(null)));
    }

    /**
     * The answer for a deployment with nothing collecting.
     *
     * <p>Built here rather than by throwing, so the body says what is true instead of
     * Spring's generic error shape — and so nothing on this path can reach the
     * database by accident.
     */
    static ResponseEntity<ProblemDetail> liveFeedUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "This deployment serves a historical snapshot and has no live feed: no "
                        + "collector writes vehicle positions to it, so there is nothing "
                        + "current to report. This is not the same as there being no buses.");
        problem.setTitle("No live feed on this deployment");
        problem.setType(URI.create("about:blank"));
        problem.setProperty("snapshot", "/api/vehicles/snapshot");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * One curated minute of positions, with the minute it is.
     *
     * <p>For deployments where nothing is collecting: the hosted site holds only the
     * derived tables, so {@code /live} has nothing to report there and answers 503 by
     * configuration (see {@link #live()}). A map with nothing on it for ever would read
     * as broken rather than honest, so this answers with
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
