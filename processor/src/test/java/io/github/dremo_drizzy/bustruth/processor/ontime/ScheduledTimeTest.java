package io.github.dremo_drizzy.bustruth.processor.ontime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The published time means an instant, and these fix which one.
 *
 * <p>Each test asserts the local reading a passenger would see on the timetable,
 * because that is the thing being promised. Asserting UTC instants instead would pass
 * just as readily against the wrong basis and prove nothing.
 */
class ScheduledTimeTest {

    @Test
    @DisplayName("an ordinary time is the time the timetable prints")
    void ordinaryTime() {
        Instant scheduled = ScheduledTime.toInstant(LocalDate.of(2026, 10, 14), "08:15:00");

        assertThat(localReading(scheduled)).isEqualTo(LocalDateTime.of(2026, 10, 14, 8, 15));
    }

    @Test
    @DisplayName("24:40:00 is 00:40 the next morning, still on the service date given")
    void pastMidnight() {
        Instant scheduled = ScheduledTime.toInstant(LocalDate.of(2026, 10, 14), "24:40:00");

        // The service date stays the 14th — the bus belongs to the 14th's service day —
        // while the clock on the wall says the 15th. Collapsing the two is how a
        // late-night trip ends up compared against the wrong day's schedule.
        assertThat(localReading(scheduled)).isEqualTo(LocalDateTime.of(2026, 10, 15, 0, 40));
    }

    @Test
    @DisplayName("25:10:00 parses, though no time type would accept it")
    void wellPastMidnight() {
        Instant scheduled = ScheduledTime.toInstant(LocalDate.of(2026, 10, 14), "25:10:00");

        assertThat(localReading(scheduled)).isEqualTo(LocalDateTime.of(2026, 10, 15, 1, 10));
    }

    @Test
    @DisplayName("on the 23-hour day the morning reads as printed, not an hour later")
    void springForward() {
        // Halifax moves to ADT at 02:00 on 2026-03-08. A naive midnight basis puts this
        // bus at 09:00 local and would record every bus that morning as an hour early.
        Instant scheduled = ScheduledTime.toInstant(LocalDate.of(2026, 3, 8), "08:00:00");

        assertThat(localReading(scheduled)).isEqualTo(LocalDateTime.of(2026, 3, 8, 8, 0));
        assertThat(scheduled).isEqualTo(Instant.parse("2026-03-08T11:00:00Z"));
    }

    @Test
    @DisplayName("on the 25-hour day the morning reads as printed, not an hour earlier")
    void fallBack() {
        // ADT ends at 02:00 on 2026-11-01, making that service day 25 hours long.
        Instant scheduled = ScheduledTime.toInstant(LocalDate.of(2026, 11, 1), "08:00:00");

        assertThat(localReading(scheduled)).isEqualTo(LocalDateTime.of(2026, 11, 1, 8, 0));
        assertThat(scheduled).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
    }

    @Test
    @DisplayName("the clock-change days are the only ones midnight would have got wrong")
    void midnightBasisDiffersOnlyAtAClockChange() {
        // The point of the noon anchor, stated as a test: a midnight basis is correct
        // on 363 days a year, which is precisely what would have let it ship.
        LocalDate springForward = LocalDate.of(2026, 3, 8);
        LocalDate fallBack = LocalDate.of(2026, 11, 1);

        for (LocalDate day = LocalDate.of(2026, 1, 1);
                day.isBefore(LocalDate.of(2027, 1, 1));
                day = day.plusDays(1)) {
            Instant fromNoon = ScheduledTime.startOfServiceDay(day);
            Instant fromMidnight = day.atStartOfDay(ScheduledTime.HALIFAX).toInstant();

            if (day.equals(springForward) || day.equals(fallBack)) {
                assertThat(Duration.between(fromMidnight, fromNoon).abs())
                        .as("%s is a clock-change day", day)
                        .isEqualTo(Duration.ofHours(1));
            } else {
                assertThat(fromNoon).as("%s is an ordinary day", day).isEqualTo(fromMidnight);
            }
        }
    }

    @Test
    @DisplayName("the repeated hour is told apart by elapsed time, not by its reading")
    void theHourThatHappensTwice() {
        // 01:30 local happens twice on 2026-11-01, once in ADT and once in AST. Two
        // different buses on the night of the 31st are published for the two of them,
        // an hour apart in elapsed time, and the local clock cannot tell them apart.
        LocalDate serviceDate = LocalDate.of(2026, 10, 31);
        Instant firstPass = ScheduledTime.toInstant(serviceDate, "25:30:00");
        Instant secondPass = ScheduledTime.toInstant(serviceDate, "26:30:00");

        assertThat(localReading(firstPass)).isEqualTo(LocalDateTime.of(2026, 11, 1, 1, 30));
        assertThat(localReading(secondPass)).isEqualTo(LocalDateTime.of(2026, 11, 1, 1, 30));
        // Same reading, different instants — which is why the schedule's offset is
        // kept as elapsed time and resolved once, rather than parsed as a local time
        // and handed to the zone to disambiguate.
        assertThat(Duration.between(firstPass, secondPass)).isEqualTo(Duration.ofHours(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "08:15", "8am", "08:15:00:00", "aa:bb:cc", "   "})
    @DisplayName("unusable text yields nothing rather than a guess")
    void unusableText(String gtfsTime) {
        assertThat(ScheduledTime.toInstant(LocalDate.of(2026, 10, 14), gtfsTime)).isNull();
    }

    @Test
    void nullTextYieldsNothing() {
        assertThat(ScheduledTime.toInstant(LocalDate.of(2026, 10, 14), null)).isNull();
    }

    /** What a passenger reading the timetable in Halifax would see. */
    private static LocalDateTime localReading(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ScheduledTime.HALIFAX).toLocalDateTime();
    }
}
