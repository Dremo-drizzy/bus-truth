package io.github.dremo_drizzy.bustruth.api.live;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.github.dremo_drizzy.bustruth.api.ApiTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The live endpoint against a real PostgreSQL 18.
 *
 * <p>Its whole job is one SQL statement, and {@code distinct on} is Postgres-only, so
 * testing it on another engine would test something else entirely.
 *
 * <p>The three states the map has to tell apart get a test each: buses on the map, no
 * buses running, and no data arriving. The second and third both return zero features,
 * which is exactly why they need separating.
 */
@SpringBootTest(properties = {
        "POSTGRES_PASSWORD=unused",
        // The api never migrates; the test creates the table the loader owns.
        "spring.flyway.enabled=false"
})
@Import(ApiTestcontainers.class)
class LiveVehiclesIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcClient jdbcClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // The loader owns this schema; the api only reads it. Kept to the columns the
        // query touches so the test fails if the query starts needing more.
        jdbcClient.sql("""
                create table if not exists raw_vehicle_positions (
                    vehicle_id text not null,
                    vehicle_timestamp timestamptz not null,
                    route_id text,
                    trip_id text,
                    latitude real,
                    longitude real,
                    bearing real,
                    first_feed_timestamp timestamptz not null,
                    primary key (vehicle_id, vehicle_timestamp))
                """).update();
        jdbcClient.sql("truncate table raw_vehicle_positions").update();
    }

    @Test
    void returnsOneFeaturePerVehicleUsingItsNewestPosition() throws Exception {
        insert("2531", minutesAgo(5), 44.6492f, -63.57545f, 252.0f);
        insert("2531", minutesAgo(1), 44.6500f, -63.57600f, 250.0f);   // newer, wins
        insert("3272", minutesAgo(2), 44.7000f, -63.60000f, null);

        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.vehicle_count").value(2))
                .andExpect(jsonPath("$.features.length()").value(2))
                // distinct on keeps the newest row per vehicle, not an arbitrary one.
                .andExpect(jsonPath("$.features[?(@.properties.vehicle_id == '2531')]"
                        + ".geometry.coordinates[1]").value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.closeTo(44.65, 0.01))));
    }

    @Test
    void coordinatesAreLongitudeThenLatitude() throws Exception {
        insert("2531", minutesAgo(1), 44.6492f, -63.57545f, 252.0f);

        mockMvc.perform(get("/api/vehicles/live"))
                // Halifax is west of Greenwich and north of the equator: the first
                // number must be the negative one. Reversed, the map still renders,
                // with the buses in Somalia.
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]")
                        .value(org.hamcrest.Matchers.closeTo(-63.575, 0.01)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]")
                        .value(org.hamcrest.Matchers.closeTo(44.649, 0.01)));
    }

    @Test
    void excludesVehiclesOlderThanTheWindow() throws Exception {
        insert("2531", minutesAgo(1), 44.6492f, -63.57545f, null);
        insert("9999", minutesAgo(30), 44.6492f, -63.57545f, null);

        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(jsonPath("$.vehicle_count").value(1))
                .andExpect(jsonPath("$.features[0].properties.vehicle_id").value("2531"));
    }

    @Test
    void eachFeatureCarriesItsOwnTimestampSoStaleDotsCanBeFaded() throws Exception {
        insert("2531", minutesAgo(1), 44.6492f, -63.57545f, null);
        insert("3163", minutesAgo(9), 44.7000f, -63.60000f, null);

        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(jsonPath("$.features[0].properties.vehicle_timestamp").exists())
                .andExpect(jsonPath("$.features[1].properties.vehicle_timestamp").exists());
    }

    @Test
    void stateTwoNoBusesRunningButDataIsRecent() throws Exception {
        // Everything is outside the ten-minute window but only just: the pipeline is
        // healthy, the buses have stopped for the night.
        insert("2531", minutesAgo(12), 44.6492f, -63.57545f, null);

        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_count").value(0))
                .andExpect(jsonPath("$.features").isEmpty())
                // Recent, so the page can say "no buses running" rather than "down".
                .andExpect(jsonPath("$.newest_position_at").exists());
    }

    @Test
    void stateThreeNoDataAtAll() throws Exception {
        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle_count").value(0))
                // Null newest_position_at is the strongest possible "no data": the
                // table is empty. With stale data it would be an old timestamp, and
                // the page compares it against now().
                .andExpect(jsonPath("$.newest_position_at").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void newestPositionIgnoresTheWindowSoStalenessIsVisible() throws Exception {
        Instant stale = minutesAgo(45);
        insert("2531", stale, 44.6492f, -63.57545f, null);

        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(jsonPath("$.vehicle_count").value(0))
                // Read from the whole table, not the windowed query — otherwise this
                // and an empty table would be indistinguishable.
                .andExpect(jsonPath("$.newest_position_at")
                        .value(org.hamcrest.Matchers.startsWith(stale.toString().substring(0, 16))));
    }

    @Test
    void generatedAtAndVehicleCountAreAlwaysPresent() throws Exception {
        mockMvc.perform(get("/api/vehicles/live"))
                .andExpect(jsonPath("$.generated_at").exists())
                .andExpect(jsonPath("$.vehicle_count").exists());
    }

    private void insert(String vehicleId, Instant at, Float lat, Float lon, Float bearing) {
        jdbcClient.sql("""
                        insert into raw_vehicle_positions
                            (vehicle_id, vehicle_timestamp, route_id, trip_id,
                             latitude, longitude, bearing, first_feed_timestamp)
                        values (:id, :at, '320', '4653691', :lat, :lon, :bearing, :at)
                        """)
                .param("id", vehicleId)
                .param("at", at.atOffset(ZoneOffset.UTC))
                .param("lat", lat)
                .param("lon", lon)
                .param("bearing", bearing)
                .update();
    }

    private static Instant minutesAgo(int minutes) {
        return Instant.now().minus(Duration.ofMinutes(minutes));
    }
}
