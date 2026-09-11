package submission;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The course tree as fetched once from GET /tree, plus the filter and ordering
 * rules the Submission Center applies to it. The window's lazy expanders and the
 * filter radios read from here, so a filter toggle rebuilds the tree instantly
 * with no network; everything decision-shaped lives in this class where it can
 * be tested without Swing.
 */
class CourseTreeCache {

    private List<ApiModels.Course> courses = new ArrayList<>();
    private final Map<String, List<ApiModels.Assignment>> assignmentsByCourse = new HashMap<>();
    private final Map<String, List<ApiModels.Problem>> problemsByAssignment = new HashMap<>();

    /** Replaces the cache with a freshly fetched tree. */
    void replaceWith(ApiModels.Tree tree) {
        courses = new ArrayList<>();
        assignmentsByCourse.clear();
        problemsByAssignment.clear();
        if (tree == null || tree.courses() == null) return;

        for (ApiModels.Course c : tree.courses()) {
            courses.add(c);
            List<ApiModels.Assignment> assignments =
                    c.assignments() != null ? c.assignments() : List.of();
            assignmentsByCourse.put(c.id(), assignments);
            for (ApiModels.Assignment a : assignments) {
                problemsByAssignment.put(a.id(),
                        a.problems() != null ? a.problems() : List.of());
            }
        }
    }

    boolean isEmpty() {
        return courses.isEmpty();
    }

    /** The courses in server order (the server already sorts them for display). */
    List<ApiModels.Course> courses() {
        return List.copyOf(courses);
    }

    /**
     * A course's assignments, earliest due first (missing dates last), optionally
     * only those still due — judged against the SERVER's clock, so a skewed local
     * machine cannot move work between the Upcoming and past buckets.
     */
    List<ApiModels.Assignment> visibleAssignments(String courseId, boolean upcomingOnly, Instant serverNow) {
        List<ApiModels.Assignment> result =
                new ArrayList<>(assignmentsByCourse.getOrDefault(courseId, List.of()));
        if (upcomingOnly) {
            result.removeIf(a -> !ApiTree.isUpcoming(a, serverNow));
        }
        result.sort(ApiTree.byDueDateNullsLast());
        return result;
    }

    /** An assignment's problems, alphabetical, optionally only the unsolved ones. */
    List<ApiModels.Problem> visibleProblems(String assignmentId, boolean unsolvedOnly) {
        List<ApiModels.Problem> result =
                new ArrayList<>(problemsByAssignment.getOrDefault(assignmentId, List.of()));
        if (unsolvedOnly) {
            result.removeIf(p -> Boolean.TRUE.equals(p.solved()));
        }
        result.sort(ApiTree.byTitle());
        return result;
    }
}
