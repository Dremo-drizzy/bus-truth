package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * One poll of one GTFS-Realtime feed: fetch, archive, decode, skip-if-unchanged,
 * map, publish.
 *
 * <p>This is the template method pattern. {@link #pollOnce()} is {@code final}, so
 * the sequence is written once and a subclass cannot reorder it, skip the archiving
 * step, or publish before the bytes are safely on disk. Subclasses fill in only what
 * genuinely differs per feed: where to fetch from, what to call it, which topic it
 * goes to, how to turn the protobuf into records, and what key each record uses.
 *
 * @param <T> the domain record this feed produces
 */
public abstract class FeedPoller<T> {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);

    private final HttpClient httpClient;
    private final RawArchiver archiver;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Duration httpTimeout;

    /**
     * Content hash of the last snapshot published, so an unchanged feed is not
     * republished.
     *
     * <p>A hash of the entities, NOT the feed header timestamp. The header advances
     * on every poll even when nothing has changed, so comparing headers would skip
     * nothing at all — measured in Stage 1, where byte-level dedupe in the saver
     * almost never fired for exactly this reason.
     *
     * <p>Deliberately in memory only. After a restart each feed publishes once more,
     * even if nothing changed — which is safe ONLY because the loader dedupes on
     * insert (ADR-004). If that dedupe is ever removed, this becomes a source of
     * duplicate rows after every restart.
     *
     * <p>volatile because the scheduler may run this poller on a different thread
     * each time.
     */
    private volatile String lastPublishedContentHash = null;

    protected FeedPoller(HttpClient httpClient,
                         RawArchiver archiver,
                         KafkaTemplate<String, Object> kafkaTemplate,
                         Duration httpTimeout) {
        this.httpClient = httpClient;
        this.archiver = archiver;
        this.kafkaTemplate = kafkaTemplate;
        this.httpTimeout = httpTimeout;
    }

    /**
     * The fixed sequence. Never throws: a failure in one feed must not stop the
     * others, and must not kill the scheduled task, which Spring would otherwise
     * stop re-running.
     *
     * <p>There is no retry here on purpose. The poll interval is the retry: a failed
     * fetch is followed 20 seconds later by another one, which returns a fresher
     * snapshot than a retry would. An explicit retry plus backoff could also push a
     * cycle past the next scheduled run.
     */
    public final void pollOnce() {
        try {
            byte[] raw = fetch();

            // Archive first: the bytes are the source of truth, so they reach disk
            // before any code that could have a bug in it touches them.
            archiver.archive(feedName(), raw);

            FeedMessage feed = FeedMessage.parseFrom(raw);
            String contentHash = FeedContent.hash(feed);
            if (contentHash.equals(lastPublishedContentHash)) {
                log.debug("{}: content unchanged, nothing published", feedName());
                return;
            }

            List<T> records = map(feed);
            for (T record : records) {
                kafkaTemplate.send(topic(), keyOf(record), record);
            }
            lastPublishedContentHash = contentHash;
            log.info("{}: published {} records to {} (feed timestamp {})",
                    feedName(), records.size(), topic(), feed.getHeader().getTimestamp());

        } catch (Exception failure) {
            // Broad on purpose: HTTP failures, malformed protobuf, a full disk, a
            // broker that is down. All of them mean "skip this poll", never "stop".
            log.error("{}: poll failed: {}", feedName(), failure.toString());
        }
    }

    private byte[] fetch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(url())
                .timeout(httpTimeout)
                .header("User-Agent", "bus-truth-collector/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url());
        }
        return response.body();
    }

    /** Folder name in the archive, e.g. "vehicle-positions". */
    protected abstract String feedName();

    /** Where this feed is published by the agency. */
    protected abstract URI url();

    /** Kafka topic the records go to. */
    protected abstract String topic();

    /** Turns one decoded snapshot into domain records. The only per-feed logic. */
    protected abstract List<T> map(FeedMessage feed);

    /**
     * The Kafka message key for one record, or null when the feed has no natural key.
     * The key decides the partition, and Kafka only guarantees ordering within a
     * partition — so this is what keeps one vehicle's or one trip's messages in
     * order (ADR-003).
     */
    protected abstract String keyOf(T record);
}
