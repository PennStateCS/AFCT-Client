package submission;

public final class ErrorMessages {
    private ErrorMessages() {}

    public static String userMessage(Throwable throwable, String fallback) {
        if (throwable == null) {
            return fallback;
        }
        return userMessage(throwable.getMessage(), fallback);
    }

    public static String userMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }
}
