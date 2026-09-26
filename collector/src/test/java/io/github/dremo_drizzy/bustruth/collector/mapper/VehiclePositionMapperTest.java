package io.github.dremo_drizzy.bustruth.collector.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Waiting on {@link VehiclePositionMapper}, which Daniel writes.
 *
 * <p>Remove the {@code @Disabled} below once the mapper is implemented; the tests
 * describe what it has to do. They are disabled rather than deleted so the
 * expectations are agreed before the code is written, and disabled rather than
 * failing so CI stays green in the meantime.
 */
@Disabled("enable when VehiclePositionMapper is implemented")
class VehiclePositionMapperTest {

    private static FeedMessage feed;
    private static List<VehiclePosition> mapped;

    @BeforeAll
    static void mapTheFixture() throws IOException {
        feed = Fixtures.feed("vehicle-positions");
        mapped = new VehiclePositionMapper().map(feed);
    }

    @Test
    void mapsEveryVehicleInTheSnapshot() {
        long vehiclesInFeed = feed.getEntityList().stream().filter(entity -> entity.hasVehicle()).count();

        assertThat(vehiclesInFeed).isPositive();
        assertThat(mapped).hasSize((int) vehiclesInFeed);
    }

    @Test
    void usesTheVehicleDescriptorIdNotTheEntityId() {
        // In a real snapshot the entity id was "526" while vehicle.id was "2526".
        // The descriptor's id is Halifax's id for the bus; the entity id names the
        // record.
        String firstEntityId = feed.getEntityList().stream()
                .filter(entity -> entity.hasVehicle())
                .findFirst().orElseThrow().getId();
        String firstDescriptorId = feed.getEntityList().stream()
                .filter(entity -> entity.hasVehicle())
                .findFirst().orElseThrow().getVehicle().getVehicle().getId();

        assertThat(mapped.getFirst().vehicleId()).isEqualTo(firstDescriptorId);
        if (!firstEntityId.equals(firstDescriptorId)) {
            assertThat(mapped.getFirst().vehicleId()).isNotEqualTo(firstEntityId);
        }
    }

    @Test
    void alwaysHasTheFieldsEveryVehicleReports() {
        assertThat(mapped).allSatisfy(position -> {
            assertThat(position.vehicleId()).isNotNull().isNotBlank();
            assertThat(position.latitude()).isNotNull();
            assertThat(position.longitude()).isNotNull();
            assertThat(position.timestamp()).isNotNull();
        });
    }

    @Test
    void leavesUnreportedFieldsNullInsteadOfZero() {
        // currentStatus was present on 11 of 69 vehicles; speed on about half. A bus
        // that never reported its speed must not look like one standing still.
        assertThat(mapped)
                .describedAs("some field the agency omits should come through as null")
                .anySatisfy(position -> assertThat(position.currentStatus()).isNull());
    }

    @Test
    void stampsEveryRecordWithTheFeedTimestamp() {
        assertThat(mapped).allSatisfy(position ->
                assertThat(position.feedTimestamp().getEpochSecond())
                        .isEqualTo(feed.getHeader().getTimestamp()));
    }
}
