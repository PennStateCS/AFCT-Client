package submission;

import java.security.cert.X509Certificate;

public class LoginResult {
    public enum LoginStatus {
        SUCCESS,
        FAILURE,
        ERROR,
        /** The server's certificate is unknown; ask the user whether to trust it. */
        UNTRUSTED_CERT,
        /** The server's certificate changed and the platform does not vouch for it. */
        CERT_CHANGED
    }

    public LoginStatus status;
    public String message;

    /** For the certificate statuses: the chain the server offered, and its origin. */
    public X509Certificate[] chain;
    public String origin;

    public LoginResult(LoginStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public static LoginResult certificateResult(LoginStatus status, String origin, X509Certificate[] chain) {
        LoginResult result = new LoginResult(status,
                status == LoginStatus.UNTRUSTED_CERT
                        ? "This server's certificate is not trusted yet."
                        : "This server's certificate has changed.");
        result.origin = origin;
        result.chain = chain;
        return result;
    }

    public static LoginResult getSuccessResult() {
        return new LoginResult(LoginStatus.SUCCESS, "Authentication Success.");
    }

    public static LoginResult getFailureResult() {
        return new LoginResult(LoginStatus.FAILURE, "Authentication Failed.");
    }

    public static LoginResult getErrorResult(String message) {
        String safe = ErrorMessages.userMessage(message, "An unexpected error occurred.");
        return new LoginResult(LoginStatus.ERROR, "Authentication Error: " + safe);
    }
}
