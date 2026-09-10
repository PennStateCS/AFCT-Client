package submission;

import automata.fsa.FiniteStateAutomaton;
import gui.environment.Environment;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Temp-file handling for a submission: what gets encoded, what it is named, and
 * that cleanup really removes what encodeToTemp created (the leak this class
 * exists to close), without ever touching a file the user chose themselves.
 */
class SubmissionFilesTest {

    private static Environment envWithAutomaton() {
        Environment env = Mockito.mock(Environment.class);
        Mockito.when(env.getObject()).thenReturn(new FiniteStateAutomaton());
        return env;
    }

    @Test
    void anonymousTempIsEncodedJff() throws Exception {
        File f = SubmissionFiles.encodeToTemp(envWithAutomaton(), null);
        try {
            assertTrue(f.exists());
            assertTrue(f.getName().endsWith(".jff"), f.getName());
            String content = Files.readString(f.toPath());
            assertTrue(content.contains("<structure"), "Encoded JFLAP XML expected, got: "
                    + content.substring(0, Math.min(80, content.length())));
        } finally {
            SubmissionFiles.deleteQuietly(f);
        }
    }

    @Test
    void namedTempKeepsTheOpenFilesName() throws Exception {
        // The server records the uploaded file's name; a student recognizes
        // "hw1.jff" in their history, not "afct8231.jff".
        File f = SubmissionFiles.encodeToTemp(envWithAutomaton(), "hw1.jff");
        try {
            assertEquals("hw1.jff", f.getName());
            assertTrue(f.getParentFile().getName().startsWith("afct"),
                    "Named temp must live in its own afct* dir: " + f.getParent());
        } finally {
            SubmissionFiles.deleteQuietly(f);
        }
    }

    @Test
    void deleteQuietlyRemovesTheNamedTempAndItsDirectory() throws Exception {
        File f = SubmissionFiles.encodeToTemp(envWithAutomaton(), "hw1.jff");
        File dir = f.getParentFile();
        SubmissionFiles.deleteQuietly(f);
        assertFalse(f.exists(), "File must be gone");
        assertFalse(dir.exists(), "Wrapper temp dir must be gone too");
    }

    @Test
    void deleteQuietlyRemovesTheAnonymousTempButNotTheSystemTempDir() throws Exception {
        File f = SubmissionFiles.encodeToTemp(envWithAutomaton(), null);
        File parent = f.getParentFile();
        SubmissionFiles.deleteQuietly(f);
        assertFalse(f.exists());
        assertTrue(parent.exists(), "The system temp directory must survive");
    }

    @Test
    void deleteQuietlyNeverThrows() {
        assertDoesNotThrow(() -> SubmissionFiles.deleteQuietly(null));
        assertDoesNotThrow(() -> SubmissionFiles.deleteQuietly(new File("does/not/exist.jff")));
    }
}
