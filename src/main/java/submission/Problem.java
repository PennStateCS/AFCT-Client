package submission;

public class Problem {
    private String name;
    private String description;
    private long id;

    public Problem(long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String toString() {
        return this.name;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public long getId() {
        return this.id;
    }
}
