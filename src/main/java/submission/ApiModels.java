package submission;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * The client API's response shapes, as records Jackson binds directly. Replaces
 * the untyped Map plumbing: a field the server did not send is null here, once,
 * instead of a String.valueOf/"null"-string check at every use site.
 *
 * Every record ignores unknown fields on purpose: the server releases separately
 * and may add fields before this client learns about them.
 *
 * Dates stay ISO-8601 strings as sent (helpers parse on demand), so no extra
 * Jackson module is needed and "present but unparseable" stays distinguishable.
 */
public final class ApiModels {

    private ApiModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(String id, String email, String firstName, String lastName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginResponse(String token, String expiresAt, User user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeResponse(User user, String expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Problem(
            String id,
            String title,
            String description,
            String type,
            Integer maxStates,
            Boolean isDeterministic,
            Integer maxPoints,
            /** This student's cap, extra-submission grants already included. */
            Integer maxSubmissions,
            Integer submissionCount,
            Integer grade,
            String status,
            Boolean solved,
            /** False when a person grades this problem rather than the autograder. */
            Boolean autograderEnabled) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Assignment(
            String id,
            String title,
            String description,
            /** Effective for this student: extensions (student or group) already applied. */
            String dueDate,
            String unlockAt,
            String lateCutoff,
            Boolean allowLateSubmissions,
            Boolean isGroup,
            String groupName,
            List<Problem> problems) {

        public Instant dueInstant() {
            return parseIsoOrNull(dueDate);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Course(
            String id,
            String name,
            String code,
            String semester,
            /** IANA zone the course's deadlines are anchored to. */
            String timezone,
            Boolean isPublished,
            Boolean isArchived,
            String role,
            List<Assignment> assignments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tree(String serverTime, List<Course> courses) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Submission(
            String id,
            String status,
            Boolean correct,
            String submittedAt,
            String fileName,
            String feedback,
            Boolean feedbackVisible,
            /** Display name of the group member who submitted; null on individual work. */
            String submittedBy,
            Integer grade) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubmissionList(List<Submission> submissions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateResult(String submissionId, String status) {}

    /** Parses an ISO-8601 string, or null when missing or malformed. */
    public static Instant parseIsoOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** A nullable count as an int, -1 meaning "unknown" as throughout the UI. */
    public static int orUnknown(Integer value) {
        return value != null ? value : -1;
    }
}
