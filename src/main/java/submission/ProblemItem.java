package submission;

// Class used as items for problem drop-down menu
public class ProblemItem extends DropdownItem {
    public String description;

    public ProblemItem(String id, String title) {
        super(id, title);
        this.description = "";
    }

    public ProblemItem(String id, String title, String description) {
        super(id, title);
        this.description = description;
    }
}
