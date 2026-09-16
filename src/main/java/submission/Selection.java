package submission;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * What is selected in the course tree, derived from the selected node in one
 * place. Replaces four parallel mutable fields that were written from tree
 * events, refresh-restore, and the submit path — the drift between them was a
 * standing bug risk.
 *
 * Selecting a problem carries its assignment and course (the node's ancestors);
 * selecting an assignment carries its course; anything else is empty.
 */
record Selection(CourseItem course, AssignmentItem assignment, ProblemItem problem,
                 DefaultMutableTreeNode node) {

    private static final Selection EMPTY = new Selection(null, null, null, null);

    static Selection empty() {
        return EMPTY;
    }

    static Selection fromNode(DefaultMutableTreeNode node) {
        if (node == null) return EMPTY;
        Object uo = node.getUserObject();

        if (uo instanceof ProblemItem problem) {
            AssignmentItem assignment = null;
            CourseItem course = null;
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null && parent.getUserObject() instanceof AssignmentItem a) {
                assignment = a;
                DefaultMutableTreeNode grand = (DefaultMutableTreeNode) parent.getParent();
                if (grand != null && grand.getUserObject() instanceof CourseItem c) {
                    course = c;
                }
            }
            return new Selection(course, assignment, problem, node);
        }
        if (uo instanceof AssignmentItem assignment) {
            CourseItem course = null;
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null && parent.getUserObject() instanceof CourseItem c) {
                course = c;
            }
            return new Selection(course, assignment, null, node);
        }
        if (uo instanceof CourseItem course) {
            return new Selection(course, null, null, node);
        }
        // Placeholders and the invisible root select nothing.
        return EMPTY;
    }

    /** A problem plus both its ancestors: the state Submit requires. */
    boolean isSubmittable() {
        return problem != null && assignment != null && course != null;
    }
}
