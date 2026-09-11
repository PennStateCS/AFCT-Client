package submission;

import javax.swing.JFrame;
import javax.swing.SwingWorker;
import java.io.File;
import java.time.Duration;
import java.util.List;

/**
 * One submission from upload through the grading poll, separated from the
 * window: SubmitWindow reacts to the two callbacks and never sees the network.
 *
 * The upload and the result are reported separately on purpose. The upload
 * being accepted (202) is what consumes the attempt and unlocks the UI; the
 * grading result arrives up to the poll timeout later, and a failure after
 * acceptance means only that the RESULT could not be fetched - the submission
 * itself is safely queued server-side, which the outcome kinds keep distinct.
 */
class SubmissionTask {

    /** How long the grading poll waits before handing the wait over to Submission History. */
    static final Duration POLL_TIMEOUT = Duration.ofMinutes(2);

    /** Both callbacks arrive on the EDT. */
    interface Listener {
        /** The server accepted the upload; the attempt is used and grading is running. */
        void uploadAccepted(String submissionId);

        /** The terminal report for this attempt; see {@link Outcome}. */
        void finished(Outcome outcome);
    }

    enum Kind {
        /** Graded, full marks. */
        CORRECT,
        /** Graded, not accepted; {@code feedback} carries the evaluator's witness if visible. */
        INCORRECT,
        /** Completed with no verdict: a person grades this problem, later. */
        NOT_GRADED,
        /** The evaluator failed; the student should resubmit. */
        GRADING_FAILED,
        /** Still queued/processing when the poll timeout elapsed; history will show it. */
        STILL_RUNNING,
        /** The upload itself failed; no attempt was consumed. {@code error} says why. */
        UPLOAD_FAILED,
        /** Upload accepted but the result could not be fetched; the submission is queued. */
        RESULT_FETCH_FAILED,
        /** The user cancelled the login prompt; nothing was sent. */
        LOGIN_CANCELLED
    }

    record Outcome(Kind kind, String submissionId, String feedback, String error) {

        static Outcome error(Kind kind, String message) {
            return new Outcome(kind, null, null, message);
        }
    }

    /** The pure mapping from a polled submission to its outcome; kept separate for tests. */
    static Outcome outcomeOf(ApiModels.Submission result) {
        String id = result.id();
        if ("COMPLETED".equals(result.status())) {
            if (result.correct() == null) {
                // Not "incorrect": the autograder did not judge this one; a person will.
                return new Outcome(Kind.NOT_GRADED, id, null, null);
            }
            if (result.correct()) {
                return new Outcome(Kind.CORRECT, id, null, null);
            }
            return new Outcome(Kind.INCORRECT, id, result.feedback() != null ? result.feedback() : "", null);
        }
        if ("FAILED".equals(result.status())) {
            return new Outcome(Kind.GRADING_FAILED, id, null, null);
        }
        return new Outcome(Kind.STILL_RUNNING, id, null, null);
    }

    /**
     * Uploads and polls in the background. When {@code deleteFileWhenDone} is set
     * (a temp-encoded file, never one the user browsed to), the file is removed
     * once the task ends, whatever the outcome.
     */
    static void run(SessionHandler sessionHandler, JFrame loginParent,
                    String courseId, String assignmentId, String problemId,
                    File file, boolean deleteFileWhenDone, Listener listener) {
        new SwingWorker<SubmissionTask.Outcome, String>() {
            @Override
            protected Outcome doInBackground() {
                try {
                    AFCTClient client = sessionHandler.requireAuthenticated(loginParent);
                    if (client == null) {
                        return Outcome.error(Kind.LOGIN_CANCELLED, "Login cancelled.");
                    }

                    ApiModels.CreateResult accepted =
                            client.createSubmission(courseId, assignmentId, problemId, file);
                    if (accepted == null || accepted.submissionId() == null) {
                        return Outcome.error(Kind.UPLOAD_FAILED, "No submission id returned by server.");
                    }
                    String submissionId = accepted.submissionId();
                    publish(submissionId);

                    try {
                        return outcomeOf(client.waitForResult(submissionId, POLL_TIMEOUT));
                    } catch (Exception ex) {
                        return new Outcome(Kind.RESULT_FETCH_FAILED, submissionId, null,
                                ErrorMessages.userMessage(ex, "The result could not be fetched."));
                    }
                } catch (Exception ex) {
                    return Outcome.error(Kind.UPLOAD_FAILED,
                            ErrorMessages.userMessage(ex, "Unexpected submission error."));
                }
            }

            @Override
            protected void process(List<String> submissionIds) {
                listener.uploadAccepted(submissionIds.get(submissionIds.size() - 1));
            }

            @Override
            protected void done() {
                if (deleteFileWhenDone) {
                    SubmissionFiles.deleteQuietly(file);
                }
                Outcome outcome;
                try {
                    outcome = get();
                } catch (Exception ex) {
                    outcome = Outcome.error(Kind.UPLOAD_FAILED,
                            ErrorMessages.userMessage(ex, "Unexpected submission error."));
                }
                listener.finished(outcome);
            }
        }.execute();
    }
}
