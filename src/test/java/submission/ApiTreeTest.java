package submission;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Submission Center's data rules, tested against maps shaped like the real
 * /tree and history payloads. The server resolves visibility, extensions and
 * grant-adjusted limits before the client ever sees them, so what is tested here
 * is that the client faithfully renders what arrived: group columns, course-zone
 * dates, the server-clock upcoming filter, and the per-student attempt cap.
 */
class ApiTreeTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ── parsing ──────────────────────────────────────────────────────────────

    @Test
    void groupAssignmentCarriesGroupFields() {
        AssignmentItem a = ApiTree.assignment(map(
                "id", "a1", "title", "HW 3", "description", "d",
                "dueDate", "2026-09-20T03:59:00.000Z",
                "isGroup", true, "groupName", "Team 2",
                "allowLateSubmissions", true, "lateCutoff", "2026-09-22T03:59:00.000Z",
                "problems", List.of(map("id", "p1"), map("id", "p2"))));
        assertTrue(a.isGroup);
        assertEquals("Team 2", a.groupName);
        assertTrue(a.allowLateSubmissions);
        assertEquals(2, a.problemCount);
        assertEquals(Instant.parse("2026-09-22T03:59:00.000Z"), a.lateCutoffInstant());
    }

    @Test
    void individualAssignmentHasNoGroupFields() {
        AssignmentItem a = ApiTree.assignment(map("id", "a1", "title", "HW 1"));
        assertFalse(a.isGroup);
        assertNull(a.groupName);
        assertNull(a.dueInstant());
        assertEquals(0, a.problemCount);
    }

    @Test
    void jsonNullGroupNameBecomesJavaNull() {
        // Jackson maps a JSON null to Java null, but a defensive "null" string from
        // any stringification must not surface as a group literally named "null".
        AssignmentItem a = ApiTree.assignment(map("id", "a1", "isGroup", true, "groupName", "null"));
        assertNull(a.groupName);
    }

    @Test
    void problemCarriesTheGrantAdjustedCapAsSent() {
        // maxSubmissions arrives already adjusted for this student's extra-submission
        // grants; the client must show it untouched.
        ProblemItem p = ApiTree.problem(map(
                "id", "p1", "title", "Problem 1", "maxSubmissions", 7,
                "submissionCount", 5, "maxPoints", 100, "grade", 80, "solved", false));
        assertEquals(7, p.maxSubmissions);
        assertEquals(5, p.submissionCount);
        assertEquals(2, p.attemptsLeft());
        assertFalse(p.solved);
    }

    @Test
    void problemWithMissingNumbersReportsUnknownNotZero() {
        ProblemItem p = ApiTree.problem(map("id", "p1", "title", "P"));
        assertEquals(-1, p.maxSubmissions);
        assertEquals(-1, p.submissionCount);
        assertEquals(-1, p.attemptsLeft(), "Unknown must not read as none left");
    }

    @Test
    void courseTimezoneNullStringBecomesNull() {
        assertNull(ApiTree.course(map("id", "c1", "name", "X", "timezone", "null")).timezone);
        assertEquals("America/New_York",
                ApiTree.course(map("id", "c1", "name", "X", "timezone", "America/New_York")).timezone);
    }

    // ── upcoming filter and ordering ─────────────────────────────────────────

    @Test
    void upcomingComparesAgainstTheGivenClock() {
        Instant serverNow = Instant.parse("2026-09-10T12:00:00Z");
        assertTrue(ApiTree.isUpcoming(map("dueDate", "2026-09-10T12:00:01Z"), serverNow));
        assertFalse(ApiTree.isUpcoming(map("dueDate", "2026-09-10T11:59:59Z"), serverNow));
        assertFalse(ApiTree.isUpcoming(map("dueDate", null), serverNow));
        assertFalse(ApiTree.isUpcoming(map("dueDate", "garbage"), serverNow));
    }

    @Test
    void assignmentsSortEarliestDueFirstWithMissingDatesLast() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(
                map("id", "none", "dueDate", null),
                map("id", "late", "dueDate", "2026-12-01T00:00:00Z"),
                map("id", "soon", "dueDate", "2026-09-15T00:00:00Z")));
        raw.sort(ApiTree.byDueDateNullsLast());
        assertEquals(List.of("soon", "late", "none"), raw.stream().map(m -> m.get("id")).toList());
    }

    @Test
    void problemsSortByTitleCaseInsensitively() {
        List<Map<String, Object>> raw = new ArrayList<>(List.of(
                map("title", "problem B"), map("title", "Problem a"), map("title", "Problem C")));
        raw.sort(ApiTree.byTitle());
        assertEquals(List.of("Problem a", "problem B", "Problem C"),
                raw.stream().map(m -> m.get("title")).toList());
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
        Object[] row = ApiTree.historyRow(map(
                "submittedAt", "2026-09-10T12:00:00Z", "submittedBy", "Ada Lovelace",
                "fileName", "dfa.jff", "status", "COMPLETED", "correct", true, "feedback", "ok"),
                true, WHEN);
        assertArrayEquals(new Object[]{
                "@2026-09-10T12:00:00Z", "Ada Lovelace", "dfa.jff", "COMPLETED", "Correct", "ok"}, row);
    }

    @Test
    void individualRowHasNoMemberColumn() {
        Object[] row = ApiTree.historyRow(map(
                "submittedAt", "2026-09-10T12:00:00Z",
                "fileName", "dfa.jff", "status", "COMPLETED", "correct", false),
                false, WHEN);
        assertArrayEquals(new Object[]{
                "@2026-09-10T12:00:00Z", "dfa.jff", "COMPLETED", "Incorrect", ""}, row);
    }

    @Test
    void queuedRowSaysNotEvaluatedYet() {
        Object[] row = ApiTree.historyRow(map("status", "PENDING"), false, WHEN);
        assertEquals("Not evaluated yet", row[3]);
        Object[] processing = ApiTree.historyRow(map("status", "PROCESSING"), false, WHEN);
        assertEquals("Not evaluated yet", processing[3]);
    }

    @Test
    void finishedRowWithoutVerdictLeavesResultBlank() {
        Object[] row = ApiTree.historyRow(map("status", "FAILED"), false, WHEN);
        assertEquals("", row[3]);
    }
}
