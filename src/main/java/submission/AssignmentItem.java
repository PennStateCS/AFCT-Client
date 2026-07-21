package submission;

/**
 * Represents an assignment in the submission system.
 */
public final class AssignmentItem {

    public final String id;
    public final String name;
    public final String description;
    public final String dueDate; // raw UTC ISO-8601 string from the server, e.g. "2026-01-15T04:59:00.000Z"

    /** True when this is a group assignment (server field {@code isGroup}). */
    public final boolean isGroup;
    /** The caller's group name for a group assignment they belong to, else null. */
    public final String groupName;
    /** Whether the assignment accepts late submissions (server field {@code allowLateSubmissions}). */
    public final boolean allowLateSubmissions;

    public AssignmentItem(String id, String name, String description, String dueDate) {
        this(id, name, description, dueDate, false, null, false);
    }

    public AssignmentItem(String id, String name, String description, String dueDate,
                          boolean isGroup, String groupName, boolean allowLateSubmissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
        this.isGroup = isGroup;
        this.groupName = groupName;
        this.allowLateSubmissions = allowLateSubmissions;
    }

    /** Parses {@link #dueDate} as an Instant, or null if missing/unparseable. */
    public java.time.Instant dueInstant() {
        if (dueDate == null || dueDate.isBlank() || "null".equals(dueDate)) return null;
        try {
            return java.time.Instant.parse(dueDate);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
