package io.github.dremo_drizzy.bustruth.processor;

import io.github.dremo_drizzy.bustruth.processor.detect.DetectionRun;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs detection from the command line, for backfilling the archive.
 *
 * <pre>
 *   ./mvnw -f processor/pom.xml spring-boot:run \
 *       -Dspring-boot.run.profiles=local \
 *       -Dspring-boot.run.arguments="--from=2026-10-01 --to=2026-10-07"
 * </pre>
 *
 * <p>Without arguments it does nothing and leaves the service running, which is what
 * the endpoint is for.
 */
@Component
public class ProcessorCli implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessorCli.class);

    private final DetectionRun detectionRun;

    public ProcessorCli(DetectionRun detectionRun) {
        this.detectionRun = detectionRun;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!arguments.containsOption("from") || !arguments.containsOption("to")) {
            log.info("no --from/--to given; POST /processor/run?from=&to= to derive arrivals");
            return;
        }
        LocalDate from = LocalDate.parse(arguments.getOptionValues("from").getFirst());
        LocalDate to = LocalDate.parse(arguments.getOptionValues("to").getFirst());
        log.info("deriving arrivals for {} .. {}", from, to);
        log.info("done: {}", detectionRun.run(from, to));
    }
}
