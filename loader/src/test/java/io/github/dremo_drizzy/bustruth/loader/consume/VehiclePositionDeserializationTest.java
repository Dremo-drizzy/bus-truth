package io.github.dremo_drizzy.bustruth.loader.consume;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import io.github.dremo_drizzy.bustruth.loader.Fixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

/**
 * The deserializer configuration, which is where this service is most likely to
 * break. A wrong default type, a too-narrow trusted-packages list, or the wrong
 * deserializer class all present the same way in production: the loader runs, reports
 * healthy, and stores nothing.
 *
 * <p>No broker involved — this is the configuration from application.yml applied to
 * real captured bytes.
 */
class VehiclePositionDeserializationTest {

    @Test
    void everyCapturedMessageDeserializesIntoTheRecord() throws IOException {
        List<VehiclePosition> positions = Fixtures.positions();

        assertThat(positions).isNotEmpty();
        assertThat(positions).allSatisfy(position -> {
            assertThat(position).isNotNull();
            assertThat(position.vehicleId()).isNotBlank();
            assertThat(position.timestamp()).isNotNull();
            assertThat(position.feedTimestamp()).isNotNull();
        });
    }

    @Test
    void absentFieldsStayNullRatherThanBecomingZero() throws IOException {
        List<VehiclePosition> positions = Fixtures.positions();

        // The agency omits currentStatus on most vehicles and directionId on about
        // half. If JSON nulls arrived as 0 or "", every downstream metric would be
        // computed over invented data.
        assertThat(positions)
                .describedAs("captured traffic should contain at least one omitted field")
                .anySatisfy(position -> assertThat(position.currentStatus()).isNull());
    }

    @Test
    void timestampsComeBackAsInstantsNotStrings() throws IOException {
        VehiclePosition position = Fixtures.positions().getFirst();

        // The collector writes ISO-8601 strings; without Jackson's time support they
        // would fail to bind, or bind as text.
        assertThat(position.timestamp()).isInstanceOf(java.time.Instant.class);
        assertThat(position.timestamp()).isBefore(java.time.Instant.now());
    }

    @Test
    void floatsKeepTheFeedsOwnPrecision() throws IOException {
        VehiclePosition position = Fixtures.positions().getFirst();

        assertThat(position.latitude()).isNotNull();
        // Float, not Double: a widened value would print digits the agency never sent.
        assertThat(position.latitude()).isInstanceOf(Float.class);
        assertThat(position.latitude()).isBetween(43.0f, 46.0f);
    }

    @Test
    void malformedJsonFailsLoudlyRatherThanReturningAnEmptyRecord() {
        try (JacksonJsonDeserializer<VehiclePosition> deserializer = Fixtures.configuredDeserializer()) {
            byte[] notJson = "this is not json".getBytes(StandardCharsets.UTF_8);

            // The exception is what ErrorHandlingDeserializer converts into a null
            // payload plus a header, so the listener can skip and carry on.
            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> deserializer.deserialize("hfx.vehicle-positions", notJson))
                    .isInstanceOf(Exception.class);
        }
    }
}
