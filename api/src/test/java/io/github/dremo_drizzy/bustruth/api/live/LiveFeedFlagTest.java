package io.github.dremo_drizzy.bustruth.api.live;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The switch that says whether this deployment has a live feed at all.
 *
 * <p>A unit test rather than another integration test, because the claim being tested
 * is about what does NOT happen: with the feed disabled, the database is never asked.
 * Mocked repositories can prove that directly — {@code verifyNoInteractions} — where a
 * real database could only show that nothing went wrong this time.
 *
 * <p>The query itself is covered against real PostgreSQL in {@link LiveVehiclesIT}.
 */
class LiveFeedFlagTest {

    private final LivePositionRepository positions = mock(LivePositionRepository.class);
    private final MapSnapshotRepository snapshots = mock(MapSnapshotRepository.class);

    private MockMvc mockMvcWithLiveFeed(boolean enabled) {
        return MockMvcBuilders
                .standaloneSetup(new LiveVehiclesController(positions, snapshots, enabled))
                .build();
    }

    @Test
    @DisplayName("feed disabled: 503 problem document naming the snapshot, database untouched")
    void disabledAnswers503AndNeverTouchesTheDatabase() throws Exception {
        mockMvcWithLiveFeed(false).perform(get("/api/vehicles/live"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("No live feed on this deployment"))
                .andExpect(jsonPath("$.detail").value(containsString(
                        "serves a historical snapshot and has no live feed")))
                .andExpect(jsonPath("$.detail").value(containsString(
                        "not the same as there being no buses")))
                .andExpect(jsonPath("$.snapshot").value("/api/vehicles/snapshot"))
                // The body is a problem document, never a feature collection: an empty
                // list here is the false zero this switch exists to prevent.
                .andExpect(jsonPath("$.features").doesNotExist());

        verifyNoInteractions(positions, snapshots);
    }

    @Test
    @DisplayName("feed enabled: 200 GeoJSON from the repository, as before")
    void enabledServesLivePositions() throws Exception {
        Instant at = Instant.parse("2026-10-06T20:08:00Z");
        when(positions.latestPerVehicle()).thenReturn(List.of(
                new LivePosition("2531", "1", "trip-1", 44.6492f, -63.57545f, 252.0f, at)));
        when(positions.newestPositionAt()).thenReturn(Optional.of(at));

        mockMvcWithLiveFeed(true).perform(get("/api/vehicles/live"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.vehicle_id").value("2531"));

        verify(positions).latestPerVehicle();
        verifyNoInteractions(snapshots);
    }
}
