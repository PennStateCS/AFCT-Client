package submission;

import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SessionHandler credential management, preference helpers, and
 * login URL-building logic.
 *
 * The SessionHandler constructor creates a LoginWindow (a Swing dialog).
 * In headless test environments we intercept that constructor call with
 * Mockito's MockedConstruction so no real GUI is opened.
 *
 * Tests that need a live AFCT server are skipped here (network calls are
 * never made). Those belong in integration / end-to-end tests run against
 * a real or stub server.
 */
class SessionHandlerTest {

    /**
     * Creates a SessionHandler with its LoginWindow mocked out (no GUI).
     * The caller is responsible for closing the returned MockedConstruction.
     */
    private record Harness(MockedConstruction<LoginWindow> mock, SessionHandler handler)
            implements AutoCloseable {
        @Override
        public void close() {
            mock.close();
        }
    }

    private Harness open() {
        MockedConstruction<LoginWindow> mock =
                Mockito.mockConstruction(LoginWindow.class);
        return new Harness(mock, new SessionHandler());
    }

    private void wipePrefs() throws BackingStoreException {
        Preferences prefs = Preferences.userNodeForPackage(SessionHandler.class);
        prefs.remove(SessionHandler.PREF_SERVER);
        prefs.remove(SessionHandler.PREF_PORT);
        prefs.remove(SessionHandler.PREF_EMAIL);
        prefs.remove(SessionHandler.PREF_PASSWORD);
        prefs.remove(SessionHandler.PREF_PASSWORD_ENCRYPTED);
        prefs.remove(SessionHandler.PREF_PASSWORD_SALT);
        prefs.remove(SessionHandler.PREF_REMEMBER_ME);
        prefs.remove(SessionHandler.PREF_HAS_USED_SAVED_CREDS);
        prefs.remove(SessionHandler.PREF_SAVED_CREDS_EXPIRE_AT_MS);
        prefs.remove(SessionHandler.PREF_SAVED_CREDS_EXPIRE_AFTER);
        prefs.flush();
    }

    /** Wipe the test-node preferences before/after each test so runs don't bleed into each other. */
    @BeforeEach
    void cleanPrefsBefore() throws BackingStoreException {
        wipePrefs();
    }

    @AfterEach
    void cleanPrefsAfter() throws BackingStoreException {
        wipePrefs();
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    void newHandlerIsNotLoggedIn() {
        try (var h = open()) {
            assertFalse(h.handler().loggedIn);
        }
    }

    @Test
    void newHandlerHasNullEmail() {
        try (var h = open()) {
            assertNull(h.handler().getUserEmail());
        }
    }

    @Test
    void requireAuthenticatedReturnsNullWhenNotLoggedIn() {
        try (var h = open()) {
            // No login attempted – must return null and not block.
            assertNull(h.handler().requireAuthenticated());
        }
    }

    // ── Default preferences ──────────────────────────────────────────────────

    @Test
    void savedServerDefaultsToConstant() {
        try (var h = open()) {
            assertEquals(SessionHandler.defaultServer, h.handler().getSavedServer());
        }
    }

    @Test
    void savedPortDefaultsToConstant() {
        try (var h = open()) {
            assertEquals(SessionHandler.defaultPort, h.handler().getSavedPort());
        }
    }

    @Test
    void savedEmailDefaultsToConstant() {
        try (var h = open()) {
            assertEquals(SessionHandler.defaultEmail, h.handler().getSavedEmail());
        }
    }

    @Test
    void savedPasswordDefaultsToEmpty() {
        try (var h = open()) {
            assertEquals(SessionHandler.defaultPassword, h.handler().getSavedPassword());
        }
    }

    @Test
    void rememberMeDefaultsToFalse() {
        try (var h = open()) {
            assertFalse(h.handler().hasRememberMe());
        }
    }

    // ── saveCredentials / clearSavedCredentials ──────────────────────────────

    @Test
    void saveCredentialsPersistsServerAndPort() {
        try (var h = open()) {
            h.handler().saveCredentials("https://10.0.0.1", "3000", "a@b.com", "secret");
            assertEquals("https://10.0.0.1", h.handler().getSavedServer());
            assertEquals("3000", h.handler().getSavedPort());
        }
    }

    @Test
    void saveCredentialsPersistsEmail() {
        try (var h = open()) {
            h.handler().saveCredentials("https://10.0.0.1", "3000", "student@rit.edu", "pw");
            assertEquals("student@rit.edu", h.handler().getSavedEmail());
        }
    }

    @Test
    void saveCredentialsPersistsPasswordRoundTrip() {
        try (var h = open()) {
            h.handler().saveCredentials("https://10.0.0.1", "3000", "a@b.com", "mypassword");
            assertEquals("mypassword", h.handler().getSavedPassword());
        }
    }

    @Test
    void saveCredentialsSetsRememberMe() {
        try (var h = open()) {
            h.handler().saveCredentials("https://10.0.0.1", "3000", "a@b.com", "pw");
            assertTrue(h.handler().hasRememberMe());
        }
    }

    @Test
    void clearSavedCredentialsClearsRememberMe() {
        try (var h = open()) {
            h.handler().saveCredentials("https://10.0.0.1", "3000", "a@b.com", "pw");
            h.handler().clearSavedCredentials();
            assertFalse(h.handler().hasRememberMe());
        }
    }

    @Test
    void clearSavedCredentialsClearsPassword() {
        try (var h = open()) {
            h.handler().saveCredentials("https://10.0.0.1", "3000", "a@b.com", "pw");
            h.handler().clearSavedCredentials();
            assertEquals(SessionHandler.defaultPassword, h.handler().getSavedPassword());
        }
    }

    // ── Encryption round-trip ────────────────────────────────────────────────

    @Test
    void passwordEncryptDecryptRoundTrip() {
        // Save → getSavedPassword decrypts; if values match, AES-GCM round-trip works.
        try (var h = open()) {
            String plain = "SuperSecret123!";
            h.handler().saveCredentials("https://localhost", "443", "x@y.com", plain);
            assertEquals(plain, h.handler().getSavedPassword());
        }
    }

    @Test
    void passwordEncryptedKeyIsPresentAfterSave() {
        try (var h = open()) {
            h.handler().saveCredentials("https://localhost", "443", "x@y.com", "pw");
            Preferences prefs = h.handler().preferences;
            String enc = prefs.get(SessionHandler.PREF_PASSWORD_ENCRYPTED, null);
            assertNotNull(enc, "Encrypted key must be present after saveCredentials");
            assertTrue(enc.startsWith("v2:"), "Encrypted value must use v2 format; got: " + enc);
        }
    }

    @Test
    void plaintextPasswordKeyIsAbsentAfterSave() {
        try (var h = open()) {
            h.handler().saveCredentials("https://localhost", "443", "x@y.com", "pw");
            Preferences prefs = h.handler().preferences;
            // Legacy plaintext key must NOT be written.
            assertNull(prefs.get(SessionHandler.PREF_PASSWORD, null));
        }
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Test
    void logoutClearsLoggedInFlag() {
        try (var h = open()) {
            // Manually set to simulate post-login state without a network call.
            h.handler().loggedIn = true;
            h.handler().logout();
            assertFalse(h.handler().loggedIn);
        }
    }

    // ── login() – offline error paths ────────────────────────────────────────

    @Test
    void loginWithEmptyServerReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().login("", "3000", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
            assertNotNull(r.message);
        }
    }

    @Test
    void loginWithBlankServerReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().login("   ", "3000", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
        }
    }

    @Test
    void loginWithUnreachableHostReturnsErrorNotException() {
        // An unreachable host should produce an ERROR result, not a thrown exception.
        try (var h = open()) {
            LoginResult r = h.handler().login("http://192.0.2.1", "9999", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status,
                    "Connection failure to unreachable host must map to ERROR, got: " + r.message);
        }
    }
}
