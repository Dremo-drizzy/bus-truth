package io.github.dremo_drizzy.bustruth.collector;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

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
}
