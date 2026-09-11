package submission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ServerAddress.parse, the single point of URL normalisation behind the
 * login window's one Server address field. Replaces the old fixUrl tests.
 */
class ServerAddressTest {

    @Test
    void noSchemeDefaultsToHttps() {
        ServerAddress a = ServerAddress.parse("afct.example.edu");
        assertEquals("https", a.scheme());
        assertEquals(443, a.port());
        assertEquals("https://afct.example.edu", a.baseUrl());
    }

    @Test
    void explicitHttpIsKept() {
        ServerAddress a = ServerAddress.parse("http://10.0.0.1:3000");
        assertEquals("http", a.scheme());
        assertEquals("10.0.0.1", a.host());
        assertEquals(3000, a.port());
        assertEquals("http://10.0.0.1:3000", a.baseUrl());
    }

    @Test
    void defaultPortIsOmittedFromBaseUrl() {
        assertEquals("https://afct.example.edu", ServerAddress.parse("https://afct.example.edu:443").baseUrl());
        assertEquals("http://afct.example.edu", ServerAddress.parse("http://afct.example.edu:80").baseUrl());
    }

    @Test
    void nonDefaultPortIsKept() {
        assertEquals("https://afct.example.edu:8443", ServerAddress.parse("afct.example.edu:8443").baseUrl());
    }

    @Test
    void trailingSlashAndPathAreDropped() {
        assertEquals("https://afct.example.edu", ServerAddress.parse("https://afct.example.edu/").baseUrl());
        assertEquals("https://afct.example.edu", ServerAddress.parse("https://afct.example.edu/dashboard?x=1").baseUrl());
    }

    @Test
    void whitespaceIsTrimmed() {
        assertEquals("https://afct.example.edu", ServerAddress.parse("  afct.example.edu  ").baseUrl());
    }

    @Test
    void emptyInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(null));
    }

    @Test
    void garbageThrows() {
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("https://"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("http://:3000"));
    }

    @Test
    void userinfoIsRejected() {
        // user@host addresses are how phishing links dress up; a server address has
        // no business carrying credentials.
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("https://evil@afct.example.edu"));
    }

    // ── AFCTClient constructor on top of the parser ──────────────────────────

    @Test
    void clientConstructorAcceptsCommonForms() {
        assertDoesNotThrow(() -> new AFCTClient("http://localhost:3000"));
        assertDoesNotThrow(() -> new AFCTClient("https://localhost"));
        assertDoesNotThrow(() -> new AFCTClient("localhost:3000"));
    }

    @Test
    void newClientIsNotAuthenticated() {
        AFCTClient client = new AFCTClient("http://localhost:3000");
        assertFalse(client.isAuthenticated());
    }

    // ── isLoopback ───────────────────────────────────────────────────────────

    @Test
    void loopbackHostsAreRecognized() {
        assertTrue(ServerAddress.parse("http://localhost:3000").isLoopback());
        assertTrue(ServerAddress.parse("http://127.0.0.1:3000").isLoopback());
        assertTrue(ServerAddress.parse("http://[::1]:3000").isLoopback());
        assertFalse(ServerAddress.parse("http://10.0.0.1:3000").isLoopback());
        assertFalse(ServerAddress.parse("afct.example.edu").isLoopback());
    }
}
