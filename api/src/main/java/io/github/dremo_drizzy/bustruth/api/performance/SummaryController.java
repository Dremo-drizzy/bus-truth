package io.github.dremo_drizzy.bustruth.api.performance;

import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRepository.Totals;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.NetworkSummary;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The whole network for a range, and the days inside it.
 *
 * <p>This endpoint exists because of a measurement rather than a design preference.
 * Across the published week, Halifax's on-time performance ranged from 46.9% to 68.3%
 * by service date — a spread of 21 points, which is wider than the 17.5-point gap
 * between the two candidate definitions of "arrived" and wider still than the
 * 8.7-point margin of error that remains after the definition was settled.
 *
 * <p>In other words, <b>the buses vary more from day to day than the methodology
 * does.</b> A single percentage presented as "Halifax's on-time performance" would
 * imply a precision the data does not support, so the summary always carries both its
 * period and the days it averages, and the site leads with the pair.
 */
@RestController
public class SummaryController {

    private final PerformanceRepository repository;

    public SummaryController(PerformanceRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "The network's on-time performance for a range, with each day",
            description = "The days come with the total on purpose: day-to-day spread "
                    + "across the published week is 21 points, wider than any "
                    + "methodological choice in this project, so a single figure for a "
                    + "range is only honest beside the days it averages.")
    @GetMapping("/api/performance/summary")
    public NetworkSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RoutesController.requireOrderedRange(from, to);
        Totals totals = repository.networkTotals(from, to);

        return new NetworkSummary(
                from,
                to,
                totals.arrivals(),
                Share.of(totals.onTime(), totals.arrivals()),
                totals.medianDelaySeconds(),
                totals.p90DelaySeconds(),
                repository.networkByDay(from, to),
                repository.networkByHour(from, to));
    }
}
