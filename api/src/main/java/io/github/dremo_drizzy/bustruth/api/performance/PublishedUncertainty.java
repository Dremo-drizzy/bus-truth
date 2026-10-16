package io.github.dremo_drizzy.bustruth.api.performance;

/**
 * The uncertainty this project publishes, and the observation floors derived from it.
 *
 * <p>The floors are <b>computed here, not chosen</b>, and they are computed from one
 * number. That is deliberate: the first version of them was derived from a 21-point
 * figure, and when the arrival moment was later calibrated against ground truth, the
 * 21 points stopped being what the project claimed — leaving two floors in the code
 * justified by a number no longer in the README. Deriving them mechanically means the
 * drift cannot happen twice.
 *
 * <h2>The principle</h2>
 *
 * A single observation moves a percentage by {@code 100/n} points. A sample is too thin
 * when one observation can swing its percentage further than the uncertainty the
 * project already admits to — at that point the figure is describing its own sample
 * size rather than the service.
 *
 * <h2>The number</h2>
 *
 * {@link #HEADLINE_SENSITIVITY_POINTS} is the measured sensitivity of the published
 * on-time share to the one bias this project knows it has and does not correct: the
 * arrival is timed from the end of a stop's reporting window, which the agency's own
 * {@code STOPPED_AT} positions put a median 61 seconds after the bus actually got
 * there (ADR-014). Shifting every observed arrival earlier by that much moves the
 * on-time share by this many points, and that span is what the method page publishes.
 * Measured over 4–10 October 2026: 58.4% on time as published, 49.7% with every
 * arrival shifted 61 seconds earlier, so the span is 8.7 points.
 *
 * <p>It is not the older 21-point figure. Twenty-one points was the gap between two
 * candidate definitions while the choice was open; the choice is now settled by
 * measurement, so what remains uncertain is the residual bias of the definition that
 * won, which is a narrower thing. Narrower uncertainty means <em>stricter</em> floors,
 * because sampling noise that was once comfortably inside the methodological
 * uncertainty now stands out against it. Improving the method shortens the leaderboard,
 * and that is the correct direction.
 */
public final class PublishedUncertainty {

    /**
     * Percentage points. Measured, not assumed — see ADR-017 for the derivation and
     * the query that produced it.
     */
    public static final double HEADLINE_SENSITIVITY_POINTS = 8.7;

    /**
     * How many figures a claim rests on.
     *
     * <p>Showing a percentage rests on one figure. Ranking rests on two — "this route
     * is worse than that one" is a statement about a pair, and each side carries its
     * own sampling noise, so the noise behind the comparison is twice the noise behind
     * either number.
     *
     * <p>This replaced a chosen constant. The ranking floor was previously "a quarter
     * of the margin", which read like arithmetic and was taste: a free parameter hidden
     * inside a derivation, which is the same shape as the invented threshold this
     * project has already had to withdraw once. Two is not a preference — it is how
     * many percentages are involved in a comparison.
     */
    private static final int FIGURES_IN_A_SHOWN_PERCENTAGE = 1;

    private static final int FIGURES_IN_A_COMPARISON = 2;

    private PublishedUncertainty() {
    }

    /**
     * Fewest observations for which a percentage may be shown at all.
     *
     * <p>The smallest n where one observation moves the figure by less than the
     * published uncertainty.
     */
    public static int displayFloor() {
        return smallestSampleWhereNoiseStaysWithinTheMargin(FIGURES_IN_A_SHOWN_PERCENTAGE);
    }

    /**
     * Fewest observations for which a route or stop may take a position in a ranking.
     */
    public static int rankingFloor() {
        return smallestSampleWhereNoiseStaysWithinTheMargin(FIGURES_IN_A_COMPARISON);
    }

    /**
     * The smallest sample in which the noise behind a claim stays inside the margin.
     *
     * <p>One observation moves a percentage by {@code 100/n} points, and a claim
     * resting on {@code figures} percentages carries that much noise per figure. So the
     * requirement is {@code figures * 100/n < margin}, and the answer is the smallest n
     * that satisfies it.
     *
     * <p>Computed rather than tabulated, so re-measuring the margin moves both floors
     * by itself. Floored at 2, because a percentage over a single observation is never
     * anything but 0% or 100%.
     */
    static int smallestSampleWhereNoiseStaysWithinTheMargin(int figures) {
        return smallestSampleWhereNoiseStaysWithin(HEADLINE_SENSITIVITY_POINTS, figures);
    }

    static int smallestSampleWhereNoiseStaysWithin(double margin, int figures) {
        if (margin <= 0) {
            throw new IllegalArgumentException("the margin must be positive: " + margin);
        }
        if (figures < 1) {
            throw new IllegalArgumentException("a claim rests on at least one figure");
        }
        int n = (int) Math.floor(figures * 100.0 / margin) + 1;
        return Math.max(2, n);
    }
}
