package submission;

/**
 * Represents a problem in the submission system.
 */
public final class ProblemItem {

    public final String id;
    public final String name;
    public final String description;
    public final boolean solved; // For unsolved filter

    public ProblemItem(String id, String name, String description, boolean solved) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.solved = solved;
    }

    @Override
    public String toString() {
        return solved ? name + " ✔" : name;
    }
}
