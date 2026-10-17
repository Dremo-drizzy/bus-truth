package io.github.dremo_drizzy.bustruth.processor;

import io.github.dremo_drizzy.bustruth.processor.detect.DetectionRun;
import io.github.dremo_drizzy.bustruth.processor.ontime.OntimeRun;
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
 *   ./mvnw -pl common,processor -am package -DskipTests
 *   java -jar processor/target/processor-0.1.0-SNAPSHOT.jar \
 *       --server.port=0 --from=2026-10-04 --to=2026-10-10 [--stage=detect|ontime|both]
 * </pre>
 *
 * <p>The packaged jar rather than {@code spring-boot:run}: on this machine the plugin
 * silently dropped {@code -Dspring-boot.run.arguments}, so the application started,
 * found no dates and did nothing, successfully.
 *
 * <p>Without arguments it does nothing and leaves the service running, which is what
 * the endpoints are for.
 */
@Component
public class ProcessorCli implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessorCli.class);

    private final DetectionRun detectionRun;
    private final OntimeRun ontimeRun;

    public ProcessorCli(DetectionRun detectionRun, OntimeRun ontimeRun) {
        this.detectionRun = detectionRun;
        this.ontimeRun = ontimeRun;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!arguments.containsOption("from") || !arguments.containsOption("to")) {
            log.info("no --from/--to given; POST /processor/run?from=&to= to derive arrivals");
            return;
        }
        LocalDate from = LocalDate.parse(arguments.getOptionValues("from").getFirst());
        LocalDate to = LocalDate.parse(arguments.getOptionValues("to").getFirst());

        // Both stages by default, because the second is useless without the first and
        // the common case is rebuilding a range end to end. --stage names one of them
        // for the case that matters: changing the on-time window, where re-deriving
        // every arrival from raw positions would be minutes of work for no change.
        String stage = arguments.containsOption("stage")
                ? arguments.getOptionValues("stage").getFirst()
                : "both";

        if (stage.equals("both") || stage.equals("detect")) {
            log.info("deriving arrivals for {} .. {}", from, to);
            log.info("detection done: {}", detectionRun.run(from, to));
        }
        if (stage.equals("both") || stage.equals("ontime")) {
            log.info("comparing arrivals against the schedule for {} .. {}", from, to);
            log.info("on-time done: {}", ontimeRun.run(from, to));
        }
    }
}
