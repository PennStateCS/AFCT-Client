package submission;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pure mapping from a polled submission to the outcome the window reports.
 * The distinction that matters most is RESULT_FETCH_FAILED vs UPLOAD_FAILED,
 * decided in the task itself: after acceptance a failure must never read as
 * "your submission failed", because the attempt is consumed and queued.
 */
class SubmissionTaskTest {

    private static ApiModels.Submission sub(String status, Boolean correct, String feedback) {
        return new ApiModels.Submission("s1", status, correct, null, null, feedback, null, null, null);
    }

    @Test
    void completedAndCorrectIsCorrect() {
        SubmissionTask.Outcome o = SubmissionTask.outcomeOf(sub("COMPLETED", true, null));
        assertEquals(SubmissionTask.Kind.CORRECT, o.kind());
        assertEquals("s1", o.submissionId());
    }

    @Test
    void completedAndWrongIsIncorrectWithFeedback() {
        SubmissionTask.Outcome o = SubmissionTask.outcomeOf(sub("COMPLETED", false, "witness: abba"));
        assertEquals(SubmissionTask.Kind.INCORRECT, o.kind());
        assertEquals("witness: abba", o.feedback());
    }

    @Test
    void completedWithoutVerdictIsNotGradedNeverIncorrect() {
        // A person grades this problem; the autograder passing on it must not read
        // as a failure to the student ("not correct" and "not graded yet" are
        // opposite things to read — same rule as the web).
        SubmissionTask.Outcome o = SubmissionTask.outcomeOf(sub("COMPLETED", null, null));
        assertEquals(SubmissionTask.Kind.NOT_GRADED, o.kind());
        assertEquals("s1", o.submissionId());
    }

    @Test
    void failedIsGradingFailed() {
        assertEquals(SubmissionTask.Kind.GRADING_FAILED,
                SubmissionTask.outcomeOf(sub("FAILED", null, null)).kind());
    }

    @Test
    void pendingOrProcessingIsStillRunning() {
        assertEquals(SubmissionTask.Kind.STILL_RUNNING,
                SubmissionTask.outcomeOf(sub("PENDING", null, null)).kind());
        assertEquals(SubmissionTask.Kind.STILL_RUNNING,
                SubmissionTask.outcomeOf(sub("PROCESSING", null, null)).kind());
    }

    // ── run(): the full task against mocked session/client ──────────────────

    /** Collects the listener callbacks and lets the test await the terminal one. */
    private static class RecordingListener implements SubmissionTask.Listener {
        final List<String> acceptedIds = new ArrayList<>();
        final AtomicReference<SubmissionTask.Outcome> outcome = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void uploadAccepted(String submissionId) {
            acceptedIds.add(submissionId);
        }

        @Override
        public void finished(SubmissionTask.Outcome o) {
            outcome.set(o);
            done.countDown();
        }
    }

    private static SubmissionTask.Outcome runTask(SessionHandler handler, File file,
                                                  boolean deleteWhenDone,
                                                  RecordingListener listener) throws Exception {
        SubmissionTask.run(handler, null, "c1", "a1", "p1", file, deleteWhenDone, listener);
        assertTrue(listener.done.await(15, TimeUnit.SECONDS), "Task did not finish");
        return listener.outcome.get();
    }

    @Test
    void happyPathReportsAcceptanceThenTheGradedOutcome() throws Exception {
        SessionHandler handler = Mockito.mock(SessionHandler.class);
        AFCTClient client = Mockito.mock(AFCTClient.class);
        Mockito.when(handler.requireAuthenticated(Mockito.any())).thenReturn(client);
        Mockito.when(client.createSubmission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new ApiModels.CreateResult("s9", "PENDING"));
        Mockito.when(client.waitForResult(Mockito.eq("s9"), Mockito.any()))
                .thenReturn(sub("COMPLETED", true, null));

        RecordingListener listener = new RecordingListener();
        SubmissionTask.Outcome outcome = runTask(handler, new File("unused.jff"), false, listener);

        assertEquals(List.of("s9"), listener.acceptedIds);
        assertEquals(SubmissionTask.Kind.CORRECT, outcome.kind());
    }

    @Test
    void uploadFailureNeverReportsAcceptance() throws Exception {
        SessionHandler handler = Mockito.mock(SessionHandler.class);
        AFCTClient client = Mockito.mock(AFCTClient.class);
        Mockito.when(handler.requireAuthenticated(Mockito.any())).thenReturn(client);
        Mockito.when(client.createSubmission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new IOException("Resubmit cooldown active."));

        RecordingListener listener = new RecordingListener();
        SubmissionTask.Outcome outcome = runTask(handler, new File("unused.jff"), false, listener);

        assertTrue(listener.acceptedIds.isEmpty(), "No attempt was consumed");
        assertEquals(SubmissionTask.Kind.UPLOAD_FAILED, outcome.kind());
        assertTrue(outcome.error().contains("cooldown"), outcome.error());
    }

    @Test
    void fetchFailureAfterAcceptanceKeepsTheSubmissionId() throws Exception {
        // The upload went through; only the result fetch died. The outcome must say
        // so, with the id, because the submission is queued server-side.
        SessionHandler handler = Mockito.mock(SessionHandler.class);
        AFCTClient client = Mockito.mock(AFCTClient.class);
        Mockito.when(handler.requireAuthenticated(Mockito.any())).thenReturn(client);
        Mockito.when(client.createSubmission(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new ApiModels.CreateResult("s9", "PENDING"));
        Mockito.when(client.waitForResult(Mockito.eq("s9"), Mockito.any()))
                .thenThrow(new IOException("boom"));

        RecordingListener listener = new RecordingListener();
        SubmissionTask.Outcome outcome = runTask(handler, new File("unused.jff"), false, listener);

        assertEquals(List.of("s9"), listener.acceptedIds);
        assertEquals(SubmissionTask.Kind.RESULT_FETCH_FAILED, outcome.kind());
        assertEquals("s9", outcome.submissionId());
    }

    @Test
    void cancelledLoginEndsTheTaskWithoutUploading() throws Exception {
        SessionHandler handler = Mockito.mock(SessionHandler.class);
        Mockito.when(handler.requireAuthenticated(Mockito.any())).thenReturn(null);

        RecordingListener listener = new RecordingListener();
        SubmissionTask.Outcome outcome = runTask(handler, new File("unused.jff"), false, listener);

        assertEquals(SubmissionTask.Kind.LOGIN_CANCELLED, outcome.kind());
        assertTrue(listener.acceptedIds.isEmpty());
    }

    @Test
    void tempFileIsDeletedWhenAskedAndOnlyThen() throws Exception {
        SessionHandler handler = Mockito.mock(SessionHandler.class);
        Mockito.when(handler.requireAuthenticated(Mockito.any())).thenReturn(null);

        File temp = Files.createTempFile("afct-task-test", ".jff").toFile();
        runTask(handler, temp, true, new RecordingListener());
        // done() deletes on the EDT after finished(); wait for it to land.
        for (int i = 0; i < 50 && temp.exists(); i++) Thread.sleep(100);
        assertFalse(temp.exists(), "deleteFileWhenDone must remove the temp file on any outcome");

        File kept = Files.createTempFile("afct-task-test", ".jff").toFile();
        try {
            runTask(handler, kept, false, new RecordingListener());
            assertTrue(kept.exists(), "A user-chosen file must never be deleted");
        } finally {
            Files.deleteIfExists(kept.toPath());
        }
    }
}
