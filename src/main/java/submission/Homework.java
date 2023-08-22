package submission;

import java.util.ArrayList;

public class Homework {
    private String name;
    private long id;
    private ArrayList<Problem> problems;

    public Homework(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public String toString() {
        return this.name;
    }

    public long getId() {
        return this.id;
    }

    public ArrayList<Problem> getProblems() {
        return this.problems;
    }

    public void setProblems(ArrayList<Problem> problems) {
        this.problems = problems;
    }
}
