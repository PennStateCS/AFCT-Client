package submission;

import file.EncodeException;
import file.XMLCodec;
import gui.environment.Environment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Temp .jff handling for a submission. The editor's current object is encoded to
 * a fresh temp file per attempt (so unsaved work submits exactly as it looks on
 * screen), and {@link #deleteQuietly} removes it once the upload is done - the
 * files used to accumulate in the temp directory for the OS to clean someday.
 */
final class SubmissionFiles {

    private SubmissionFiles() {}

    /**
     * Encodes the editor's current object to a temp file. With a name (the open
     * file's), the temp file keeps it inside its own temp directory, so the server
     * records the file name the student knows; with null, an anonymous
     * "afct*.jff" is used.
     */
    static File encodeToTemp(Environment environment, String fileName)
            throws IOException, EncodeException {
        File target;
        if (fileName != null && !fileName.isBlank()) {
            Path tempDir = Files.createTempDirectory("afct");
            Path exact = tempDir.resolve(fileName);
            Files.createFile(exact);
            target = exact.toFile();
        } else {
            target = File.createTempFile("afct", ".jff");
        }
        new XMLCodec().encode(environment.getObject(), target, null);
        return target;
    }

    /**
     * Deletes a temp file made by {@link #encodeToTemp}, and its wrapper temp
     * directory when it has one. Never throws: cleanup must not turn a completed
     * submission into an error.
     */
    static void deleteQuietly(File file) {
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            Files.deleteIfExists(file.toPath());
            // Only the named variant wraps the file in its own "afct*" temp dir.
            if (parent != null && parent.getName().startsWith("afct")) {
                Files.deleteIfExists(parent.toPath());
            }
        } catch (Exception ignored) {
        }
    }
}
