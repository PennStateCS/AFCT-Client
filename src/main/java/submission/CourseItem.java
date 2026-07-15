package submission;

/**
 * Represents a course in the submission system.
 */
public final class CourseItem {

    public final String id;
    public final String name;
    /** IANA timezone the course's deadlines are anchored to, e.g. "America/New_York". May be null. */
    public final String timezone;

    public CourseItem(String id, String name, String timezone) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
    }

    @Override
    public String toString() {
        return name;
    }
}
