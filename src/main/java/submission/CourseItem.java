package submission;

/**
 * Represents a course in the submission system.
 */
public final class CourseItem {

    public final String id;
    public final String name;

    public CourseItem(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
