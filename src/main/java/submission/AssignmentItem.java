package submission;

/**
 * Represents an assignment in the submission system.
 */
public final class AssignmentItem {

    public final String id;
    public final String name;
    public final String description;
    public final String dueDate; // raw UTC ISO-8601 string from the server, e.g. "2026-01-15T04:59:00.000Z"

    public AssignmentItem(String id, String name, String description, String dueDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
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
