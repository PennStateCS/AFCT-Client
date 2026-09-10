package submission;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The filter and ordering rules the Submission Center's tree applies to the cached fetch. */
class CourseTreeCacheTest {

    private static ApiModels.Problem problem(String title, Boolean solved) {
        return new ApiModels.Problem("p-" + title, title, null, null, null, null,
                100, null, null, null, null, solved);
    }

    private static ApiModels.Assignment assignment(String id, String due, List<ApiModels.Problem> problems) {
        return new ApiModels.Assignment(id, id, null, due, null, null, null, null, null, problems);
    }

    private static ApiModels.Course course(String id, List<ApiModels.Assignment> assignments) {
        return new ApiModels.Course(id, id, null, null, null, null, null, null, assignments);
    }

    private static CourseTreeCache cache(ApiModels.Course... courses) {
        CourseTreeCache cache = new CourseTreeCache();
        cache.replaceWith(new ApiModels.Tree("2026-09-10T12:00:00Z", List.of(courses)));
        return cache;
    }

    @Test
    void emptyAndNullTreesAreEmpty() {
        CourseTreeCache cache = new CourseTreeCache();
        assertTrue(cache.isEmpty());
        cache.replaceWith(null);
        assertTrue(cache.isEmpty());
        cache.replaceWith(new ApiModels.Tree("t", null));
        assertTrue(cache.isEmpty());
    }

    @Test
    void assignmentsSortEarliestDueFirstMissingLast() {
        CourseTreeCache cache = cache(course("c1", List.of(
                assignment("none", null, null),
                assignment("late", "2026-12-01T00:00:00Z", null),
                assignment("soon", "2026-09-15T00:00:00Z", null))));
        assertEquals(List.of("soon", "late", "none"),
                cache.visibleAssignments("c1", false, Instant.now())
                        .stream().map(ApiModels.Assignment::id).toList());
    }

    @Test
    void upcomingFilterUsesTheGivenServerClock() {
        Instant serverNow = Instant.parse("2026-09-10T12:00:00Z");
        CourseTreeCache cache = cache(course("c1", List.of(
                assignment("past", "2026-09-10T11:59:59Z", null),
                assignment("future", "2026-09-10T12:00:01Z", null),
                assignment("undated", null, null))));
        assertEquals(List.of("future"),
                cache.visibleAssignments("c1", true, serverNow)
                        .stream().map(ApiModels.Assignment::id).toList());
    }

    @Test
    void unsolvedFilterDropsSolvedProblemsAndSortsByTitle() {
        CourseTreeCache cache = cache(course("c1", List.of(
                assignment("a1", null, List.of(
                        problem("b solved", true),
                        problem("C open", false),
                        problem("a open", null))))));
        assertEquals(List.of("a open", "C open"),
                cache.visibleProblems("a1", true)
                        .stream().map(ApiModels.Problem::title).toList());
        assertEquals(3, cache.visibleProblems("a1", false).size());
    }

    @Test
    void unknownIdsReturnEmptyListsNotNull() {
        CourseTreeCache cache = cache(course("c1", null));
        assertTrue(cache.visibleAssignments("nope", false, Instant.now()).isEmpty());
        assertTrue(cache.visibleProblems("nope", false).isEmpty());
    }

    @Test
    void replaceWithDiscardsThePreviousFetch() {
        CourseTreeCache cache = cache(course("old", List.of(assignment("a-old", null, null))));
        cache.replaceWith(new ApiModels.Tree("t", List.of(course("new", null))));
        assertTrue(cache.visibleAssignments("old", false, Instant.now()).isEmpty());
        assertEquals("new", cache.courses().get(0).id());
    }
}
