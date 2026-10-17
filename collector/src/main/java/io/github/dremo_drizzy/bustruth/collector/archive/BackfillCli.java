package io.github.dremo_drizzy.bustruth.collector.archive;

import io.github.dremo_drizzy.bustruth.collector.config.CollectorProperties;
import java.nio.file.Path;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the archive backfill from the command line.
 *
 * <pre>
 *   ./mvnw -f collector/pom.xml spring-boot:run -Dspring-boot.run.profiles=local \
 *       -Dspring-boot.run.arguments=--backfill-from=2026-10-04,--backfill-to=2026-10-10
 * </pre>
 *
 * <p>Without those arguments it does nothing, so the collector's normal job is
 * unaffected. The archive root defaults to data/raw — the dataset the saver script
 * owns — rather than the collector's own dev archive, because the saver's is the one
 * with twenty-two days in it.
 */
@Component
public class BackfillCli implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillCli.class);

    private final ArchiveBackfill backfill;
    private final CollectorProperties properties;

    public BackfillCli(ArchiveBackfill backfill, CollectorProperties properties) {
        this.backfill = backfill;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        if (!arguments.containsOption("backfill-from") || !arguments.containsOption("backfill-to")) {
            return;
        }
        LocalDate from = LocalDate.parse(arguments.getOptionValues("backfill-from").getFirst());
        LocalDate to = LocalDate.parse(arguments.getOptionValues("backfill-to").getFirst());

        Path root = arguments.containsOption("archive-root")
                ? Path.of(arguments.getOptionValues("archive-root").getFirst())
                : Path.of("data", "raw");

        log.info("backfilling {} .. {} from {}", from, to, root.toAbsolutePath());
        log.info("result: {}", backfill.run(root, from, to));
    }
}
