package io.github.dremo_drizzy.bustruth.api.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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

    @ParameterizedTest(name = "{1} figure(s) within {0} points needs n = {2}")
    @CsvSource({
            // The arithmetic, at the margins this project has actually published.
            "21.0, 1,  5",   // the old definitional swing: 100/5 = 20 < 21
            "8.7,  1, 12",   // today's margin, one figure: 100/12 = 8.3 < 8.7
            "8.7,  2, 23",   // today's margin, a comparison: 200/23 = 8.70 < 8.7
            "100.0, 1, 2",   // a nonsense margin still cannot admit a sample of one
    })
    void theFloorIsTheSmallestSampleWhoseNoiseFitsInsideTheMargin(
            double margin, int figures, int expected) {
        assertThat(PublishedUncertainty.smallestSampleWhereNoiseStaysWithin(margin, figures))
                .isEqualTo(expected);
        // The defining property, asserted rather than assumed: at the floor the claim's
        // noise fits inside the margin, and one observation below it does not.
        assertThat(figures * 100.0 / expected).isLessThan(margin);
        if (expected > 2) {
            assertThat(figures * 100.0 / (expected - 1)).isGreaterThanOrEqualTo(margin);
        }
    }

    @Test
    @DisplayName("the published floors follow from the published uncertainty")
    void theFloorsAreDerivedFromTheConstant() {
        assertThat(PublishedUncertainty.HEADLINE_SENSITIVITY_POINTS).isEqualTo(8.7);
        assertThat(PublishedUncertainty.displayFloor()).isEqualTo(12);
        assertThat(PublishedUncertainty.rankingFloor()).isEqualTo(23);
    }

    @Test
    @DisplayName("ranking needs a bigger sample than showing, because it compares two figures")
    void rankingIsStricterThanDisplay() {
        // Not a preference: a comparison rests on two percentages and carries both
        // their noise, so it needs roughly twice the sample. If this ever inverts, the
        // leaderboard is ranking rows it would refuse to print a number for.
        assertThat(PublishedUncertainty.rankingFloor())
                .isGreaterThan(PublishedUncertainty.displayFloor());
        assertThat(PublishedUncertainty.rankingFloor())
                .isCloseTo(2 * PublishedUncertainty.displayFloor(), within(2));
    }

    @Test
    @DisplayName("a zero or negative margin is a bug, not a floor of infinity")
    void refusesAnImpossibleMargin() {
        assertThatThrownBy(() ->
                PublishedUncertainty.smallestSampleWhereNoiseStaysWithin(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                PublishedUncertainty.smallestSampleWhereNoiseStaysWithin(8.7, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
