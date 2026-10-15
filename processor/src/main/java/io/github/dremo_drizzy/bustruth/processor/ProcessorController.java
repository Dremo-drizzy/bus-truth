package io.github.dremo_drizzy.bustruth.processor;

import io.github.dremo_drizzy.bustruth.processor.detect.DetectionCounts;
import io.github.dremo_drizzy.bustruth.processor.detect.DetectionRun;
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

    public ProcessorController(DetectionRun detectionRun) {
        this.detectionRun = detectionRun;
    }

    @PostMapping("/processor/run")
    public DetectionCounts run(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return detectionRun.run(from, to);
    }
}
