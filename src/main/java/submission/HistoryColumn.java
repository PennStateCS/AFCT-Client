package submission;

import java.util.List;

/**
 * The submission-history table's columns, in order. The row builder, the width
 * table, and the renderer all consult this one definition, so a renamed header
 * can no longer silently break the widths or the status-pill styling (they used
 * to match on the header string).
 */
enum HistoryColumn {
    SUBMITTED("Submitted", 160, true),
    /** Present only on a group problem: who made the shared attempt. */
    GROUP_MEMBER("Group Member", 130, true),
    FILE("File", 160, true),
    STATUS("Status", 100, false),
    RESULT("Result", 90, true),
    FEEDBACK("Feedback", 240, true);

    final String title;
    final int preferredWidth;
    /** Whether the cell text wraps (everything except the status pill). */
    final boolean wraps;

    HistoryColumn(String title, int preferredWidth, boolean wraps) {
        this.title = title;
        this.preferredWidth = preferredWidth;
        this.wraps = wraps;
    }

    /** The columns shown for an individual vs a group problem, in table order. */
    static List<HistoryColumn> forGroup(boolean group) {
        return group
                ? List.of(SUBMITTED, GROUP_MEMBER, FILE, STATUS, RESULT, FEEDBACK)
                : List.of(SUBMITTED, FILE, STATUS, RESULT, FEEDBACK);
    }

    static Object[] titles(List<HistoryColumn> columns) {
        return columns.stream().map(c -> c.title).toArray();
    }
}
