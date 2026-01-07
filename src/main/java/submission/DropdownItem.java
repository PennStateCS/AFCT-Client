package submission;

public abstract class DropdownItem {
    public String id;
    public String title;

    public DropdownItem(String id, String title) {
        this.id = id;
        this.title = title;
    }

    // Getters
    public String getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    // Setters
    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // Helpers
    public String toString() {
        return this.title;
    }
}
