package submission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** The per-day submission log file: naming, line format, and append behavior. */
class SubmissionLogTest {

    @Test
    void writesTimestampedLinesToThePerDayFile(@TempDir Path dir) throws Exception {
        LocalDateTime fixed = LocalDateTime.of(2026, 9, 10, 21, 30, 5);
        SubmissionLog log = new SubmissionLog(dir, () -> fixed);

        log.log("SUBMIT_START", "problem=P1");
        log.log("SUBMIT_ACCEPTED", "submissionId=s1");

        Path file = dir.resolve("submissions-2026-09-10.log");
        assertTrue(Files.exists(file));
        var lines = Files.readAllLines(file);
        assertEquals(2, lines.size(), "Appends, never overwrites");
        assertEquals("[2026-09-10 21:30:05] SUBMIT_START: problem=P1", lines.get(0));
        assertEquals("[2026-09-10 21:30:05] SUBMIT_ACCEPTED: submissionId=s1", lines.get(1));
    }

    @Test
    void aNewDayGetsANewFile(@TempDir Path dir) {
        LocalDateTime[] now = {LocalDateTime.of(2026, 9, 10, 23, 59, 0)};
        SubmissionLog log = new SubmissionLog(dir, () -> now[0]);

        log.log("A", "x");
        now[0] = LocalDateTime.of(2026, 9, 11, 0, 1, 0);
        log.log("B", "y");

        assertTrue(Files.exists(dir.resolve("submissions-2026-09-10.log")));
        assertTrue(Files.exists(dir.resolve("submissions-2026-09-11.log")));
    }

    @Test
    void aWriteFailureNeverThrows(@TempDir Path dir) throws Exception {
        // Point the log at a path that cannot be a directory (a file), so the
        // write fails; logging must swallow it.
        Path notADir = dir.resolve("occupied");
        Files.writeString(notADir, "in the way");
        SubmissionLog log = new SubmissionLog(notADir, LocalDateTime::now);
        assertDoesNotThrow(() -> log.log("A", "x"));
    }
}
