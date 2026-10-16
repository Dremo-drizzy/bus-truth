package io.github.dremo_drizzy.bustruth.api.performance;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.api.live.VehicleFeatureCollection;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.CoverageDay;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.Route;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.RouteDetail;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.RoutePerformance;
import io.github.dremo_drizzy.bustruth.api.performance.PerformanceRows.StopReliability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

/**
 * The frontend's test fixtures must still be shaped like this API's responses.
 *
 * <p>The Playwright suite serves captured responses so it can run without a database,
 * which is the right trade — and it has one failure mode: a captured response goes
 * stale silently. Rename a field here and the browser tests keep passing against
 * yesterday's JSON while the real site breaks, and nothing fails until someone loads
 * the page.
 *
 * <p>This closes that gap where the change would be made. The fixtures are
 * round-tripped through the very records the controllers return, with the same Jackson
 * 3 mapper Spring serialises them with, and the key sets are compared in both
 * directions:
 *
 * <ul>
 *   <li>a key in the fixture that the record does not have fails as an unknown property
 *   <li>a field the record has that the fixture lacks shows up as a key the round trip
 *       adds
 * </ul>
 *
 * <p>So renaming, adding or removing a field in a response fails this test, in this
 * module, on the commit that does it — rather than on a schedule, days later, in
 * somebody else's pull request.
 */
class FixtureContractTest {

    /**
     * Surefire runs with the module directory as its working directory, so the
     * frontend's fixtures are one level up. Asserted rather than assumed: if the
     * layout moves, this should fail loudly instead of quietly testing nothing.
     */
    private static final Path FIXTURES = Path.of("..", "web", "tests", "fixtures");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    static List<Fixture> fixtures() {
        TypeFactory types = MAPPER.getTypeFactory();
        return List.of(
                new Fixture("routes.json", types.constructCollectionType(List.class, Route.class)),
                new Fixture("routes-performance.json",
                        types.constructCollectionType(List.class, RoutePerformance.class)),
                new Fixture("route-1.json", types.constructType(RouteDetail.class)),
                new Fixture("stop.json", types.constructType(StopReliability.class)),
                new Fixture("coverage.json",
                        types.constructCollectionType(List.class, CoverageDay.class)),
                new Fixture("meta.json", types.constructType(MetaController.Meta.class)),
                new Fixture("vehicles-live.json",
                        types.constructType(VehicleFeatureCollection.class)));
    }

    @Test
    void theFixtureDirectoryIsWhereThisTestExpects() {
        assertThat(FIXTURES).as("frontend fixtures directory").isDirectory();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void theFixtureStillMatchesTheResponseShape(Fixture fixture) throws IOException {
        JsonNode captured = MAPPER.readTree(Files.readString(FIXTURES.resolve(fixture.name())));

        // Deserialising into the real response type rejects any key the API no longer
        // has: Jackson 3 fails on unknown properties by default.
        Object asResponse = MAPPER.readValue(captured.toString(), fixture.type());
        JsonNode roundTripped = MAPPER.valueToTree(asResponse);

        assertThat(keyPaths(roundTripped, ""))
                .as("%s: the API's own shape against the captured fixture", fixture.name())
                .isEqualTo(keyPaths(captured, ""));
    }

    /**
     * Every key path in a document, with array indices collapsed.
     *
     * <p>Collapsed because the fixtures hold 67 routes and 243 vehicles, and the
     * question is whether the <em>shape</em> still matches, not whether element 43 has
     * the same keys as element 42. A sorted set, so the comparison reports exactly
     * which path appeared or vanished.
     */
    private static TreeSet<String> keyPaths(JsonNode node, String prefix) {
        TreeSet<String> paths = new TreeSet<>();
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String path = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                paths.add(path);
                paths.addAll(keyPaths(field.getValue(), path));
            }
        } else if (node.isArray()) {
            List<JsonNode> elements = new ArrayList<>();
            node.forEach(elements::add);
            for (JsonNode element : elements) {
                paths.addAll(keyPaths(element, prefix + "[]"));
            }
        }
        return paths;
    }

    /** One fixture and the response type it was captured from. */
    record Fixture(String name, tools.jackson.databind.JavaType type) {
        @Override
        public String toString() {
            return name;
        }
    }
}
