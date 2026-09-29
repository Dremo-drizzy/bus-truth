package io.github.dremo_drizzy.bustruth.collector.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes raw feed bytes to disk, in the same layout and format as
 * scripts/save_feeds.py: {@code <root>/<feed>/<yyyy-MM-dd>/<HHmmss>.pb.gz}, in UTC.
 *
 * <p>Called before the bytes are decoded, so a parsing bug can never cost us a
 * snapshot. Raw data is the one thing that cannot be recreated (ADR-005).
 *
 * <p>Built by {@code CollectorConfig} rather than component-scanned: it takes a
 * plain Path, which keeps it usable from a test with a temporary directory and
 * leaves it with exactly one constructor for Spring to consider.
 */
public class RawArchiver {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_OF_DAY =
            DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);

    private static final Logger log = LoggerFactory.getLogger(RawArchiver.class);

    private final Path root;

    public RawArchiver(Path root) {
        // Resolved immediately, and logged: a relative archive-dir is interpreted
        // against the process working directory, which is the module directory under
        // spring-boot:run and the repo root under java -jar. Printing the absolute
        // path means nobody has to guess where the snapshots went.
        this.root = root.toAbsolutePath().normalize();
        log.info("archiving raw feed bytes to {}", this.root);
    }

    /**
     * Compresses and stores one snapshot, returning where it landed.
     *
     * <p>The bytes are written to a {@code .tmp} file and then renamed. A rename on
     * one filesystem is atomic, so a reader never sees a half-written snapshot and
     * an interrupted run leaves a stray .tmp rather than a corrupt .pb.gz.
     */
    public Path archive(String feedName, byte[] raw) throws IOException {
        Instant now = Instant.now();
        Path directory = root.resolve(feedName).resolve(DAY.format(now));
        Files.createDirectories(directory);

        String timeOfDay = TIME_OF_DAY.format(now);
        Path temporary = directory.resolve(timeOfDay + ".pb.gz.tmp");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temporary))) {
            out.write(raw);
        }

        // Two snapshots can land in the same second — a restart, or the saver script
        // running alongside this service. Overwriting one would be silent data loss,
        // and ADR-005 makes raw data the one thing that cannot be recreated, so a
        // collision takes a -1, -2 ... suffix instead.
        //
        // The name is claimed with createFile, not by letting the move fail: on
        // Windows ATOMIC_MOVE maps to MoveFileEx with MOVEFILE_REPLACE_EXISTING and
        // quietly replaces the target, so it never reports a collision. createFile
        // fails if the name exists on every platform, and it does so atomically,
        // which also makes this correct when two processes race.
        for (int attempt = 0; ; attempt++) {
            Path target = directory.resolve(attempt == 0
                    ? timeOfDay + ".pb.gz"
                    : timeOfDay + "-" + attempt + ".pb.gz");
            try {
                Files.createFile(target);
            } catch (FileAlreadyExistsException taken) {
                if (attempt > 100) {
                    throw taken;
                }
                continue;
            }
            // Replaces the empty file just reserved, never somebody else's snapshot.
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
    }
}
