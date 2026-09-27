package io.github.dremo_drizzy.bustruth.collector.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * The behaviour of the template method itself: what happens when a feed misbehaves.
 *
 * <p>These are the guarantees the design promises and that nothing else tests — one
 * feed's failure must not stop the others, and the raw bytes must be archived even
 * when the rest of the poll goes wrong.
 */
class FeedPollerTest {

    @TempDir
    Path archiveRoot;

    private HttpClient httpClient;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private TestPoller poller;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        poller = new TestPoller(httpClient, new RawArchiver(archiveRoot), kafkaTemplate);
    }

    @Test
    void aFailedFetchIsSwallowedSoTheScheduledTaskSurvives() throws Exception {
        when(httpClient.send(any(), any())).thenThrow(new IOException("connection reset"));

        // If this threw, Spring's scheduler would log an unexpected error, and the
        // other two feeds would be running on borrowed luck.
        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void anHttpErrorPublishesNothing() throws Exception {
        givenResponse(503, new byte[0]);

        assertThatCode(poller::pollOnce).doesNotThrowAnyException();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void malformedBytesAreStillArchivedBeforeTheyFailToDecode() throws Exception {
        givenResponse(200, "this is not protobuf".getBytes());

        assertThatCode(poller::pollOnce).doesNotThrowAnyException();

        // The point of archiving before decoding (ADR-005): bytes we cannot parse
        // today are kept, so a fix to the parser can be applied to them later.
        assertThat(archivedFiles()).hasSize(1);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void unchangedContentIsPublishedOnceAndThenSkipped() throws Exception {
        byte[] snapshot = Fixtures.rawBytes("alerts");
        int entities = FeedMessage.parseFrom(snapshot).getEntityCount();
        givenResponse(200, snapshot);

        poller.pollOnce();
        poller.pollOnce();
        poller.pollOnce();

        // Three polls of identical content, one round of publishing: the content hash
        // did its job. The header timestamp check it replaced would have published
        // all three times.
        verify(kafkaTemplate, times(entities)).send(anyString(), any(), any());
        // Every fetch is archived even when nothing is published — and all three
        // survive, because same-second snapshots get a suffix instead of
        // overwriting each other.
        assertThat(archivedFiles()).hasSize(3);
    }

    private void givenResponse(int statusCode, byte[] body) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.<byte[]>send(any(HttpRequest.class), any())).thenReturn(response);
    }

    private List<Path> archivedFiles() throws IOException {
        try (var found = Files.walk(archiveRoot)) {
            return found.filter(path -> path.getFileName().toString().endsWith(".pb.gz")).toList();
        }
    }

    /** A poller over a feed of entity ids, so the test exercises only the template. */
    private static final class TestPoller extends FeedPoller<String> {

        private TestPoller(HttpClient httpClient,
                           RawArchiver archiver,
                           KafkaTemplate<String, Object> kafkaTemplate) {
            super(httpClient, archiver, kafkaTemplate, Duration.ofSeconds(5));
        }

        @Override
        protected String feedName() {
            return "test-feed";
        }

        @Override
        protected URI url() {
            return URI.create("https://example.invalid/feed.pb");
        }

        @Override
        protected String topic() {
            return "test.topic";
        }

        @Override
        protected List<String> map(FeedMessage feed) {
            return feed.getEntityList().stream().map(FeedEntity::getId).toList();
        }

        @Override
        protected String keyOf(String record) {
            return record;
        }
    }
}
