package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the service alerts feed, and archives it without modelling it.
 *
 * <p>There is no {@code ServiceAlert} record and no mapper any more. The feed's content
 * changed on 0 of 180 consecutive polls, nothing queries it, and nothing planned will:
 * decoding every snapshot into typed records only to drop them was work done for no
 * reader. The raw bytes still reach the archive on every poll, through the same template
 * method as the other two feeds, so the history stays unbroken and a future consumer can
 * be built from the archive rather than from a live feed it was not there to watch.
 *
 * <p>The type parameter is the protobuf's own {@link FeedEntity} rather than a domain
 * record, which is the honest statement of what this feed is here: archived, not
 * modelled. The record is in git if it is ever wanted back.
 */
@Component
// Polling is on unless switched off. A test that boots this application does not
// want three schedulers fetching live data and writing rows underneath its
// assertions, and an operator may want the service up without it collecting.
// Two switches, both default-on, and either one off stops this poller.
// polling-enabled is the global one, off in every test that boots the context.
// alerts.enabled is per feed, because the host and the laptop do not collect the
// same feeds: archiving trip updates on the host would fill its disk in about
// five months, so that feed stays on the laptop (ADR-018). Listing both names in
// one annotation means every one of them must be absent or true.
@ConditionalOnProperty(
        name = {"bustruth.collector.polling-enabled",
                "bustruth.collector.alerts.enabled"},
        matchIfMissing = true)
public class ServiceAlertPoller extends FeedPoller<FeedEntity> {

    private final CollectorProperties.Feed feed;

    public ServiceAlertPoller(HttpClient httpClient,
                              RawArchiver archiver,
                              CollectorProperties properties) {
        super(httpClient, archiver, properties.httpTimeout());
        this.feed = properties.alerts();
    }

    @Scheduled(fixedRateString = "${bustruth.collector.poll-interval}")
    public void poll() {
        pollOnce();
    }

    @Override
    protected String feedName() {
        return "alerts";
    }

    @Override
    protected URI url() {
        return feed.url();
    }

    /**
     * Hands the decoded entities straight back, unmapped.
     *
     * <p>Not an empty list: {@code pollOnce()} logs this count, and a log that said
     * "mapped 0 records" of a snapshot that carried twelve alerts would be a log that
     * lies. The count is real, the modelling is simply absent.
     */
    @Override
    protected List<FeedEntity> map(FeedMessage message) {
        return message.getEntityList();
    }

    /**
     * Deliberately does nothing.
     *
     * <p>Alerts are archived and nothing queries them. The archive costs almost nothing
     * (content changed on 0% of 180 consecutive polls) and keeps the option open.
     */
    @Override
    protected void store(List<FeedEntity> records) {
        // Archived by FeedPoller before this point; nothing reads a database row.
    }
}
