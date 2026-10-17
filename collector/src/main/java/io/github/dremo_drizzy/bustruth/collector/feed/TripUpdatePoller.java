package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.TripUpdateMapper;
import io.github.dremo_drizzy.bustruth.common.TripUpdate;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the trip updates feed. */
@Component
// Polling is on unless switched off. A test that boots this application does not
// want three schedulers fetching live data and writing rows underneath its
// assertions, and an operator may want the service up without it collecting.
// Two switches, both default-on, and either one off stops this poller.
// polling-enabled is the global one, off in every test that boots the context.
// trip-updates.enabled is per feed, because the host and the laptop do not collect the
// same feeds: archiving trip updates on the host would fill its disk in about
// five months, so that feed stays on the laptop (ADR-018). Listing both names in
// one annotation means every one of them must be absent or true.
@ConditionalOnProperty(
        name = {"bustruth.collector.polling-enabled",
                "bustruth.collector.trip-updates.enabled"},
        matchIfMissing = true)
public class TripUpdatePoller extends FeedPoller<TripUpdate> {

    private final CollectorProperties.Feed feed;
    private final TripUpdateMapper mapper;

    public TripUpdatePoller(HttpClient httpClient,
                            RawArchiver archiver,
                            CollectorProperties properties,
                            TripUpdateMapper mapper) {
        super(httpClient, archiver, properties.httpTimeout());
        this.feed = properties.tripUpdates();
        this.mapper = mapper;
    }

    @Scheduled(fixedRateString = "${bustruth.collector.poll-interval}")
    public void poll() {
        pollOnce();
    }

    @Override
    protected String feedName() {
        return "trip-updates";
    }

    @Override
    protected URI url() {
        return feed.url();
    }

    @Override
    protected List<TripUpdate> map(FeedMessage message) {
        return mapper.map(message);
    }

    /**
     * Deliberately does nothing.
     *
     * <p>Trip updates are archived but not loaded. The tables are designed and sized —
     * about 6.1M rows a day with the right dedupe key — and loading them only
     * pays off once prediction accuracy is built; see Stage R9 of the build brief.
     */
    @Override
    protected void store(List<TripUpdate> records) {
        // Archived by FeedPoller before this point; nothing reads a database row.
    }
}
