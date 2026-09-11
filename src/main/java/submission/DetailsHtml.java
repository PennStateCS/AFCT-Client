package submission;

import java.time.Instant;
import java.util.function.Function;

/**
 * The HTML shown in the Selected Assignment and Selected Problem cards, built as
 * pure functions so the wording, escaping, and the attempt-count coloring are
 * testable without Swing. SubmitWindow only pours the result into a JTextPane.
 */
public final class DetailsHtml {

    private DetailsHtml() {}

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    public static String assignmentPlaceholder() {
        return "<html><body style='font-family: sans-serif; padding: 4px; color: #888;'>"
                + "<i>Select an assignment to view details</i></body></html>";
    }

    public static String problemPlaceholder() {
        return "<html><body style='font-family: sans-serif; padding: 4px; color: #888;'>"
                + "<i>Select a problem to view details</i></body></html>";
    }

    /**
     * Title, one compact metadata line (due date, individual vs group with the
     * student's group name, late policy), then the description. Dates arrive
     * already formatted, so the caller decides the timezone.
     */
    public static String assignmentDetails(AssignmentItem assignment, Function<Instant, String> formatDueDate) {
        String title = assignment.name != null ? assignment.name : "Untitled Assignment";
        String descriptionHtml = descriptionHtml(assignment.descriptionJson, assignment.description,
                "No description available.");

        StringBuilder meta = new StringBuilder();
        Instant due = assignment.dueInstant();
        if (due != null) {
            meta.append("<b>Due:</b> ").append(escapeHtml(formatDueDate.apply(due)));
        }

        String typeText = assignment.isGroup
                ? (assignment.groupName != null && !assignment.groupName.isBlank()
                    ? "Group (your group: " + escapeHtml(assignment.groupName) + ")"
                    : "Group")
                : "Individual";
        if (meta.length() > 0) meta.append(" &nbsp;&middot;&nbsp; ");
        meta.append(typeText);

        String lateText;
        if (assignment.allowLateSubmissions) {
            Instant cutoff = assignment.lateCutoffInstant();
            lateText = cutoff != null
                    ? "Late until " + escapeHtml(formatDueDate.apply(cutoff))
                    : "Late accepted";
        } else {
            lateText = "No late submissions";
        }
        meta.append(" &nbsp;&middot;&nbsp; ").append(lateText);

        return String.format(
                "<html><body style='font-family: sans-serif; padding: 4px;'>"
                        + "<h3 style='margin: 0 0 4px 0; color: #000000;'>%s</h3>"
                        + "<p style='margin: 0 0 6px 0; color: #555555;'>%s</p>%s"
                        + "</body></html>",
                escapeHtml(title),
                meta,
                descriptionHtml);
    }

    /**
     * Title, description, and one compact metadata line: type, the intrinsic
     * FA/PDA constraints when they apply, points, grade, and the submissions-used
     * count, colored by how many attempts remain. The cap arrives grant-adjusted
     * from the server, so the coloring reflects any extra submissions granted.
     */
    public static String problemDetails(ProblemItem problem) {
        String title = problem.name != null ? problem.name : "Untitled Problem";

        String descriptionHtml = descriptionHtml(problem.descriptionJson, problem.description,
                "No description available");

        StringBuilder meta = new StringBuilder();
        String typeName = problem.typeFullName();
        if (typeName != null) meta.append("Type: ").append(escapeHtml(typeName));
        // A non-positive cap (e.g. -1) means "no limit", so only show a real cap.
        if (problem.maxStates != null && problem.maxStates > 0) {
            sep(meta).append("Max states: ").append(problem.maxStates);
        }
        if (problem.isDeterministic != null) {
            sep(meta).append("Deterministic: ").append(problem.isDeterministic ? "Yes" : "No");
        }
        if (Boolean.FALSE.equals(problem.autograderEnabled)) {
            // Sets expectations: no instant verdict, and the check mark waits for a person.
            sep(meta).append("Graded by your instructor");
        }
        if (problem.maxPoints >= 0) {
            sep(meta).append("Points: ").append(problem.maxPoints);
        }
        if (problem.grade >= 0) {
            sep(meta).append("Grade: ").append(problem.grade)
                    .append(problem.maxPoints >= 0 ? " / " + problem.maxPoints : "");
        }
        if (problem.submissionCount >= 0 && problem.maxSubmissions > 0) {
            int left = problem.attemptsLeft();
            String color = left == 0 ? "#c0392b" : (left == 1 ? "#e67e22" : "#27ae60");
            String warn = left == 0 ? " (limit reached)" : (left == 1 ? " (last attempt)" : "");
            sep(meta).append("<span style='color:").append(color).append(";'><b>Submissions: ")
                    .append(problem.submissionCount).append(" / ").append(problem.maxSubmissions)
                    .append(warn).append("</b></span>");
        } else if (problem.submissionCount >= 0) {
            sep(meta).append("Submissions: ").append(problem.submissionCount).append(" (no limit)");
        }
        String metaHtml = meta.length() > 0
                ? "<p style='margin: 0; color: #555555;'>" + meta + "</p>" : "";

        return String.format(
                "<html><body style='font-family: sans-serif; padding: 4px;'>"
                        + "<h3 style='margin: 0 0 4px 0; color: #000000;'>%s</h3>%s%s"
                        + "</body></html>",
                escapeHtml(title),
                descriptionHtml,
                metaHtml);
    }

    /**
     * The description block: the rich source rendered when the server sent one and
     * it parses, else the plain-text projection, else the placeholder. The plain
     * fallback matters: an envelope from a newer format version renders as the
     * text the server always ships alongside, never as nothing.
     */
    private static String descriptionHtml(com.fasterxml.jackson.databind.JsonNode rich,
                                          String plain, String placeholder) {
        String richHtml = RichTextHtml.render(rich, RichTextHtml.MATH_AS_CODE);
        if (richHtml != null && !richHtml.isBlank()) {
            return "<div style='margin: 0 0 6px 0; color: #000000;'>" + richHtml + "</div>";
        }
        String text = plain != null && !plain.isBlank() && !plain.equals("null") ? plain : placeholder;
        return "<p style='margin: 0 0 6px 0; color: #000000;'>" + escapeHtml(text) + "</p>";
    }

    private static StringBuilder sep(StringBuilder meta) {
        if (meta.length() > 0) meta.append(" &nbsp;&middot;&nbsp; ");
        return meta;
    }
}
