package io.github.dremo_drizzy.bustruth.api.performance;

import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRepository.Totals;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.Route;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.RouteDetail;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.RoutePerformance;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Routes: the dropdown, the leaderboard, and one route's detail. */
@RestController
@RequestMapping("/api/routes")
public class RoutesController {

    /**
     * Below this many arrivals a stop is left out of the worst-stops ranking.
     *
     * <p>A presentation guard, not a judgement about service: sorting stops by a
     * percentage over two observations ranks the stops with the fewest observations,
     * not the least reliable ones. Overridable per request, and the value applied comes
     * back in the response so the page can state it.
     */
    private static final int DEFAULT_MIN_ARRIVALS = 5;

    private static final int WORST_STOPS = 10;

    private final PerformanceRepository repository;

    public RoutesController(PerformanceRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Every route in the newest published schedule",
            description = "For dropdowns. Names come from the most recently loaded "
                    + "publication, which is the label a passenger expects today.")
    @GetMapping
    public List<Route> routes() {
        return repository.routes();
    }

    @Operation(summary = "On-time performance per route, ranked",
            description = "Routes with at least minArrivals arrivals rank first, by "
                    + "on-time share, ties broken by count; thinly observed routes are "
                    + "still returned, marked rankable=false, and sort last. Sorting on "
                    + "share alone ranks routes by how little was observed of them.")
    @GetMapping("/performance")
    public List<RoutePerformance> performance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer minArrivals) {
        requireOrderedRange(from, to);
        return repository.routePerformance(from, to,
                minArrivals == null ? DEFAULT_MIN_ARRIVALS : minArrivals);
    }

    /**
     * One route in detail.
     *
     * <p>A known route with no observations in the range returns a body of zeros and
     * empty lists, not a 404. The two are different facts — "no such route" against
     * "this route ran and we recorded nothing" — and the second is a statement about
     * this project's own coverage that it should not be allowed to hide behind a
     * missing page.
     */
    @Operation(summary = "One route: by hour, by day, and its worst stops",
            description = "404 only when the schedule has no such route. A route with no "
                    + "observations in the range returns zeros, which is a fact about "
                    + "our coverage rather than a missing page.")
    @GetMapping("/{routeId}/performance")
    public RouteDetail routeDetail(
            @PathVariable String routeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer minArrivals) {
        requireOrderedRange(from, to);
        Route route = repository.route(routeId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "no route " + routeId + " in the published schedule"));

        int threshold = minArrivals == null ? DEFAULT_MIN_ARRIVALS : minArrivals;
        Totals totals = repository.routeTotals(routeId, from, to)
                .orElse(new Totals(0, 0, null, null));

        return new RouteDetail(
                route.routeId(),
                route.routeShortName(),
                route.routeLongName(),
                totals.arrivals(),
                Share.of(totals.onTime(), totals.arrivals()),
                totals.medianDelaySeconds(),
                totals.p90DelaySeconds(),
                repository.routeByHour(routeId, from, to),
                repository.routeByDay(routeId, from, to),
                repository.routeWorstStops(routeId, from, to, threshold, WORST_STOPS),
                threshold,
                repository.routeStopsBelowThreshold(routeId, from, to, threshold));
    }

    /**
     * A reversed range is a caller mistake, not an empty result.
     *
     * <p>{@code between} returns nothing for from > to, so without this the answer to
     * a typo is a confident "no buses were on time", which is worse than an error.
     */
    static void requireOrderedRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "to (" + to + ") is before from (" + from + ")");
        }
    }
}
