package submission;

/**
 * Represents a problem in the submission system.
 */
public final class ProblemItem {

    public final String id;
    public final String name;
    public final String description;
    public final boolean solved; // For unsolved filter

    // Metadata from the client API (-1 / null when not provided)
    public final String type;          // e.g. "FA", "PDA", "TM"
    public final int maxPoints;
    public final int maxSubmissions;   // 0 or -1 = unlimited/unknown
    public final int grade;            // -1 until graded
    public int submissionCount;        // mutable: bumped locally after a successful submit

    public ProblemItem(String id, String name, String description, boolean solved,
                       String type, int maxPoints, int maxSubmissions, int submissionCount, int grade) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.solved = solved;
        this.type = type;
        this.maxPoints = maxPoints;
        this.maxSubmissions = maxSubmissions;
        this.submissionCount = submissionCount;
        this.grade = grade;
    }

    /** Attempts remaining, or -1 if unlimited/unknown. */
    public int attemptsLeft() {
        if (maxSubmissions <= 0 || submissionCount < 0) return -1;
        return Math.max(0, maxSubmissions - submissionCount);
    }

    @Override
    public String toString() {
        return solved ? name + " ✔" : name;
    }
}
