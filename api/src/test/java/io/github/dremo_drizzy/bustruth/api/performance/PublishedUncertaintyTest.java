package io.github.dremo_drizzy.bustruth.api.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The observation floors are derived, and these tests are what stops them being
 * quietly re-chosen.
 *
 * <p>The first version of the floors was justified by a 21-point uncertainty. The
 * arrival moment was then calibrated against the agency's own STOPPED_AT positions, the
 * published uncertainty narrowed to 8.7 points, and two hand-written floors in the code
 * were left resting on a figure the project no longer claimed. Deriving them from one
 * constant means that cannot recur — and these tests mean the derivation itself cannot
 * drift into a lookup table of numbers somebody liked.
 */
class PublishedUncertaintyTest {

    @ParameterizedTest(name = "one observation must move less than {0} points, so n = {1}")
    @CsvSource({
            // The arithmetic, at the figures this project has actually published.
            "21.0, 5",     // the old definitional swing: 100/5 = 20 < 21
            "8.7, 12",     // today's measured sensitivity: 100/12 = 8.3 < 8.7
            "2.175, 46",   // today's, a quarter of it, for rankings
            "100.0, 2",    // nonsense uncertainty still cannot admit a sample of one
    })
    void theFloorIsTheSmallestSampleOneObservationCannotSwingTooFar(
            double points, int expected) {
        assertThat(PublishedUncertainty.smallestSampleWhereOneObservationMovesLessThan(points))
                .isEqualTo(expected);
        // The defining property, asserted rather than assumed: at the floor one
        // observation moves less than the uncertainty, and one below it does not.
        assertThat(100.0 / expected).isLessThan(points);
        if (expected > 2) {
            assertThat(100.0 / (expected - 1)).isGreaterThanOrEqualTo(points);
        }
    }

    @Test
    @DisplayName("the published floors follow from the published uncertainty")
    void theFloorsAreDerivedFromTheConstant() {
        assertThat(PublishedUncertainty.HEADLINE_SENSITIVITY_POINTS).isEqualTo(8.7);
        assertThat(PublishedUncertainty.displayFloor()).isEqualTo(12);
        assertThat(PublishedUncertainty.rankingFloor()).isEqualTo(46);
    }

    @Test
    @DisplayName("ranking is stricter than merely showing a figure")
    void rankingIsStricterThanDisplay() {
        // Placing one route above another is a stronger claim than printing its
        // percentage, so it needs a quieter sample. If this ever inverts, the
        // leaderboard is ranking rows it would refuse to print a number for.
        assertThat(PublishedUncertainty.rankingFloor())
                .isGreaterThan(PublishedUncertainty.displayFloor());
    }

    @Test
    @DisplayName("a zero or negative uncertainty is a bug, not a floor of infinity")
    void refusesAnImpossibleUncertainty() {
        assertThatThrownBy(() ->
                PublishedUncertainty.smallestSampleWhereOneObservationMovesLessThan(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
