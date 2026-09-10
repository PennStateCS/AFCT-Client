package submission;

import org.junit.jupiter.api.Test;

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
    void completedWithoutVerdictIsIncorrectWithEmptyFeedback() {
        // Feedback can be withheld by visibility settings; the outcome must not be null-y.
        SubmissionTask.Outcome o = SubmissionTask.outcomeOf(sub("COMPLETED", null, null));
        assertEquals(SubmissionTask.Kind.INCORRECT, o.kind());
        assertEquals("", o.feedback());
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
}
