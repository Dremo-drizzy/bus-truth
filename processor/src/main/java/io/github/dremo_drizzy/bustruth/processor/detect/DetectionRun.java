package io.github.dremo_drizzy.bustruth.processor.detect;

import io.github.dremo_drizzy.bustruth.common.ArrivalEvent;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.github.dremo_drizzy.bustruth.processor.store.ArrivalEventRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives arrivals for a date range, re-runnably.
 *
 * <p>Re-runnable is the whole design: the arrival rule will change, and when it does
 * the fix is to run the range again rather than to patch rows. So a run deletes the
 * range it is about to rebuild, inside one transaction — a half-rebuilt range would
 * hold two rules' output with no way to tell the rows apart.
 */
@Service
public class DetectionRun {

    private static final Logger log = LoggerFactory.getLogger(DetectionRun.class);

    private final ArrivalEventRepository repository;
    private final DetectionSettings settings;

    public DetectionRun(ArrivalEventRepository repository, DetectionSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    @Transactional
    public DetectionCounts run(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to (" + to + ") is before from (" + from + ")");
        }

        // The settings, every run, before anything else. They decide what the project
        // publishes, and a run that does not state them lets a stale configuration
        // value pass unnoticed — which is exactly what happened: application.yml
        // pinned the old arrival moment and quietly overrode the code's default for a
        // full week's run.
        log.info("settings: arrival moment {}, on-time window {}s to +{}s, max trail gap {}s",
                settings.arrivalMoment(), settings.onTimeEarly().toSeconds(),
                settings.onTimeLate().toSeconds(), settings.maxTrailGap().toSeconds());

        int cleared = repository.deleteRange(from, to);
        log.info("cleared {} existing arrivals for {} .. {}", cleared, from, to);

        DetectionCounts total = DetectionCounts.zero();
        int written = 0;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            List<VehiclePosition> positions = repository.positionsForDay(day);
            Map<TrailKey, List<VehiclePosition>> trails = groupIntoTrails(positions, day);

            List<ArrivalEvent> arrivals = new ArrayList<>();
            DetectionCounts dayCounts = DetectionCounts.zero();

            for (List<VehiclePosition> trail : trails.values()) {
                // A detector per trail: it holds the counts of the last call, so
                // sharing one across trails would lose them.
                ArrivalDetector detector = new PositionBasedDetector(settings);
                arrivals.addAll(detector.detect(trail));
                dayCounts = dayCounts.plus(detector.counts());
            }

            int dayWritten = repository.insertAll(arrivals);
            written += dayWritten;
            total = total.plus(dayCounts);

            log.info("{}: {} positions, {} trails, {} — {} rows written",
                    day, positions.size(), trails.size(), dayCounts, dayWritten);
        }

        log.info("run complete for {} .. {}: {} — {} rows written",
                from, to, total, written);
        return total;
    }

    /**
     * One trail per vehicle per trip per service day.
     *
     * <p>Grouping by vehicle alone would splice two trips together and read the
     * handover as a sequence jumping backwards; grouping by trip alone would splice
     * two buses running the same trip id on different days. The service day is in the
     * key because the same trip id runs again tomorrow.
     *
     * <p>Both the filter and the key use {@link ServiceDay}, not the feed's raw
     * {@code start_date}. That matters for more than tidiness: a post-midnight trip
     * the feed stamps with the following calendar date belongs to the previous service
     * day, so judging membership on the raw value would pull it into the wrong run —
     * and since a run only clears the range it is rebuilding, it would emit rows
     * outside its own delete range and leave them to go stale.
     */
    private static Map<TrailKey, List<VehiclePosition>> groupIntoTrails(
            List<VehiclePosition> positions, LocalDate day) {
        Map<TrailKey, List<VehiclePosition>> trails = new LinkedHashMap<>();
        for (VehiclePosition position : positions) {
            LocalDate serviceDay = ServiceDay.of(position);
            if (!serviceDay.equals(day)) {
                continue;
            }
            trails.computeIfAbsent(
                    new TrailKey(position.vehicleId(), position.tripId(), serviceDay),
                    key -> new ArrayList<>()).add(position);
        }
        return trails;
    }

    private record TrailKey(String vehicleId, String tripId, LocalDate serviceDay) {
    }
}
