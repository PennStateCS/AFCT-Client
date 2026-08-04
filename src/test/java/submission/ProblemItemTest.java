package submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProblemItem data and helper methods.
 */
class ProblemItemTest {

    private ProblemItem make(String id, String name, boolean solved,
                             int maxSubmissions, int submissionCount) {
        return new ProblemItem(id, name, "desc", solved,
                "FA", 100, maxSubmissions, submissionCount, -1, null, null);
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toStringReturnsProblemName() {
        ProblemItem p = make("p1", "Traffic Light", false, -1, 0);
        assertEquals("Traffic Light", p.toString());
    }

    @Test
    void toStringReturnsProblemNameWhenSolved() {
        // Solved state is shown via icon, not in the text label.
        ProblemItem p = make("p2", "Traffic Light", true, -1, 0);
        assertEquals("Traffic Light", p.toString());
    }

    // ── attemptsLeft ─────────────────────────────────────────────────────────

    @Test
    void attemptsLeftIsMinusOneWhenUnlimited() {
        ProblemItem p = make("p3", "X", false, 0, 5);
        assertEquals(-1, p.attemptsLeft());
    }

    @Test
    void attemptsLeftIsMinusOneWhenMaxNegative() {
        ProblemItem p = make("p4", "X", false, -1, 5);
        assertEquals(-1, p.attemptsLeft());
    }

    @Test
    void attemptsLeftComputesRemaining() {
        ProblemItem p = make("p5", "X", false, 10, 3);
        assertEquals(7, p.attemptsLeft());
    }

    @Test
    void attemptsLeftClampsToZeroWhenExhausted() {
        ProblemItem p = make("p6", "X", false, 5, 5);
        assertEquals(0, p.attemptsLeft());
    }

    @Test
    void attemptsLeftClampsToZeroWhenOverLimit() {
        ProblemItem p = make("p7", "X", false, 5, 10);
        assertEquals(0, p.attemptsLeft());
    }

    @Test
    void attemptsLeftIsMinusOneWhenCountNegative() {
        // submissionCount < 0 also signals "unlimited"
        ProblemItem p = make("p8", "X", false, 10, -1);
        assertEquals(-1, p.attemptsLeft());
    }

    // ── fields ───────────────────────────────────────────────────────────────

    @Test
    void solvedFieldReflectsConstructorValue() {
        assertTrue(make("p9", "X", true, 0, 0).solved);
        assertFalse(make("p10", "X", false, 0, 0).solved);
    }
}
