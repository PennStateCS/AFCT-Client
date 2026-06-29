package submission;

/**
 * Represents an assignment in the submission system.
 */
public final class AssignmentItem {

    public final String id;
    public final String name;
    public final String description;
    public final String dueDate; // For upcoming filter

    public AssignmentItem(String id, String name, String description, String dueDate) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.dueDate = dueDate;
    }

    @Override
    public String toString() {
        return name;
    }
}
