package submission;

public class LoginResult {
    public enum LoginStatus {
        SUCCESS,
        FAILURE,
        ERROR
    }

    public LoginStatus status;
    public String message;

    public LoginResult(LoginStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public static LoginResult getSuccessResult() {
        return new LoginResult(LoginStatus.SUCCESS, "Authentication Success.");
    }

    public static LoginResult getFailureResult() {
        return new LoginResult(LoginStatus.FAILURE, "Authentication Failed.");
    }

    public static LoginResult getErrorResult(String message) {
        return new LoginResult(LoginStatus.ERROR, "Authentication error: " + message);
    }
}
