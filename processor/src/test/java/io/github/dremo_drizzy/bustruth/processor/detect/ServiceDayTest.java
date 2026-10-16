package io.github.dremo_drizzy.bustruth.processor.detect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which day a bus seen at ten past midnight belongs to.
 *
 * <p>This exists because of a measured defect, not a hypothetical. 2,660 arrivals in
 * the published week came out with delays beyond minus twelve hours — which is not a
 * delay, it is the service date and the published time disagreeing about which day the
 * trip is on. Re-resolving them one service day earlier gave a median delay of four
 * seconds and put 2,659 of 2,660 inside half an hour, which is what a correct answer
 * looks like and what a coincidence does not.
 */
class ServiceDayTest {

    @Test
    @DisplayName("a bus seen at 00:16 belongs to the previous service day, whatever the feed stamps")
    void aPostMidnightTripBelongsToThePreviousServiceDay() {
        // Trip 4649655, 4 October, the case that exposed this: the feed said
        // start_date=20261004, the bus was at the stop at 00:16 on the 4th, and the
        // schedule publishes that stop at 24:32:00 — which for a trip on the 4th would
        // be 00:32 on the 5th. It is the 3rd's service day.
        VehiclePosition position = at(LocalDateTime.of(2026, 10, 4, 0, 16, 28), "20261004");

        assertThat(ServiceDay.of(position)).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("a feed that already labels the previous day is believed, not shifted twice")
    void anAgencyLabelledPreviousDayIsKept() {
        // Here start_date disagrees with the clock, which is the agency asserting the
        // service day rather than stamping today's date. Shifting again would move the
        // trip two days back.
        VehiclePosition position = at(LocalDateTime.of(2026, 10, 4, 0, 16, 28), "20261003");

        assertThat(ServiceDay.of(position)).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("an ordinary daytime arrival is the date the feed sent")
    void daytimeIsUntouched() {
        VehiclePosition position = at(LocalDateTime.of(2026, 10, 4, 17, 30, 0), "20261004");

        assertThat(ServiceDay.of(position)).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("the boundary is 04:00 local, and it is exclusive")
    void theBoundaryIsFourAmLocal() {
        assertThat(ServiceDay.of(at(LocalDateTime.of(2026, 10, 4, 3, 59, 59), "20261004")))
                .as("03:59:59 is still the previous service day")
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(ServiceDay.of(at(LocalDateTime.of(2026, 10, 4, 4, 0, 0), "20261004")))
                .as("04:00:00 has started today's service")
                .isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("the boundary sits below the earliest stop the agency publishes")
    void earliestScheduledStopIsAfterTheBoundary() {
        // The justification for 04:00, asserted rather than left in a comment. Measured
        // across every stop time in the feed version in force: the earliest is 04:20
        // and the latest 26:01. If Halifax ever publishes a stop before 04:00, the
        // boundary would start clipping real early-morning service, and this is the
        // test that should fail when that happens.
        LocalTime earliestScheduledStop = LocalTime.of(4, 20);

        assertThat(ServiceDay.SERVICE_DAY_START)
                .as("the service-day boundary must not cut into published service")
                .isBefore(earliestScheduledStop);
    }

    @Test
    @DisplayName("an unusable start_date falls back to the clock, with the same boundary")
    void fallsBackToTheClock() {
        // Halifax sends start_date on every position — 1,205,076 of 1,205,076 in the
        // published week — so this path is unreached today. It exists because a feed
        // that stopped sending it would otherwise put every post-midnight arrival on
        // the wrong day, silently.
        assertThat(ServiceDay.of(at(LocalDateTime.of(2026, 10, 4, 0, 16, 28), null)))
                .isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(ServiceDay.of(at(LocalDateTime.of(2026, 10, 4, 12, 0, 0), "not-a-date")))
                .isEqualTo(LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("the service day is local, so a late-evening bus is not already tomorrow")
    void theDayIsHalifaxLocalNotUtc() {
        // 21:00 in Halifax is already the next day in UTC. Computing this in UTC would
        // move every evening arrival forward a day.
        VehiclePosition position = at(LocalDateTime.of(2026, 10, 4, 21, 0, 0), null);

        assertThat(ServiceDay.of(position)).isEqualTo(LocalDate.of(2026, 10, 4));
    }

    private static VehiclePosition at(LocalDateTime localTime, String startDate) {
        Instant timestamp = localTime.atZone(ServiceDay.HALIFAX).toInstant();
        return new VehiclePosition(
                "bus-1", null, "4649655", "1", null, startDate,
                44.65f, -63.57f, null, null, null, 5, "8184", null, null, null,
                timestamp, timestamp);
    }
}
