package io.github.dremo_drizzy.bustruth.collector.archive;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.github.dremo_drizzy.bustruth.collector.mapper.VehiclePositionMapper;
import io.github.dremo_drizzy.bustruth.collector.store.VehiclePositionWriter;
import io.github.dremo_drizzy.bustruth.common.VehiclePosition;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Replays archived snapshots into the database.
 *
 * <p>The archive was always described as the irreplaceable asset and the database as
 * derived and rebuildable — but nothing could actually rebuild it. The archive held
 * 152,000 snapshots while the database held 3,374, because rows only ever arrived from
 * a collector running live. Everything downstream was therefore measuring how long the
 * collector happened to be running, not what the buses did.
 *
 * <p>This reads what is on disk and writes it through exactly the same path a live poll
 * takes: the same protobuf parser, the same {@link VehiclePositionMapper}, the same
 * {@link VehiclePositionWriter}. Nothing here decodes or maps anything itself, because a
 * second implementation would be a second set of bugs, and a backfill that disagreed
 * with live collection would be worse than no backfill.
 *
 * <p>Safe to run repeatedly and safe to interrupt: the insert is idempotent on
 * (vehicle_id, vehicle_timestamp), so a re-run writes only what is missing.
 */
@Component
public class ArchiveBackfill {

    private static final Logger log = LoggerFactory.getLogger(ArchiveBackfill.class);

    private final VehiclePositionMapper mapper;
    private final VehiclePositionWriter writer;

    public ArchiveBackfill(VehiclePositionMapper mapper, VehiclePositionWriter writer) {
        this.mapper = mapper;
        this.writer = writer;
    }

    /**
     * @param archiveRoot the directory holding per-feed folders, normally data/raw
     * @param from        first archive day to replay, inclusive
     * @param to          last archive day to replay, inclusive
     */
    public Result run(Path archiveRoot, LocalDate from, LocalDate to) throws IOException {
        Path feed = archiveRoot.resolve("vehicle-positions");
        if (!Files.isDirectory(feed)) {
            throw new IOException("no vehicle-positions archive at " + feed.toAbsolutePath());
        }

        int snapshots = 0;
        int unreadable = 0;
        int attempted = 0;
        int written = 0;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            Path dayDirectory = feed.resolve(day.toString());
            if (!Files.isDirectory(dayDirectory)) {
                log.info("{}: no archive directory, skipping", day);
                continue;
            }

            int daySnapshots = 0;
            int dayAttempted = 0;
            int dayWritten = 0;

            // Sorted so the replay follows the order the snapshots were taken, which
            // makes a partial run a prefix of a full one rather than a scatter.
            try (var files = Files.list(dayDirectory)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".pb.gz"))
                        .sorted().toList()) {
                    List<VehiclePosition> positions;
                    try {
                        positions = mapper.map(parse(file));
                    } catch (Exception unparseable) {
                        // One corrupt snapshot must not end the backfill: the rest of
                        // the archive is still worth loading, and the file stays on
                        // disk to be looked at.
                        unreadable++;
                        log.warn("{}: could not read, skipping: {}", file.getFileName(),
                                unparseable.toString());
                        continue;
                    }
                    daySnapshots++;
                    for (VehiclePosition position : positions) {
                        dayAttempted++;
                        if (writer.insert(position, null, null)) {
                            dayWritten++;
                        }
                    }
                }
            }

            snapshots += daySnapshots;
            attempted += dayAttempted;
            written += dayWritten;
            log.info("{}: {} snapshots, {} inserts attempted, {} rows written ({}% already present)",
                    day, daySnapshots, dayAttempted, dayWritten,
                    dayAttempted == 0 ? 0
                            : Math.round((dayAttempted - dayWritten) * 100.0 / dayAttempted));
        }

        Result result = new Result(snapshots, unreadable, attempted, written);
        log.info("backfill complete: {}", result);
        return result;
    }

    private static FeedMessage parse(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             GZIPInputStream gzip = new GZIPInputStream(raw)) {
            return FeedMessage.parseFrom(gzip.readAllBytes());
        }
    }

    /**
     * @param snapshotsRead    archived snapshots decoded
     * @param snapshotsSkipped files that could not be decoded
     * @param insertsAttempted positions handed to the writer
     * @param rowsWritten      inserts that created a row; the remainder were already
     *                         stored, which is what makes a re-run cheap and harmless
     */
    public record Result(int snapshotsRead, int snapshotsSkipped,
                         int insertsAttempted, int rowsWritten) {
        @Override
        public String toString() {
            return "%,d snapshots read, %,d unreadable, %,d inserts attempted, %,d rows written"
                    .formatted(snapshotsRead, snapshotsSkipped, insertsAttempted, rowsWritten);
        }
    }
}
