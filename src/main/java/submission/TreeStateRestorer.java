package submission;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Puts the Submission Center's tree back the way the student had it after a
 * Refresh or a filter change rebuilds it: every branch they had expanded, then
 * the selection. Branches load lazily and one at a time, so this works as a
 * pump: {@link #pump} expands the next remembered branch that still needs its
 * children, the triggered load's done() calls pump again, and when nothing is
 * left the remembered selection is re-applied.
 */
class TreeStateRestorer {

    private final JTree tree;
    /** Whether a node's children are real loaded items of the class, not placeholders. */
    private final BiPredicate<DefaultMutableTreeNode, Class<?>> hasRealChildren;

    private String courseId;
    private String assignmentId;
    private String problemId;
    private final Set<String> expandedCourseIds = new HashSet<>();
    private final Set<String> expandedAssignmentIds = new HashSet<>();
    private boolean inProgress = false;

    TreeStateRestorer(JTree tree, BiPredicate<DefaultMutableTreeNode, Class<?>> hasRealChildren) {
        this.tree = tree;
        this.hasRealChildren = hasRealChildren;
    }

    boolean inProgress() {
        return inProgress;
    }

    void clear() {
        courseId = null;
        assignmentId = null;
        problemId = null;
        expandedCourseIds.clear();
        expandedAssignmentIds.clear();
        inProgress = false;
    }

    /**
     * Records the current selection and every expanded course and assignment,
     * and arms the pump when there is anything to put back.
     */
    void capture(DefaultMutableTreeNode rootNode,
                 CourseItem selectedCourse, AssignmentItem selectedAssignment, ProblemItem selectedProblem) {
        courseId = selectedCourse != null ? selectedCourse.id : null;
        assignmentId = selectedAssignment != null ? selectedAssignment.id : null;
        problemId = selectedProblem != null ? selectedProblem.id : null;

        expandedCourseIds.clear();
        expandedAssignmentIds.clear();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode courseNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (!(courseNode.getUserObject() instanceof CourseItem course)) continue;
            if (!tree.isExpanded(new TreePath(courseNode.getPath()))) continue;
            expandedCourseIds.add(course.id);
            for (int j = 0; j < courseNode.getChildCount(); j++) {
                DefaultMutableTreeNode aNode = (DefaultMutableTreeNode) courseNode.getChildAt(j);
                if (!(aNode.getUserObject() instanceof AssignmentItem a)) continue;
                if (tree.isExpanded(new TreePath(aNode.getPath()))) {
                    expandedAssignmentIds.add(a.id);
                }
            }
        }
        inProgress = !expandedCourseIds.isEmpty() || courseId != null;
    }

    /**
     * Re-expands the next remembered branch that still needs its children loaded,
     * then returns; when everything is back, re-applies the selection and ends.
     * The caller gates this on not currently loading.
     */
    void pump(DefaultMutableTreeNode rootNode) {
        if (!inProgress) return;

        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode courseNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (!(courseNode.getUserObject() instanceof CourseItem course)) continue;
            if (!expandedCourseIds.contains(course.id)) continue;

            if (!hasRealChildren.test(courseNode, AssignmentItem.class)) {
                // Its assignments are not loaded yet; expanding kicks off that load.
                tree.expandPath(new TreePath(courseNode.getPath()));
                return;
            }
            // Assignments are present; make sure the course shows as expanded, then look
            // for a remembered assignment under it that still needs its problems.
            tree.expandPath(new TreePath(courseNode.getPath()));
            for (int j = 0; j < courseNode.getChildCount(); j++) {
                DefaultMutableTreeNode aNode = (DefaultMutableTreeNode) courseNode.getChildAt(j);
                if (!(aNode.getUserObject() instanceof AssignmentItem a)) continue;
                if (!expandedAssignmentIds.contains(a.id)) continue;
                if (!hasRealChildren.test(aNode, ProblemItem.class)) {
                    tree.expandPath(new TreePath(aNode.getPath()));
                    return;
                }
                tree.expandPath(new TreePath(aNode.getPath()));
            }
        }

        // Everything the user had expanded is back; restore the selection and finish.
        finish(rootNode);
    }

    /** Re-selects the remembered course/assignment/problem (their ancestors are now loaded). */
    private void finish(DefaultMutableTreeNode rootNode) {
        DefaultMutableTreeNode courseNode = findChildById(rootNode, CourseItem.class, courseId);
        if (courseNode != null) {
            DefaultMutableTreeNode target = courseNode;
            if (assignmentId != null) {
                DefaultMutableTreeNode aNode = findChildById(courseNode, AssignmentItem.class, assignmentId);
                if (aNode != null) {
                    target = aNode;
                    if (problemId != null) {
                        DefaultMutableTreeNode pNode = findChildById(aNode, ProblemItem.class, problemId);
                        if (pNode != null) target = pNode;
                    }
                }
            }
            TreePath path = new TreePath(target.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
        clear();
    }

    /** A direct child of {@code parent} whose user object is a {@code type} with the given id. */
    private static DefaultMutableTreeNode findChildById(DefaultMutableTreeNode parent, Class<?> type, String id) {
        if (id == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object uo = child.getUserObject();
            if (!type.isInstance(uo)) continue;
            String childId = uo instanceof CourseItem c ? c.id
                    : uo instanceof AssignmentItem a ? a.id
                    : uo instanceof ProblemItem p ? p.id
                    : null;
            if (id.equals(childId)) return child;
        }
        return null;
    }
}
