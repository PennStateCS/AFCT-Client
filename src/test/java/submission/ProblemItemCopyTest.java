package submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ProblemItem is immutable; updates are copies swapped into the tree. */
class ProblemItemCopyTest {

    private static final ProblemItem BASE =
            new ProblemItem("p1", "P1", "d", false, "FA", 100, 5, 2, -1, 4, true, false, null);

    @Test
    void withOneMoreSubmissionOnlyChangesTheCount() {
        ProblemItem bumped = BASE.withOneMoreSubmission();
        assertEquals(3, bumped.submissionCount);
        assertEquals(2, BASE.submissionCount, "The original is untouched");
        assertEquals(BASE.id, bumped.id);
        assertEquals(BASE.maxSubmissions, bumped.maxSubmissions);
        assertEquals(BASE.solved, bumped.solved);
        assertEquals(BASE.maxStates, bumped.maxStates);
    }

    @Test
    void asSolvedOnlyChangesSolved() {
        ProblemItem solved = BASE.asSolved();
        assertTrue(solved.solved);
        assertFalse(BASE.solved);
        assertEquals(BASE.submissionCount, solved.submissionCount);
    }

    @Test
    void attemptsLeftFollowsTheCopies() {
        assertEquals(3, BASE.attemptsLeft());
        assertEquals(2, BASE.withOneMoreSubmission().attemptsLeft());
    }
}
