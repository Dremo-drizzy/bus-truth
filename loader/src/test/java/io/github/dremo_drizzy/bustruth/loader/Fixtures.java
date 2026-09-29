package io.github.dremo_drizzy.bustruth.loader;

import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

/**
 * Real messages captured from hfx.vehicle-positions, one JSON object per line.
 *
 * <p>Real traffic rather than hand-written JSON, so the tests exercise what the
 * collector actually publishes — including the nulls the agency leaves out.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static List<String> jsonLines() throws IOException {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/vehicle-positions.jsonl")) {
            if (in == null) {
                throw new IllegalStateException("missing fixture /fixtures/vehicle-positions.jsonl");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    /**
     * Deserializes with exactly the configuration application.yml gives the consumer,
     * so a test failure here means the running loader would fail the same way.
     */
    public static JacksonJsonDeserializer<VehiclePosition> configuredDeserializer() {
        JacksonJsonDeserializer<VehiclePosition> deserializer = new JacksonJsonDeserializer<>();
        deserializer.configure(java.util.Map.of(
                JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
                "io.github.dremo_drizzy.bustruth.common.VehiclePosition",
                JacksonJsonDeserializer.TRUSTED_PACKAGES,
                "io.github.dremo_drizzy.bustruth.common"), false);
        return deserializer;
    }

    public static List<VehiclePosition> positions() throws IOException {
        try (JacksonJsonDeserializer<VehiclePosition> deserializer = configuredDeserializer()) {
            return jsonLines().stream()
                    .map(line -> deserializer.deserialize("hfx.vehicle-positions",
                            line.getBytes(StandardCharsets.UTF_8)))
                    .toList();
        }
    }
}
