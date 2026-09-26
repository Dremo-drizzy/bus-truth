package io.github.dremo_drizzy.bustruth.collector.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import io.github.dremo_drizzy.bustruth.common.StopTimeUpdate;
import io.github.dremo_drizzy.bustruth.common.TripUpdate;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TripUpdateMapperTest {

    private static FeedMessage feed;
    private static List<TripUpdate> mapped;

    @BeforeAll
    static void mapTheFixture() throws IOException {
        feed = Fixtures.feed("trip-updates");
        mapped = new TripUpdateMapper().map(feed);
    }

    @Test
    void mapsEveryTripUpdateInTheSnapshot() {
        long tripUpdatesInFeed = feed.getEntityList().stream()
                .filter(entity -> entity.hasTripUpdate())
                .count();

        assertThat(tripUpdatesInFeed).isPositive();
        assertThat(mapped).hasSize((int) tripUpdatesInFeed);
    }

    @Test
    void resolvesATripIdForEveryRecord() {
        // The whole point of the fallback chain: this snapshot contains entities with
        // no trip.trip_id, and none of them may be dropped.
        assertThat(mapped).allSatisfy(update ->
                assertThat(update.tripId()).isNotNull().isNotBlank());
    }

    @Test
    void recoversTheTripIdWhenTheFeedOmitsItFromTheTripDescriptor() {
        List<TripUpdate> fromModifiedTrips = mapped.stream().filter(TripUpdate::modifiedTrip).toList();

        assertThat(fromModifiedTrips)
                .describedAs("fixture should contain modified trips, otherwise this test proves nothing")
                .isNotEmpty();
        assertThat(fromModifiedTrips).allSatisfy(update ->
                assertThat(update.tripId()).isNotNull());
    }

    @Test
    void keepsBothPredictedAndScheduledTimesSoDelayCanBeDerived() {
        StopTimeUpdate stop = mapped.stream()
                .flatMap(update -> update.stopTimeUpdates().stream())
                .filter(s -> s.arrivalTime() != null)
                .findFirst()
                .orElseThrow();

        assertThat(stop.arrivalTime()).isNotNull();
        assertThat(stop.arrivalScheduledTime()).isNotNull();
        assertThat(stop.stopId()).isNotNull();
    }

    @Test
    void leavesAbsentFieldsNullRatherThanEmptyStrings() {
        assertThat(mapped).allSatisfy(update -> {
            assertThat(update.routeId()).satisfiesAnyOf(
                    routeId -> assertThat(routeId).isNull(),
                    routeId -> assertThat(routeId).isNotBlank());
            assertThat(update.vehicleId()).satisfiesAnyOf(
                    vehicleId -> assertThat(vehicleId).isNull(),
                    vehicleId -> assertThat(vehicleId).isNotBlank());
        });
    }

    @Test
    void stampsEveryRecordWithTheFeedTimestamp() {
        assertThat(mapped).allSatisfy(update ->
                assertThat(update.feedTimestamp().getEpochSecond())
                        .isEqualTo(feed.getHeader().getTimestamp()));
    }

    @Test
    void stopTimeUpdatesCannotBeChangedThroughTheListPassedIn() {
        TripUpdate update = mapped.getFirst();
        assertThat(update.stopTimeUpdates().getClass().getName())
                .describedAs("List.copyOf returns an immutable list")
                .contains("Immutable");
    }
}
