package submission;

// Class used as items for assignment drop-down menu
public class AssignmentItem {
    String id;
    String title;

    AssignmentItem(String id, String title){
        this.id = id;
        this.title = title;
    }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }

    // Getters
    public String getId() { return this.id; }
    public String getTitle() { return this.title; }

    // Helpers
    public String toString() { return this.title; }

}
