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
 * @param arrivalMoment which position marks the arrival. The feed holds the same stop
 *                      sequence for minutes at a time, so every stop arrives as a
 *                      <em>window</em> of positions rather than one: FIRST_SIGHTING
 *                      takes that window's start, LAST_BEFORE_ADVANCE its end. The
 *                      choice moves the published on-time figure by 21 percentage
 *                      points, so it was settled by measurement. Against the 597
 *                      windows where the agency itself reported STOPPED_AT, the
 *                      window's end is a median 61 seconds late and its start a median
 *                      544 seconds early — the end is nine times closer, so
 *                      LAST_BEFORE_ADVANCE is the default. Its residual bias is late,
 *                      and it is named rather than corrected (ADR-014)
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

    /** The brief's suggested starting values, with the measured arrival moment. */
    public static DetectionSettings defaults() {
        return new DetectionSettings(
                ArrivalMoment.LAST_BEFORE_ADVANCE,
                Duration.ofSeconds(-60),
                Duration.ofSeconds(300),
                Duration.ofSeconds(120));
    }

    public DetectionSettings {
        arrivalMoment =
                arrivalMoment == null ? ArrivalMoment.LAST_BEFORE_ADVANCE : arrivalMoment;
        onTimeEarly = onTimeEarly == null ? Duration.ofSeconds(-60) : onTimeEarly;
        onTimeLate = onTimeLate == null ? Duration.ofSeconds(300) : onTimeLate;
        maxTrailGap = maxTrailGap == null ? Duration.ofSeconds(120) : maxTrailGap;
    }

    /**
     * The two ends of a stop's reporting window. Both are implemented, because the
     * difference between them is a published finding rather than an internal detail,
     * and anyone disputing the headline figure should be able to re-run the range the
     * other way and see the 21 points for themselves.
     */
    public enum ArrivalMoment {
        /** The window's start. Measured a median 544 seconds early; kept for that comparison. */
        FIRST_SIGHTING,
        /** The window's end. Measured a median 61 seconds late. The default. */
        LAST_BEFORE_ADVANCE
    }
}
