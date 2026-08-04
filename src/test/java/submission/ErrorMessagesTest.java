package submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ErrorMessages utility helper.
 */
class ErrorMessagesTest {

    // ── userMessage(String, String) ─────────────────────────────────────────

    @Test
    void returnsMessageWhenPresent() {
        assertEquals("Connection refused", ErrorMessages.userMessage("Connection refused", "fallback"));
    }

    @Test
    void returnsFallbackWhenMessageIsNull() {
        assertEquals("fallback", ErrorMessages.userMessage((String) null, "fallback"));
    }

    @Test
    void returnsFallbackWhenMessageIsBlank() {
        assertEquals("fallback", ErrorMessages.userMessage("   ", "fallback"));
    }

    @Test
    void returnsFallbackWhenMessageIsEmpty() {
        assertEquals("fallback", ErrorMessages.userMessage("", "fallback"));
    }

    // ── userMessage(Throwable, String) ──────────────────────────────────────

    @Test
    void returnsThrowableMessageWhenPresent() {
        Throwable t = new RuntimeException("timeout");
        assertEquals("timeout", ErrorMessages.userMessage(t, "fallback"));
    }

    @Test
    void returnsFallbackWhenThrowableIsNull() {
        assertEquals("fallback", ErrorMessages.userMessage((Throwable) null, "fallback"));
    }

    @Test
    void returnsFallbackWhenThrowableHasNullMessage() {
        Throwable t = new RuntimeException(); // getMessage() returns null
        assertEquals("fallback", ErrorMessages.userMessage(t, "fallback"));
    }

    @Test
    void returnsFallbackWhenThrowableHasBlankMessage() {
        Throwable t = new RuntimeException("  ");
        assertEquals("fallback", ErrorMessages.userMessage(t, "fallback"));
    }
}
