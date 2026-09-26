package io.github.dremo_drizzy.bustruth.collector.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FeedContentTest {

    @Test
    void sameContentWithALaterHeaderHashesTheSame() throws IOException {
        FeedMessage feed = Fixtures.feed("alerts");

        // Exactly what the real feed does every 20 seconds: identical entities, a
        // header timestamp 20 seconds later. This is the case the old header check
        // could never skip, and the reason alerts were republished ~2M times a day.
        FeedMessage twentySecondsLater = FeedMessage.newBuilder(feed)
                .setHeader(feed.getHeader().toBuilder()
                        .setTimestamp(feed.getHeader().getTimestamp() + 20))
                .build();

        assertThat(FeedContent.hash(twentySecondsLater))
                .isEqualTo(FeedContent.hash(feed));
    }

    @Test
    void changingAnEntityChangesTheHash() throws IOException {
        FeedMessage feed = Fixtures.feed("trip-updates");

        FeedMessage withOneFewerEntity = FeedMessage.newBuilder(feed)
                .removeEntity(0)
                .build();

        assertThat(FeedContent.hash(withOneFewerEntity))
                .isNotEqualTo(FeedContent.hash(feed));
    }

    @Test
    void differentFeedsHashDifferently() throws IOException {
        assertThat(FeedContent.hash(Fixtures.feed("alerts")))
                .isNotEqualTo(FeedContent.hash(Fixtures.feed("trip-updates")));
    }

    @Test
    void hashingIsStableAcrossCalls() throws IOException {
        FeedMessage feed = Fixtures.feed("vehicle-positions");

        assertThat(FeedContent.hash(feed)).isEqualTo(FeedContent.hash(feed));
    }
}
