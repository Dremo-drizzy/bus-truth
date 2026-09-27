package io.github.dremo_drizzy.bustruth.collector.archive;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dremo_drizzy.bustruth.collector.Fixtures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawArchiverTest {

    @TempDir
    Path sameSecondRoot;

    @Test
    void writesGzippedBytesUnderFeedAndUtcDate(@TempDir Path root) throws IOException {
        byte[] raw = Fixtures.rawBytes("vehicle-positions");

        Path written = new RawArchiver(root).archive("vehicle-positions", raw);

        assertThat(written).exists();
        assertThat(written.getFileName().toString()).endsWith(".pb.gz").hasSize("HHmmss.pb.gz".length());
        // <root>/<feed>/<yyyy-MM-dd>/<HHmmss>.pb.gz
        assertThat(written.getParent().getFileName().toString()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(written.getParent().getParent().getFileName().toString()).isEqualTo("vehicle-positions");
    }

    @Test
    void storesTheBytesUnchanged(@TempDir Path root) throws IOException {
        byte[] raw = Fixtures.rawBytes("alerts");

        Path written = new RawArchiver(root).archive("alerts", raw);

        try (InputStream in = new GZIPInputStream(Files.newInputStream(written))) {
            assertThat(in.readAllBytes())
                    .describedAs("the archive must round-trip to the exact bytes the agency sent")
                    .isEqualTo(raw);
        }
    }

    @Test
    void neverOverwritesAnExistingSnapshotFromTheSameSecond() throws IOException {
        // Two snapshots in one second happen on a restart, or when the saver script
        // runs alongside the collector. Losing one would be silent data loss in an
        // archive that ADR-005 says must be kept.
        RawArchiver archiver = new RawArchiver(sameSecondRoot);
        byte[] first = Fixtures.rawBytes("alerts");
        byte[] second = Fixtures.rawBytes("trip-updates");

        Path a = archiver.archive("alerts", first);
        Path b = archiver.archive("alerts", second);

        assertThat(b).isNotEqualTo(a);
        assertThat(a).exists();
        assertThat(b).exists();
        try (InputStream in = new GZIPInputStream(Files.newInputStream(a))) {
            assertThat(in.readAllBytes()).isEqualTo(first);
        }
        try (InputStream in = new GZIPInputStream(Files.newInputStream(b))) {
            assertThat(in.readAllBytes()).isEqualTo(second);
        }
    }

    @Test
    void leavesNoTemporaryFilesBehind(@TempDir Path root) throws IOException {
        Path written = new RawArchiver(root).archive("alerts", Fixtures.rawBytes("alerts"));

        try (var entries = Files.list(written.getParent())) {
            List<String> names = entries.map(path -> path.getFileName().toString()).toList();
            assertThat(names).containsExactly(written.getFileName().toString());
            assertThat(names).noneMatch(name -> name.endsWith(".tmp"));
        }
    }
}
