package io.github.dremo_drizzy.bustruth.collector.feed;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.archive.RawArchiver;
import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import io.github.dremo_drizzy.bustruth.collector.mapper.ServiceAlertMapper;
import io.github.dremo_drizzy.bustruth.common.ServiceAlert;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the service alerts feed. */
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
public class ServiceAlertPoller extends FeedPoller<ServiceAlert> {

    private final CollectorProperties.Feed feed;
    private final ServiceAlertMapper mapper;

    public ServiceAlertPoller(HttpClient httpClient,
                              RawArchiver archiver,
                                CollectorProperties properties,
                              ServiceAlertMapper mapper) {
        super(httpClient, archiver, properties.httpTimeout());
        this.feed = properties.alerts();
        this.mapper = mapper;
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

    @Override
    protected List<ServiceAlert> map(FeedMessage message) {
        return mapper.map(message);
    }

    /**
     * Deliberately does nothing.
     *
     * <p>Alerts are archived and nothing queries them. The archive costs almost nothing
     * (content changed on 0% of 180 consecutive polls) and keeps the option open.
     */
    @Override
    protected void store(List<ServiceAlert> records) {
        // Archived by FeedPoller before this point; nothing reads a database row.
    }
}
