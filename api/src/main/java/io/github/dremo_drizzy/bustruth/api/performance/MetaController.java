package io.github.dremo_drizzy.bustruth.api.performance;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The figures the site has to agree with itself about.
 *
 * <p>This endpoint exists to stop a number being written down twice. The frontend has
 * to know the floor below which it must say "too few observations" rather than print a
 * percentage, and that floor is derived from the project's published uncertainty — so
 * either the frontend holds its own copy and the two drift apart in six weeks, or it
 * asks. It asks.
 *
 * <p>Only what this module owns is here. The on-time window and the arrival moment are
 * the processor's settings, not the api's, and publishing them from here would be the
 * same duplication in a different direction: the api would be asserting values it never
 * applied. They belong on the method page beside their own decision record.
 */
@RestController
public class MetaController {

    @Operation(summary = "The published uncertainty, and the observation floors derived from it",
            description = "The frontend reads its display floor from here rather than "
                    + "holding a second copy of a number that is derived in one place.")
    @GetMapping("/api/meta")
    public Meta meta() {
        return new Meta(
                PublishedUncertainty.HEADLINE_SENSITIVITY_POINTS,
                PublishedUncertainty.displayFloor(),
                PublishedUncertainty.rankingFloor());
    }

    /**
     * @param headlineSensitivityPoints how far the published on-time share moves under
     *                                  the one bias the project knows about and does
     *                                  not correct (ADR-014)
     * @param displayFloor              fewest arrivals for which a percentage is shown
     *                                  at all
     * @param rankingFloor              fewest arrivals for which a route or stop may
     *                                  hold a position in a ranking
     */
    public record Meta(
            @JsonProperty("headline_sensitivity_points") double headlineSensitivityPoints,
            @JsonProperty("display_floor") int displayFloor,
            @JsonProperty("ranking_floor") int rankingFloor) {
    }
}
