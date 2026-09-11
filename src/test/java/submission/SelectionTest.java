package submission;

import org.junit.jupiter.api.Test;

import javax.swing.tree.DefaultMutableTreeNode;

import static org.junit.jupiter.api.Assertions.*;

/** Deriving the selection (and its ancestors) from a tree node, in one place. */
class SelectionTest {

    private static final CourseItem COURSE = new CourseItem("c1", "Theory", null);
    private static final AssignmentItem ASSIGNMENT =
            new AssignmentItem("a1", "HW", "", null, false, null, false, null, 1);
    private static final ProblemItem PROBLEM =
            new ProblemItem("p1", "P1", "", false, null, 100, 5, 0, -1, null, null, null);

    private static DefaultMutableTreeNode fullPathProblemNode() {
        DefaultMutableTreeNode course = new DefaultMutableTreeNode(COURSE);
        DefaultMutableTreeNode assignment = new DefaultMutableTreeNode(ASSIGNMENT);
        DefaultMutableTreeNode problem = new DefaultMutableTreeNode(PROBLEM);
        course.add(assignment);
        assignment.add(problem);
        return problem;
    }

    @Test
    void problemSelectionCarriesBothAncestors() {
        Selection s = Selection.fromNode(fullPathProblemNode());
        assertSame(PROBLEM, s.problem());
        assertSame(ASSIGNMENT, s.assignment());
        assertSame(COURSE, s.course());
        assertTrue(s.isSubmittable());
    }

    @Test
    void assignmentSelectionCarriesItsCourse() {
        DefaultMutableTreeNode course = new DefaultMutableTreeNode(COURSE);
        DefaultMutableTreeNode assignment = new DefaultMutableTreeNode(ASSIGNMENT);
        course.add(assignment);
        Selection s = Selection.fromNode(assignment);
        assertSame(ASSIGNMENT, s.assignment());
        assertSame(COURSE, s.course());
        assertNull(s.problem());
        assertFalse(s.isSubmittable());
    }

    @Test
    void courseSelectionIsJustTheCourse() {
        Selection s = Selection.fromNode(new DefaultMutableTreeNode(COURSE));
        assertSame(COURSE, s.course());
        assertNull(s.assignment());
        assertFalse(s.isSubmittable());
    }

    @Test
    void placeholdersAndNullSelectNothing() {
        assertSame(Selection.empty(), Selection.fromNode(null));
        Selection s = Selection.fromNode(new DefaultMutableTreeNode(new Placeholder("loading…")));
        assertNull(s.course());
        assertNull(s.problem());
        assertFalse(s.isSubmittable());
    }

    @Test
    void anOrphanProblemNodeIsNotSubmittable() {
        // A problem node without loaded ancestors (mid-restore) must not let
        // Submit run with a null course id.
        Selection s = Selection.fromNode(new DefaultMutableTreeNode(PROBLEM));
        assertSame(PROBLEM, s.problem());
        assertNull(s.assignment());
        assertFalse(s.isSubmittable());
    }
}
