package submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoginResult factory methods and status values.
 */
class LoginResultTest {

    @Test
    void successResultHasCorrectStatus() {
        LoginResult r = LoginResult.getSuccessResult();
        assertEquals(LoginResult.LoginStatus.SUCCESS, r.status);
    }

    @Test
    void successResultHasNonNullMessage() {
        assertNotNull(LoginResult.getSuccessResult().message);
    }

    @Test
    void failureResultHasCorrectStatus() {
        LoginResult r = LoginResult.getFailureResult();
        assertEquals(LoginResult.LoginStatus.FAILURE, r.status);
    }

    @Test
    void failureResultHasNonNullMessage() {
        assertNotNull(LoginResult.getFailureResult().message);
    }

    @Test
    void errorResultHasCorrectStatus() {
        LoginResult r = LoginResult.getErrorResult("something went wrong");
        assertEquals(LoginResult.LoginStatus.ERROR, r.status);
    }

    @Test
    void errorResultIncludesProvidedMessage() {
        LoginResult r = LoginResult.getErrorResult("timeout");
        assertTrue(r.message.contains("timeout"),
                "Error message should contain the detail; got: " + r.message);
    }

    @Test
    void errorResultWithNullMessageUsesFallback() {
        // Should not throw and should produce a non-null, non-blank message.
        LoginResult r = LoginResult.getErrorResult(null);
        assertEquals(LoginResult.LoginStatus.ERROR, r.status);
        assertNotNull(r.message);
        assertFalse(r.message.isBlank());
    }

    @Test
    void errorResultWithBlankMessageUsesFallback() {
        LoginResult r = LoginResult.getErrorResult("   ");
        assertNotNull(r.message);
        assertFalse(r.message.isBlank());
    }

    @Test
    void directConstructorSetsFields() {
        LoginResult r = new LoginResult(LoginResult.LoginStatus.SUCCESS, "ok");
        assertEquals(LoginResult.LoginStatus.SUCCESS, r.status);
        assertEquals("ok", r.message);
    }
}
