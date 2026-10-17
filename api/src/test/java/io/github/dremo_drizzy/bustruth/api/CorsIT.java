package io.github.dremo_drizzy.bustruth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The site and the API are two origins once they are hosted separately, so the browser
 * asks permission. This checks the answer.
 *
 * <p>Worth a running context rather than a unit test, because the thing being verified
 * is Spring's behaviour and not ours: that a mapping registered through
 * {@code addCorsMappings} produces the right header on a plain GET, answers a preflight
 * {@code OPTIONS} without reaching a controller, and refuses an origin that was not
 * configured. Assuming any of that is how a deployment ends up with a page reporting
 * the API as down when the API is fine.
 */
@SpringBootTest(properties = {
        "POSTGRES_PASSWORD=unused",
        "spring.flyway.enabled=false",
        // Deliberately with a trailing slash: this is how it gets typed by hand, and
        // the configuration has to cope rather than refuse every request.
        "bustruth.api.web-origin=https://bus-truth-web.onrender.com/",
})
@Import(ApiTestcontainers.class)
class CorsIT {

    private static final String SITE = "https://bus-truth-web.onrender.com";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcClient jdbcClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        // /api/routes reads the schedule; the table only has to exist for the request
        // to reach the point where CORS headers are written.
        jdbcClient.sql("""
                create table if not exists gtfs_feed_versions (
                    feed_version text primary key,
                    loaded_at timestamptz not null default clock_timestamp(),
                    source_sha256 text not null, source_bytes bigint not null)
                """).update();
        jdbcClient.sql("""
                create table if not exists gtfs_routes (
                    feed_version text not null, route_id text not null,
                    route_short_name text, route_long_name text, route_type integer,
                    primary key (feed_version, route_id))
                """).update();
    }

    @Test
    @DisplayName("a GET from the site carries the allow-origin header, with no trailing slash")
    void allowsTheSite() throws Exception {
        mockMvc.perform(get("/api/routes").header("Origin", SITE))
                .andExpect(status().isOk())
                // Exactly the origin, and never "*": a wildcard would let anybody's
                // page present this project's numbers as their own.
                .andExpect(header().string("Access-Control-Allow-Origin", SITE));
    }

    @Test
    @DisplayName("a preflight is answered without reaching a controller")
    void answersPreflight() throws Exception {
        mockMvc.perform(options("/api/routes")
                        .header("Origin", SITE)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SITE))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET"));
    }

    @Test
    @DisplayName("another origin gets no permission")
    void refusesAnyoneElse() throws Exception {
        mockMvc.perform(options("/api/routes")
                        .header("Origin", "https://not-this-site.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a write is not permitted, because every endpoint here is a read")
    void refusesWrites() throws Exception {
        mockMvc.perform(options("/api/routes")
                        .header("Origin", SITE)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
