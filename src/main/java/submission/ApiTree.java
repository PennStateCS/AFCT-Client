package submission;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pure translation between the client API's JSON maps (the /tree payload and the
 * submission history rows) and the items the Submission Center renders. Extracted
 * from SubmitWindow so the parsing, filtering, ordering and formatting rules are
 * testable without Swing; SubmitWindow keeps only the widgets.
 *
 * The server has already resolved everything per student before this code runs:
 * only published courses inside their start/end window arrive, assignment dates are
 * the effective ones (student and group extensions applied), and maxSubmissions is
 * this student's cap with any extra-submission grants included. Nothing here may
 * second-guess those values.
 */
public final class ApiTree {

    private ApiTree() {}

    /** Parses an ISO-8601 value, or null when missing, blank, "null", or malformed. */
    public static Instant parseIsoOrNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        if (s.isBlank() || "null".equals(s)) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** A number as an int, or -1 when absent or malformed (-1 means "unknown" throughout). */
    public static int asInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String stringOrNull(Object value) {
        return value != null && !"null".equals(String.valueOf(value)) ? String.valueOf(value) : null;
    }

    public static CourseItem course(Map<String, Object> c) {
        return new CourseItem(
                String.valueOf(c.get("id")),
                String.valueOf(c.getOrDefault("name", "Untitled Course")),
                stringOrNull(c.get("timezone")));
    }

    public static AssignmentItem assignment(Map<String, Object> a) {
        Object problemsObj = a.get("problems");
        int problemCount = (problemsObj instanceof List) ? ((List<?>) problemsObj).size() : 0;
        return new AssignmentItem(
                String.valueOf(a.get("id")),
                String.valueOf(a.getOrDefault("title", "Untitled Assignment")),
                String.valueOf(a.getOrDefault("description", "")),
                stringOrNull(a.get("dueDate")),
                Boolean.TRUE.equals(a.get("isGroup")),
                stringOrNull(a.get("groupName")),
                Boolean.TRUE.equals(a.get("allowLateSubmissions")),
                stringOrNull(a.get("lateCutoff")),
                problemCount);
    }

    public static ProblemItem problem(Map<String, Object> p) {
        Object descObj = p.get("description");
        String description = (descObj != null && !"null".equals(String.valueOf(descObj)))
                ? String.valueOf(descObj) : "";
        Object msObj = p.get("maxStates");
        Integer maxStates = (msObj instanceof Number) ? ((Number) msObj).intValue() : null;
        Object detObj = p.get("isDeterministic");
        Boolean isDeterministic = (detObj instanceof Boolean) ? (Boolean) detObj : null;
        return new ProblemItem(
                String.valueOf(p.get("id")),
                String.valueOf(p.getOrDefault("title", "Untitled Problem")),
                description,
                Boolean.TRUE.equals(p.get("solved")),
                stringOrNull(p.get("type")),
                asInt(p.get("maxPoints")),
                asInt(p.get("maxSubmissions")),
                asInt(p.get("submissionCount")),
                asInt(p.get("grade")),
                maxStates,
                isDeterministic);
    }

    /**
     * Whether the assignment's due date is still ahead of `now` — which must be the
     * SERVER's clock, not the machine's, or clock skew moves work between the
     * Upcoming and past buckets. No due date, or one that fails to parse, counts as
     * not upcoming.
     */
    public static boolean isUpcoming(Map<String, Object> assignment, Instant now) {
        Instant due = parseIsoOrNull(assignment.get("dueDate"));
        return due != null && due.isAfter(now);
    }

    /** Earliest due first; assignments without a parseable due date sort last. */
    public static Comparator<Map<String, Object>> byDueDateNullsLast() {
        return (x, y) -> {
            Instant dx = parseIsoOrNull(x.get("dueDate"));
            Instant dy = parseIsoOrNull(y.get("dueDate"));
            if (dx == null && dy == null) return 0;
            if (dx == null) return 1;
            if (dy == null) return -1;
            return dx.compareTo(dy);
        };
    }

    /** Alphabetical by title, case-insensitive. */
    public static Comparator<Map<String, Object>> byTitle() {
        return Comparator.comparing(
                m -> String.valueOf(m.getOrDefault("title", "")),
                String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Formats an instant in the course's IANA timezone. Deadlines are anchored to
     * the course's zone, not the student's machine: "due 11:59 PM" must read the
     * same on a laptop set to any timezone. Falls back to the local zone only when
     * the course does not carry one.
     */
    public static String formatDueDate(Instant due, String courseTimezone) {
        ZoneId zone;
        try {
            zone = courseTimezone != null ? ZoneId.of(courseTimezone) : ZoneId.systemDefault();
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        // Locale pinned so "Sep 19, 2026 at 11:59 PM EDT" reads the same on every
        // machine; the app's UI text is English throughout.
        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("MMM d, yyyy 'at' h:mm a z", java.util.Locale.US);
        return due.atZone(zone).format(fmt);
    }

    /**
     * One submission-history table row. A group problem gains a "Group Member"
     * column after the date, because the history is the group's shared attempts and
     * any groupmate may have made one.
     */
    public static Object[] historyRow(Map<String, Object> s, boolean group,
                                      Function<Instant, String> formatWhen) {
        String when = "";
        Instant submittedAt = parseIsoOrNull(s.get("submittedAt"));
        if (submittedAt != null) {
            when = formatWhen.apply(submittedAt);
        } else if (s.get("submittedAt") != null) {
            when = String.valueOf(s.get("submittedAt"));
        }

        String file = s.get("fileName") != null ? String.valueOf(s.get("fileName")) : "";
        String status = s.get("status") != null ? String.valueOf(s.get("status")) : "";

        // Result: the evaluator verdict, blank once finished without one, and a
        // holding phrase while the submission is still queued or being graded.
        String result;
        Object correct = s.get("correct");
        if (correct instanceof Boolean) {
            result = ((Boolean) correct) ? "Correct" : "Incorrect";
        } else {
            result = "PENDING".equals(status) || "PROCESSING".equals(status) ? "Not evaluated yet" : "";
        }
        String feedback = s.get("feedback") != null ? String.valueOf(s.get("feedback")) : "";

        if (group) {
            String member = s.get("submittedBy") != null ? String.valueOf(s.get("submittedBy")) : "";
            return new Object[]{when, member, file, status, result, feedback};
        }
        return new Object[]{when, file, status, result, feedback};
    }
}
