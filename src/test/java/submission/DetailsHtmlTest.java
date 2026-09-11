package submission;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The details-card HTML rules: what the metadata line says for group vs
 * individual work, how the attempt count is colored as grants change the cap,
 * and that student-authored text is escaped before it reaches a JTextPane.
 */
class DetailsHtmlTest {

    private static final Function<Instant, String> FMT = i -> "DATE(" + i + ")";

    private static ProblemItem problem(int maxSubmissions, int submissionCount) {
        return new ProblemItem("p1", "P1", "desc", false, "FA", 100,
                maxSubmissions, submissionCount, -1, null, null, null, null);
    }

    @Test
    void groupAssignmentNamesTheStudentsGroup() {
        AssignmentItem a = new AssignmentItem("a1", "HW 3", "d", "2026-09-20T03:59:00Z",
                true, "Team 2", false, null, 1, null);
        String html = DetailsHtml.assignmentDetails(a, FMT);
        assertTrue(html.contains("Group (your group: Team 2)"), html);
        assertTrue(html.contains("No late submissions"), html);
        assertTrue(html.contains("DATE(2026-09-20T03:59:00Z)"), html);
    }

    @Test
    void groupAssignmentWithoutAGroupStillSaysGroup() {
        // A student not yet placed in a group must still see the assignment is group work.
        AssignmentItem a = new AssignmentItem("a1", "HW", "d", null, true, null, false, null, 0, null);
        String html = DetailsHtml.assignmentDetails(a, FMT);
        assertTrue(html.contains(">Group"), html);
        assertFalse(html.contains("your group"), html);
    }

    @Test
    void individualAssignmentSaysIndividual() {
        AssignmentItem a = new AssignmentItem("a1", "HW", "d", null, false, null, true,
                "2026-09-22T03:59:00Z", 0, null);
        String html = DetailsHtml.assignmentDetails(a, FMT);
        assertTrue(html.contains("Individual"), html);
        assertTrue(html.contains("Late until DATE(2026-09-22T03:59:00Z)"), html);
    }

    @Test
    void attemptCountColorFollowsAttemptsLeft() {
        // Plenty left: green. One left: orange with a warning. None: red, limit reached.
        // A grant raising the cap moves the same count back to green.
        assertTrue(DetailsHtml.problemDetails(problem(5, 1)).contains("#27ae60"));
        String last = DetailsHtml.problemDetails(problem(5, 4));
        assertTrue(last.contains("#e67e22") && last.contains("(last attempt)"), last);
        String none = DetailsHtml.problemDetails(problem(5, 5));
        assertTrue(none.contains("#c0392b") && none.contains("(limit reached)"), none);
        assertTrue(DetailsHtml.problemDetails(problem(8, 5)).contains("#27ae60"),
                "A grant-raised cap must move the count out of the warning colors");
    }

    @Test
    void manuallyGradedProblemSaysSo() {
        ProblemItem manual = new ProblemItem("p1", "P1", "d", false, "FA", 100,
                5, 0, -1, null, null, false, null);
        assertTrue(DetailsHtml.problemDetails(manual).contains("Graded by your instructor"));
        // Autograded (true) and unknown (older server, null) both stay quiet.
        assertFalse(DetailsHtml.problemDetails(problem(5, 0)).contains("Graded by your instructor"));
    }

    @Test
    void unlimitedProblemsSayNoLimit() {
        String html = DetailsHtml.problemDetails(problem(-1, 3));
        assertTrue(html.contains("Submissions: 3 (no limit)"), html);
    }

    @Test
    void studentTextIsEscaped() {
        AssignmentItem a = new AssignmentItem("a1", "<b>HW</b>", "a & b <i>", null,
                true, "<script>x</script>", false, null, 0, null);
        String html = DetailsHtml.assignmentDetails(a, FMT);
        assertFalse(html.contains("<b>HW</b>"), html);
        assertTrue(html.contains("&lt;b&gt;HW&lt;/b&gt;"), html);
        assertTrue(html.contains("a &amp; b &lt;i&gt;"), html);
        assertFalse(html.contains("<script>"), html);
    }

    @Test
    void escapeHtmlCoversTheUsualSuspects() {
        assertEquals("&amp;&lt;&gt;&quot;&#39;", DetailsHtml.escapeHtml("&<>\"'"));
        assertEquals("", DetailsHtml.escapeHtml(null));
    }
}
