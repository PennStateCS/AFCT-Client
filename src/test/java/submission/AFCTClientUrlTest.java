package submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AFCTClient URL normalisation logic (fixUrl) and the base-URL
 * scheme detection performed in the constructor.
 *
 * Network calls are never made – these tests are purely structural.
 */
class AFCTClientUrlTest {

    // ── fixUrl ───────────────────────────────────────────────────────────────

    @Test
    void fixUrlStripsHttpScheme() {
        assertEquals("10.0.0.1:3000", AFCTClient.fixUrl("http://10.0.0.1:3000"));
    }

    @Test
    void fixUrlStripsHttpsScheme() {
        assertEquals("10.0.0.1:443", AFCTClient.fixUrl("https://10.0.0.1:443"));
    }

    @Test
    void fixUrlStripsTrailingSlash() {
        assertEquals("10.0.0.1:3000", AFCTClient.fixUrl("http://10.0.0.1:3000/"));
    }

    @Test
    void fixUrlLeavesBareDomainUnchanged() {
        assertEquals("10.0.0.1:3000", AFCTClient.fixUrl("10.0.0.1:3000"));
    }

    @Test
    void fixUrlTrimsLeadingAndTrailingWhitespace() {
        assertEquals("10.0.0.1:3000", AFCTClient.fixUrl("  http://10.0.0.1:3000  "));
    }

    @Test
    void fixUrlHandlesEmptyString() {
        assertEquals("", AFCTClient.fixUrl(""));
    }

    // ── Constructor – scheme selection ───────────────────────────────────────

    @Test
    void constructorDoesNotThrowForHttpUrl() {
        assertDoesNotThrow(() -> new AFCTClient("http://localhost:3000", true));
    }

    @Test
    void constructorDoesNotThrowForHttpsUrl() {
        assertDoesNotThrow(() -> new AFCTClient("https://localhost:443", true));
    }

    @Test
    void constructorDoesNotThrowForNoSchemeUrl() {
        assertDoesNotThrow(() -> new AFCTClient("localhost:3000", true));
    }

    // ── isAuthenticated ──────────────────────────────────────────────────────

    @Test
    void newClientIsNotAuthenticated() {
        AFCTClient client = new AFCTClient("http://localhost:3000", true);
        assertFalse(client.isAuthenticated());
    }
}
