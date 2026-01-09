package submission;

// Class used as items for assignment drop-down menu
public class AssignmentItem extends DropdownItem {
    public String description;

    public AssignmentItem(String id, String title) {
        super(id, title);
        this.description = "";
    }

    public AssignmentItem(String id, String title, String description) {
        super(id, title);
        this.description = description;
    }
}
