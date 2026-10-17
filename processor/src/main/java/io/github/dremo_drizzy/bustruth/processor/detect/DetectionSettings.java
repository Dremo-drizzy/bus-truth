package io.github.dremo_drizzy.bustruth.processor.detect;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The judgement calls in arrival detection, named and configurable rather than buried
 * in expressions.
 *
 * <p><b>NEEDS DANIEL'S REVIEW</b> — the values below are the build brief's suggested
 * starting points, written by Claude Code to unblock the build. They are the four
 * numbers that decide what this project publishes, and they are his to set.
 *
 * @param arrivalMoment which position marks the arrival. FIRST_SIGHTING takes the
 *                      first position reporting the stop; LAST_BEFORE_ADVANCE takes
 *                      the last one before the sequence moves on. The first answers
 *                      "when did it get there", the second "when did it leave", and
 *                      for a bus that waits at a terminus they differ by minutes.
 * @param onTimeEarly   how early still counts as on time, negative seconds
 * @param onTimeLate    how late still counts as on time, positive seconds
 * @param maxTrailGap   a gap longer than this breaks the trail, and no arrival is
 *                      derived across it. Not optional: collection coverage was
 *                      measured at 73.7% with a worst day of 42.4%, because the
 *                      laptop slept. Without this rule a sleeping laptop is
 *                      indistinguishable from a cancelled trip, and the project would
 *                      publish invented cancellations.
 */
@ConfigurationProperties(prefix = "bustruth.processor")
public record DetectionSettings(
        ArrivalMoment arrivalMoment,
        Duration onTimeEarly,
        Duration onTimeLate,
        Duration maxTrailGap) {

    /** The brief's suggested starting values. */
    public static DetectionSettings defaults() {
        return new DetectionSettings(
                ArrivalMoment.FIRST_SIGHTING,
                Duration.ofSeconds(-60),
                Duration.ofSeconds(300),
                Duration.ofSeconds(120));
    }

    public DetectionSettings {
        arrivalMoment = arrivalMoment == null ? ArrivalMoment.FIRST_SIGHTING : arrivalMoment;
        onTimeEarly = onTimeEarly == null ? Duration.ofSeconds(-60) : onTimeEarly;
        onTimeLate = onTimeLate == null ? Duration.ofSeconds(300) : onTimeLate;
        maxTrailGap = maxTrailGap == null ? Duration.ofSeconds(120) : maxTrailGap;
    }

    /** Both are implemented; the first is the default. */
    public enum ArrivalMoment {
        FIRST_SIGHTING,
        LAST_BEFORE_ADVANCE
    }
}
