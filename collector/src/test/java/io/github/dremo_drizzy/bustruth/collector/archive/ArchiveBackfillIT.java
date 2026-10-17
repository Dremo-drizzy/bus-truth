package io.github.dremo_drizzy.bustruth.collector.archive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import io.github.dremo_drizzy.bustruth.collector.TestcontainersConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The backfill replays archived snapshots through the live write path.
 *
 * <p>Against a real PostgreSQL, because the property that makes a backfill safe to
 * interrupt and re-run is the idempotent insert, and that is SQL behaviour.
 */
@SpringBootTest(properties = {
        "bustruth.collector.polling-enabled=false",
        "POSTGRES_PASSWORD=unused"
})
@Import(TestcontainersConfig.class)
class ArchiveBackfillIT {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);

    @Autowired
    private ArchiveBackfill backfill;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path archiveRoot;

    @BeforeEach
    void setUp() throws IOException {
        jdbcClient.sql("truncate table raw_vehicle_positions").update();
        // A miniature archive in the real layout, holding a real captured snapshot.
        Path day = archiveRoot.resolve("vehicle-positions").resolve(DAY.toString());
        Files.createDirectories(day);
        writeGzipped(day.resolve("120000.pb.gz"), Fixtures.rawBytes("vehicle-positions"));
    }

    @Test
    void writesArchivedPositionsIntoTheDatabase() throws IOException {
        ArchiveBackfill.Result result = backfill.run(archiveRoot, DAY, DAY);

        assertThat(result.snapshotsRead()).isEqualTo(1);
        assertThat(result.insertsAttempted()).isPositive();
        assertThat(result.rowsWritten()).isEqualTo(result.insertsAttempted());
        assertThat(rowCount()).isEqualTo(result.rowsWritten());
    }

    @Test
    void runningItAgainWritesNothingNew() throws IOException {
        backfill.run(archiveRoot, DAY, DAY);
        long afterFirstRun = rowCount();

        ArchiveBackfill.Result second = backfill.run(archiveRoot, DAY, DAY);

        // The whole point: a backfill interrupted halfway can simply be run again.
        assertThat(second.rowsWritten()).isZero();
        assertThat(second.insertsAttempted()).isPositive();
        assertThat(rowCount()).isEqualTo(afterFirstRun);
    }

    @Test
    void anUnreadableSnapshotDoesNotStopTheRest() throws IOException {
        Path day = archiveRoot.resolve("vehicle-positions").resolve(DAY.toString());
        writeGzipped(day.resolve("110000.pb.gz"), "not protobuf".getBytes());

        ArchiveBackfill.Result result = backfill.run(archiveRoot, DAY, DAY);

        assertThat(result.snapshotsSkipped()).isEqualTo(1);
        assertThat(result.snapshotsRead()).isEqualTo(1);
        assertThat(result.rowsWritten()).isPositive();
    }

    @Test
    void aMissingDayIsSkippedRatherThanFailing() throws IOException {
        // Collection has holes; a backfill over a range must tolerate them.
        ArchiveBackfill.Result result = backfill.run(archiveRoot, DAY.minusDays(2), DAY);

        assertThat(result.snapshotsRead()).isEqualTo(1);
    }

    private static void writeGzipped(Path target, byte[] content) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(target))) {
            out.write(content);
        }
    }

    private long rowCount() {
        return jdbcClient.sql("select count(*) from raw_vehicle_positions")
                .query(Long.class).single();
    }
}
