package io.github.dremo_drizzy.bustruth.collector;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads real captured snapshots from src/test/resources/fixtures.
 *
 * <p>Real bytes rather than hand-built protobuf messages, because the point of these
 * tests is what Halifax actually sends — including the parts nobody would invent,
 * like trip updates carrying no trip_id (CLAUDE.md rule 7).
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] rawBytes(String feedName) throws IOException {
        String resource = "/fixtures/" + feedName + ".pb.gz";
        try (InputStream compressed = Fixtures.class.getResourceAsStream(resource)) {
            if (compressed == null) {
                throw new IllegalStateException("missing fixture " + resource);
            }
            try (GZIPInputStream gzip = new GZIPInputStream(compressed)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                gzip.transferTo(out);
                return out.toByteArray();
            }
        }
    }

    public static FeedMessage feed(String feedName) throws IOException {
        return FeedMessage.parseFrom(rawBytes(feedName));
    }

    /**
     * Real messages captured from the Kafka topic before the rescope, one JSON object
     * per line. Kept because they are real traffic — nulls and all — and because the
     * writer's tests are about what reaches the database, not about protobuf.
     *
     * <p>Parsed with Jackson directly rather than through a Kafka deserializer: the
     * collector no longer depends on spring-kafka.
     */
    public static List<VehiclePosition> positions() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/vehicle-positions.jsonl")) {
            if (in == null) {
                throw new IllegalStateException("missing fixture /fixtures/vehicle-positions.jsonl");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> mapper.readValue(line, VehiclePosition.class))
                    .toList();
        }
    }
}
