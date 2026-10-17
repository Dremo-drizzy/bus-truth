package io.github.dremo_drizzy.bustruth.processor;

import io.github.dremo_drizzy.bustruth.processor.detect.DetectionCounts;
import io.github.dremo_drizzy.bustruth.processor.detect.DetectionRun;
import io.github.dremo_drizzy.bustruth.processor.ontime.OntimeCounts;
import io.github.dremo_drizzy.bustruth.processor.ontime.OntimeRun;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs detection for a date range on demand.
 *
 * <p>An endpoint rather than only a schedule, because re-running a range is the normal
 * response to changing the rule, and it should be as easy as asking. Safe to call
 * twice: the run clears the range before rebuilding it.
 */
@RestController
public class ProcessorController {

    private final DetectionRun detectionRun;
    private final OntimeRun ontimeRun;

    public ProcessorController(DetectionRun detectionRun, OntimeRun ontimeRun) {
        this.detectionRun = detectionRun;
        this.ontimeRun = ontimeRun;
    }

    @PostMapping("/processor/run")
    public DetectionCounts run(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return detectionRun.run(from, to);
    }

    /**
     * Compares the arrivals already derived for a range against the schedule.
     *
     * <p>Separate from detection on purpose: the on-time window will be argued about
     * far more often than the arrival rule, and re-deciding punctuality should not
     * mean re-deriving every arrival from raw positions.
     */
    @PostMapping("/processor/ontime")
    public OntimeCounts ontime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ontimeRun.run(from, to);
    }
}
