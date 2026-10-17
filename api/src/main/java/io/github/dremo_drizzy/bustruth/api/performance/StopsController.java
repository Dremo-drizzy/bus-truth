package io.github.dremo_drizzy.bustruth.api.performance;

import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRepository.Totals;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.Stop;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.StopReliability;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * One stop's reliability, broken down by the routes that call there.
 *
 * <p>The thinnest page on the site, by construction. Arrival detection observes 17.5%
 * of scheduled stop-events because the feed reports a trip's position about 6.8 times
 * across 38.3 stops (ADR-014), and that thinness lands hardest here: a route has
 * thousands of arrivals to average, while a single stop may have a handful. Which is
 * why the counts are not optional extras in this response.
 */
@RestController
@RequestMapping("/api/stops")
public class StopsController {

    private final PerformanceRepository repository;

    public StopsController(PerformanceRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "One stop: arrivals observed, on-time share, median delay, by route",
            description = "404 only when the schedule has no such stop. Counts accompany "
                    + "every share because stop-level coverage is thin: a share over a "
                    + "handful of arrivals is not a reliability figure.")
    @GetMapping("/{stopId}/reliability")
    public StopReliability reliability(
            @PathVariable String stopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoutesController.requireOrderedRange(from, to);
        Stop stop = repository.stop(stopId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "no stop " + stopId + " in the published schedule"));

        Totals totals = repository.stopTotals(stopId, from, to)
                .orElse(new Totals(0, 0, null, null));

        return new StopReliability(
                stop.stopId(),
                stop.stopName(),
                totals.arrivals(),
                Share.of(totals.onTime(), totals.arrivals()),
                totals.medianDelaySeconds(),
                totals.p90DelaySeconds(),
                repository.stopByRoute(stopId, from, to));
    }
}
