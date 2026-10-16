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
     * How much stricter a ranking has to be than a bare figure.
     *
     * <p>This is the one editorial choice left in here, and it is named rather than
     * buried. Showing "62.9% of 679 arrivals" is a weak claim; placing a route above
     * or below another is a strong one, so the noise behind a ranking should be a
     * fraction of the disclosed uncertainty rather than merely smaller than it. A
     * quarter is the factor; its consequence is in ADR-017's table.
     */
    private static final double RANKING_STRICTNESS = 4;

    private PublishedUncertainty() {
    }

    /**
     * Fewest observations for which a percentage may be shown at all.
     *
     * <p>The smallest n where one observation moves the figure by less than the
     * published uncertainty.
     */
    public static int displayFloor() {
        return smallestSampleWhereOneObservationMovesLessThan(HEADLINE_SENSITIVITY_POINTS);
    }

    /**
     * Fewest observations for which a route or stop may take a position in a ranking.
     */
    public static int rankingFloor() {
        return smallestSampleWhereOneObservationMovesLessThan(
                HEADLINE_SENSITIVITY_POINTS / RANKING_STRICTNESS);
    }

    /**
     * The smallest n such that {@code 100/n < points}.
     *
     * <p>Computed rather than tabulated so that changing the measured sensitivity moves
     * both floors by itself. Floored at 2, because a percentage over a single
     * observation is never anything but 0% or 100%.
     */
    static int smallestSampleWhereOneObservationMovesLessThan(double points) {
        if (points <= 0) {
            throw new IllegalArgumentException("uncertainty must be positive: " + points);
        }
        int n = (int) Math.floor(100.0 / points) + 1;
        return Math.max(2, n);
    }
}
