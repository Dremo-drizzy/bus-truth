package io.github.dremo_drizzy.bustruth.api.performance;

import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.CoverageDay;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The project's own data gaps, published.
 *
 * <p>This endpoint is the difference between a measurement and a dashboard. Every other
 * figure the site reports is conditional on the collector having been running, and
 * collection has been as low as 42.4% on its worst day because the laptop slept. A site
 * that showed on-time percentages without showing this would be presenting its own
 * downtime as the state of the bus network.
 */
@RestController
public class CoverageController {

    /**
     * Snapshots a full day of uninterrupted collection would produce.
     *
     * <p>Derived from the 20-second poll interval rather than written as 4,320, so that
     * changing the interval cannot leave a stale denominator here quietly reporting
     * coverage above or below reality.
     */
    private static final int SNAPSHOTS_EXPECTED_PER_DAY =
            (int) (Duration.ofDays(1).toSeconds() / 20);

    private final PerformanceRepository repository;

    public CoverageController(PerformanceRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Collection coverage per day, with arrivals derived",
            description = "Snapshots that reached the database against the number a day "
                    + "of uninterrupted 20-second polling would produce. Coverage below "
                    + "100% means every figure for that day rests on part of the day.")
    @GetMapping("/api/coverage")
    public List<CoverageDay> coverage() {
        return repository.coverage(SNAPSHOTS_EXPECTED_PER_DAY);
    }
}
