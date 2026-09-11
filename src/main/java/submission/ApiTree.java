package submission;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.function.Function;

import static submission.ApiModels.orUnknown;
import static submission.ApiModels.parseIsoOrNull;

/**
 * Pure translation between the client API's typed responses (ApiModels) and the
 * items the Submission Center renders, plus the filter, ordering and formatting
 * rules. Extracted from SubmitWindow so all of it is testable without Swing;
 * SubmitWindow keeps only the widgets.
 *
 * The server has already resolved everything per student before this code runs:
 * only published courses inside their start/end window arrive, assignment dates are
 * the effective ones (student and group extensions applied), and maxSubmissions is
 * this student's cap with any extra-submission grants included. Nothing here may
 * second-guess those values.
 */
public final class ApiTree {

    private ApiTree() {}

    public static CourseItem course(ApiModels.Course c) {
        return new CourseItem(c.id(), c.name() != null ? c.name() : "Untitled Course", c.timezone());
    }

    public static AssignmentItem assignment(ApiModels.Assignment a) {
        return new AssignmentItem(
                a.id(),
                a.title() != null ? a.title() : "Untitled Assignment",
                a.description() != null ? a.description() : "",
                a.dueDate(),
                Boolean.TRUE.equals(a.isGroup()),
                a.groupName(),
                Boolean.TRUE.equals(a.allowLateSubmissions()),
                a.lateCutoff(),
                a.problems() != null ? a.problems().size() : 0,
                a.descriptionJson());
    }

    public static ProblemItem problem(ApiModels.Problem p) {
        return new ProblemItem(
                p.id(),
                p.title() != null ? p.title() : "Untitled Problem",
                p.description() != null ? p.description() : "",
                Boolean.TRUE.equals(p.solved()),
                p.type(),
                orUnknown(p.maxPoints()),
                orUnknown(p.maxSubmissions()),
                orUnknown(p.submissionCount()),
                orUnknown(p.grade()),
                p.maxStates(),
                p.isDeterministic(),
                p.autograderEnabled(),
                p.descriptionJson());
    }

    /**
     * Whether the assignment's due date is still ahead of `now` — which must be the
     * SERVER's clock, not the machine's, or clock skew moves work between the
     * Upcoming and past buckets. No due date, or one that fails to parse, counts as
     * not upcoming.
     */
    public static boolean isUpcoming(ApiModels.Assignment assignment, Instant now) {
        Instant due = assignment.dueInstant();
        return due != null && due.isAfter(now);
    }

    /** Earliest due first; assignments without a parseable due date sort last. */
    public static Comparator<ApiModels.Assignment> byDueDateNullsLast() {
        return Comparator.comparing(ApiModels.Assignment::dueInstant,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** Alphabetical by title, case-insensitive; missing titles sort first as "". */
    public static Comparator<ApiModels.Problem> byTitle() {
        return Comparator.comparing(p -> p.title() != null ? p.title() : "",
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
    public static Object[] historyRow(ApiModels.Submission s, boolean group,
                                      Function<Instant, String> formatWhen) {
        String when = "";
        Instant submittedAt = parseIsoOrNull(s.submittedAt());
        if (submittedAt != null) {
            when = formatWhen.apply(submittedAt);
        } else if (s.submittedAt() != null) {
            when = s.submittedAt();
        }

        String file = s.fileName() != null ? s.fileName() : "";
        String status = s.status() != null ? s.status() : "";

        // Result: the evaluator verdict; a COMPLETED run without one is a problem
        // a person grades ("not correct" and "not graded yet" are opposite things
        // to read, matching the web's wording); a queued run gets a holding phrase.
        String result;
        if (s.correct() != null) {
            result = s.correct() ? "Correct" : "Incorrect";
        } else if ("COMPLETED".equals(status)) {
            result = "Not graded";
        } else {
            result = "PENDING".equals(status) || "PROCESSING".equals(status) ? "Not evaluated yet" : "";
        }

        // Withheld is not absent: feedbackVisible=false means feedback exists and
        // this problem hides it from students, which deserves saying — an empty
        // cell reads as "the system lost my feedback".
        String feedback;
        if (Boolean.FALSE.equals(s.feedbackVisible())) {
            feedback = "(feedback is hidden for this problem)";
        } else {
            feedback = s.feedback() != null ? s.feedback() : "";
        }

        if (group) {
            String member = s.submittedBy() != null ? s.submittedBy() : "";
            return new Object[]{when, member, file, status, result, feedback};
        }
        return new Object[]{when, file, status, result, feedback};
    }
}
