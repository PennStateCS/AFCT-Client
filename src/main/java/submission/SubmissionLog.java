package submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * The submission event log: one line per event, appended to a per-day file
 * (logs/submissions-YYYY-MM-DD.log) and echoed to stdout. A write failure is
 * reported and swallowed — logging must never break a submission.
 */
class SubmissionLog {

    private static final DateTimeFormatter LINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path directory;
    private final Supplier<LocalDateTime> clock;

    SubmissionLog(Path directory, Supplier<LocalDateTime> clock) {
        this.directory = directory;
        this.clock = clock;
    }

    static SubmissionLog inWorkingDirectory() {
        return new SubmissionLog(Paths.get(System.getProperty("user.dir"), "logs"), LocalDateTime::now);
    }

    void log(String event, String detail) {
        LocalDateTime now = clock.get();
        String line = "[" + now.format(LINE_FMT) + "] " + event + ": " + detail;
        System.out.println(line);
        try {
            Files.createDirectories(directory);
            Path logFile = directory.resolve("submissions-" + now.format(DATE_FMT) + ".log");
            Files.writeString(logFile, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            String msg = ErrorMessages.userMessage(ex, "Unable to write submission log.");
            System.err.println("Log write failed: " + msg);
        }
    }
}
