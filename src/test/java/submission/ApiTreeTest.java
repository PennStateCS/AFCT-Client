package submission;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Submission Center's data rules, tested against the typed API models. The
 * server resolves visibility, extensions and grant-adjusted limits before the
 * client ever sees them, so what is tested here is that the client faithfully
 * renders what arrived: group columns, course-zone dates, the server-clock
 * upcoming filter, and the per-student attempt cap.
 */
class ApiTreeTest {

    private static ApiModels.Assignment assignment(String id, String dueDate, Boolean isGroup,
                                                   String groupName, List<ApiModels.Problem> problems) {
        return new ApiModels.Assignment(id, "HW", "d", dueDate, null, null, null, isGroup, groupName, problems, null);
    }

    private static ApiModels.Problem problem(String title, Integer maxSubmissions,
                                             Integer submissionCount, Boolean solved) {
        return new ApiModels.Problem("p1", title, null, null, null, null, 100,
                maxSubmissions, submissionCount, null, null, solved, null, null);
    }

    private static ApiModels.Submission submission(String submittedAt, String submittedBy,
                                                   String status, Boolean correct) {
        return new ApiModels.Submission("s1", status, correct, submittedAt, "dfa.jff",
                correct != null && correct ? "ok" : null, null, submittedBy, null);
    }

    private static ApiModels.Submission withVisibility(String status, Boolean correct,
                                                       String feedback, Boolean feedbackVisible) {
        return new ApiModels.Submission("s1", status, correct, null, "dfa.jff",
                feedback, feedbackVisible, null, null);
    }

    // ── parsing ──────────────────────────────────────────────────────────────

    @Test
    void groupAssignmentCarriesGroupFields() {
        ApiModels.Assignment src = new ApiModels.Assignment(
                "a1", "HW 3", "d", "2026-09-20T03:59:00.000Z", null, "2026-09-22T03:59:00.000Z",
                true, true, "Team 2",
                List.of(problem("P1", 3, 0, false), problem("P2", 3, 0, false)), null);
        AssignmentItem a = ApiTree.assignment(src);
        assertTrue(a.isGroup);
        assertEquals("Team 2", a.groupName);
        assertTrue(a.allowLateSubmissions);
        assertEquals(2, a.problemCount);
        assertEquals(Instant.parse("2026-09-22T03:59:00.000Z"), a.lateCutoffInstant());
    }

    @Test
    void individualAssignmentHasNoGroupFields() {
        AssignmentItem a = ApiTree.assignment(assignment("a1", null, null, null, null));
        assertFalse(a.isGroup);
        assertNull(a.groupName);
        assertNull(a.dueInstant());
        assertEquals(0, a.problemCount);
    }

    @Test
    void missingTitlesGetPlaceholders() {
        assertEquals("Untitled Course",
                ApiTree.course(new ApiModels.Course("c1", null, null, null, null, null, null, null, null)).name);
        assertEquals("Untitled Problem", ApiTree.problem(problem(null, null, null, null)).name);
    }

    @Test
    void problemCarriesTheGrantAdjustedCapAsSent() {
        // maxSubmissions arrives already adjusted for this student's extra-submission
        // grants; the client must show it untouched.
        ProblemItem p = ApiTree.problem(problem("Problem 1", 7, 5, false));
        assertEquals(7, p.maxSubmissions);
        assertEquals(5, p.submissionCount);
        assertEquals(2, p.attemptsLeft());
        assertFalse(p.solved);
    }

    @Test
    void problemWithMissingNumbersReportsUnknownNotZero() {
        ProblemItem p = ApiTree.problem(problem("P", null, null, null));
        assertEquals(-1, p.maxSubmissions);
        assertEquals(-1, p.submissionCount);
        assertEquals(-1, p.attemptsLeft(), "Unknown must not read as none left");
    }

    // ── upcoming filter and ordering ─────────────────────────────────────────

    @Test
    void upcomingComparesAgainstTheGivenClock() {
        Instant serverNow = Instant.parse("2026-09-10T12:00:00Z");
        assertTrue(ApiTree.isUpcoming(assignment("a", "2026-09-10T12:00:01Z", null, null, null), serverNow));
        assertFalse(ApiTree.isUpcoming(assignment("a", "2026-09-10T11:59:59Z", null, null, null), serverNow));
        assertFalse(ApiTree.isUpcoming(assignment("a", null, null, null, null), serverNow));
        assertFalse(ApiTree.isUpcoming(assignment("a", "garbage", null, null, null), serverNow));
    }

    @Test
    void assignmentsSortEarliestDueFirstWithMissingDatesLast() {
        List<ApiModels.Assignment> raw = new ArrayList<>(List.of(
                assignment("none", null, null, null, null),
                assignment("late", "2026-12-01T00:00:00Z", null, null, null),
                assignment("soon", "2026-09-15T00:00:00Z", null, null, null)));
        raw.sort(ApiTree.byDueDateNullsLast());
        assertEquals(List.of("soon", "late", "none"),
                raw.stream().map(ApiModels.Assignment::id).toList());
    }

    @Test
    void problemsSortByTitleCaseInsensitively() {
        List<ApiModels.Problem> raw = new ArrayList<>(List.of(
                problem("problem B", null, null, null),
                problem("Problem a", null, null, null),
                problem("Problem C", null, null, null)));
        raw.sort(ApiTree.byTitle());
        assertEquals(List.of("Problem a", "problem B", "Problem C"),
                raw.stream().map(ApiModels.Problem::title).toList());
    }

    // ── course-zone dates ────────────────────────────────────────────────────

    @Test
    void dueDateRendersInTheCourseTimezoneNotTheMachine() {
        // 03:59 UTC is 11:59 PM the previous day in New York (EDT): the render the
        // student must see regardless of the laptop's zone.
        String text = ApiTree.formatDueDate(Instant.parse("2026-09-20T03:59:00Z"), "America/New_York");
        assertTrue(text.contains("Sep 19, 2026"), text);
        assertTrue(text.contains("11:59"), text);
        assertTrue(text.contains("EDT"), text);
    }

    @Test
    void unknownTimezoneFallsBackInsteadOfThrowing() {
        assertDoesNotThrow(() -> ApiTree.formatDueDate(Instant.now(), "Not/AZone"));
        assertDoesNotThrow(() -> ApiTree.formatDueDate(Instant.now(), null));
    }

    // ── history rows ─────────────────────────────────────────────────────────

    private static final java.util.function.Function<Instant, String> WHEN = i -> "@" + i.toString();

    @Test
    void groupRowGainsTheMemberColumn() {
        Object[] row = ApiTree.historyRow(
                submission("2026-09-10T12:00:00Z", "Ada Lovelace", "COMPLETED", true), true, WHEN);
        assertArrayEquals(new Object[]{
                "@2026-09-10T12:00:00Z", "Ada Lovelace", "dfa.jff", "COMPLETED", "Correct", "ok"}, row);
    }

    @Test
    void individualRowHasNoMemberColumn() {
        Object[] row = ApiTree.historyRow(
                submission("2026-09-10T12:00:00Z", null, "COMPLETED", false), false, WHEN);
        assertArrayEquals(new Object[]{
                "@2026-09-10T12:00:00Z", "dfa.jff", "COMPLETED", "Incorrect", ""}, row);
    }

    @Test
    void queuedRowSaysNotEvaluatedYet() {
        assertEquals("Not evaluated yet",
                ApiTree.historyRow(submission(null, null, "PENDING", null), false, WHEN)[3]);
        assertEquals("Not evaluated yet",
                ApiTree.historyRow(submission(null, null, "PROCESSING", null), false, WHEN)[3]);
    }

    @Test
    void failedRowWithoutVerdictLeavesResultBlank() {
        assertEquals("", ApiTree.historyRow(submission(null, null, "FAILED", null), false, WHEN)[3]);
    }

    @Test
    void manuallyGradedRowSaysNotGraded() {
        // COMPLETED with no verdict is instructor-graded work awaiting a person,
        // never blank and never Incorrect.
        assertEquals("Not graded",
                ApiTree.historyRow(submission(null, null, "COMPLETED", null), false, WHEN)[3]);
    }

    @Test
    void withheldFeedbackSaysSoInsteadOfShowingAnEmptyCell() {
        Object[] row = ApiTree.historyRow(
                withVisibility("COMPLETED", false, null, false), false, WHEN);
        assertEquals("Incorrect", row[3], "The verdict still shows on a feedback-off problem");
        assertEquals("(feedback is hidden for this problem)", row[4]);
    }

    @Test
    void absentFeedbackStaysBlankWhenNothingIsWithheld() {
        // feedbackVisible=true with null feedback means the evaluator said nothing;
        // that must NOT claim anything is hidden.
        Object[] row = ApiTree.historyRow(
                withVisibility("COMPLETED", true, null, true), false, WHEN);
        assertEquals("", row[4]);
    }

    @Test
    void manuallyGradedProblemFlagParsesThrough() {
        ApiModels.Problem manual = new ApiModels.Problem("p1", "P", null, null, null, null,
                100, null, null, null, null, null, false, null);
        assertEquals(Boolean.FALSE, ApiTree.problem(manual).autograderEnabled);
        assertNull(ApiTree.problem(problem("P", null, null, null)).autograderEnabled,
                "An older server that does not send the flag leaves it unknown");
    }
}
