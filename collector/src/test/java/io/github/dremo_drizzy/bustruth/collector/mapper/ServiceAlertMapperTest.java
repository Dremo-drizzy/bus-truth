package io.github.dremo_drizzy.bustruth.collector.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import io.github.dremo_drizzy.bustruth.common.ServiceAlert;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceAlertMapperTest {

    private static FeedMessage feed;
    private static List<ServiceAlert> mapped;

    @BeforeAll
    static void mapTheFixture() throws IOException {
        feed = Fixtures.feed("alerts");
        mapped = new ServiceAlertMapper().map(feed);
    }

    @Test
    void mapsEveryAlertInTheSnapshot() {
        long alertsInFeed = feed.getEntityList().stream().filter(entity -> entity.hasAlert()).count();

        assertThat(alertsInFeed).isPositive();
        assertThat(mapped).hasSize((int) alertsInFeed);
    }

    @Test
    void picksTheEnglishTranslationForTexts() {
        assertThat(mapped).allSatisfy(alert -> {
            assertThat(alert.headerText()).isNotNull().isNotBlank();
            assertThat(alert.alertId()).isNotNull().isNotBlank();
        });
    }

    @Test
    void keepsEveryRouteStopPairRatherThanFlatteningThem() {
        ServiceAlert withSeveralEntities = mapped.stream()
                .filter(alert -> alert.informedEntities().size() > 1)
                .findFirst()
                .orElseThrow();

        assertThat(withSeveralEntities.informedEntities())
                .describedAs("each informed entity keeps its own route and stop together")
                .anySatisfy(entity -> assertThat(entity.routeId()).isNotNull());
    }

    @Test
    void keepsActivePeriodsAsSentIncludingTheFarFutureEndSentinel() {
        assertThat(mapped).anySatisfy(alert ->
                assertThat(alert.activePeriods()).isNotEmpty());

        assertThat(mapped)
                .flatExtracting(ServiceAlert::activePeriods)
                .filteredOn(period -> period.end() != null)
                .allSatisfy(period -> assertThat(period.end()).isAfter(period.start()));
    }

    @Test
    void stampsEveryRecordWithTheFeedTimestamp() {
        assertThat(mapped).allSatisfy(alert ->
                assertThat(alert.feedTimestamp().getEpochSecond())
                        .isEqualTo(feed.getHeader().getTimestamp()));
    }

    @Test
    void listsAreImmutable() {
        ServiceAlert alert = mapped.getFirst();
        assertThat(alert.informedEntities().getClass().getName()).contains("Immutable");
        assertThat(alert.activePeriods().getClass().getName()).contains("Immutable");
    }
}
