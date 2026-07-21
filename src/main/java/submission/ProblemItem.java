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

    /** FA/PDA state cap, or null when the problem sets no cap. */
    public final Integer maxStates;
    /** FA determinism requirement, or null when it does not apply. */
    public final Boolean isDeterministic;

    public ProblemItem(String id, String name, String description, boolean solved,
                       String type, int maxPoints, int maxSubmissions, int submissionCount, int grade,
                       Integer maxStates, Boolean isDeterministic) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.solved = solved;
        this.type = type;
        this.maxPoints = maxPoints;
        this.maxSubmissions = maxSubmissions;
        this.submissionCount = submissionCount;
        this.grade = grade;
        this.maxStates = maxStates;
        this.isDeterministic = isDeterministic;
    }

    /** The problem type's full display name, e.g. "Finite Automaton" for "FA". */
    public String typeFullName() {
        if (type == null) return null;
        switch (type) {
            case "FA":  return "Finite Automaton";
            case "PDA": return "Pushdown Automaton";
            case "CFG": return "Context-Free Grammar";
            case "RE":  return "Regular Expression";
            case "TM":  return "Turing Machine";
            default:    return type;
        }
    }

    /** Attempts remaining, or -1 if unlimited/unknown. */
    public int attemptsLeft() {
        if (maxSubmissions <= 0 || submissionCount < 0) return -1;
        return Math.max(0, maxSubmissions - submissionCount);
    }

    @Override
    public String toString() {
        // Solved state is shown by the green check icon in the tree, not the text.
        return name;
    }
}
