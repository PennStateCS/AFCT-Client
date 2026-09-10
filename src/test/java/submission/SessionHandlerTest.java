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
        prefs.remove(SessionHandler.PREF_EMAIL);
        prefs.remove(SessionHandler.PREF_STAY_SIGNED_IN);
        prefs.remove(SessionHandler.PREF_SIGNIN_TOKEN);
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
            assertNull(h.handler().requireAuthenticated(null));
        }
    }

    // ── Default preferences ──────────────────────────────────────────────────

    @Test
    void savedServerDefaultsToEmpty() {
        // No dev-address prefill: an empty box is the correct starting state.
        try (var h = open()) {
            assertEquals("", h.handler().getSavedServer());
        }
    }

    @Test
    void savedEmailDefaultsToEmpty() {
        try (var h = open()) {
            assertEquals("", h.handler().getSavedEmail());
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
            LoginResult r = h.handler().login("", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
            assertNotNull(r.message);
        }
    }

    @Test
    void loginWithBlankServerReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().login("   ", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
        }
    }

    @Test
    void loginWithUnparseableServerReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().login("https://", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
        }
    }

    @Test
    void plainHttpOffLoopbackIsRefusedWithoutTouchingTheNetwork() {
        // A bearer token over cleartext has no protection; refuse before connecting.
        try (var h = open()) {
            long start = System.nanoTime();
            LoginResult login = h.handler().login("http://192.0.2.1:9999", "a@b.com", "pw");
            LoginResult token = h.handler().loginWithToken("http://192.0.2.1:9999", "some-token");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertEquals(LoginResult.LoginStatus.ERROR, login.status);
            assertEquals(LoginResult.LoginStatus.ERROR, token.status);
            assertTrue(login.message.contains("not secure"), login.message);
            assertTrue(token.message.contains("not secure"), token.message);
            // No connection attempt: a network timeout would take seconds.
            assertTrue(elapsedMs < 2000, "Refusal took " + elapsedMs + "ms; did it touch the network?");
        }
    }

    @Test
    void plainHttpToLoopbackIsAllowedThroughToTheNetwork() {
        // Dev stacks run on http://localhost:3000; the cleartext refusal must not
        // catch them. Port 1 is never listening, so the failure is a connection
        // error, proving the request got past the refusal.
        try (var h = open()) {
            LoginResult r = h.handler().login("http://127.0.0.1:1", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
            assertFalse(r.message.contains("not secure"),
                    "Loopback http must not be refused as cleartext, got: " + r.message);
        }
    }

    @Test
    void loginWithUnreachableHostReturnsErrorNotException() {
        // An unreachable host should produce an ERROR result, not a thrown exception.
        try (var h = open()) {
            LoginResult r = h.handler().login("https://192.0.2.1:9999", "a@b.com", "pw");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status,
                    "Connection failure to unreachable host must map to ERROR, got: " + r.message);
        }
    }

    // ── loginWithToken() – offline error paths ───────────────────────────────

    @Test
    void tokenLoginWithEmptyServerReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().loginWithToken("", "some-token");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
            assertNotNull(r.message);
        }
    }

    @Test
    void tokenLoginWithBlankTokenReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().loginWithToken("https://10.0.0.1", "   ");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
        }
    }

    @Test
    void tokenLoginWithNullTokenReturnsError() {
        try (var h = open()) {
            LoginResult r = h.handler().loginWithToken("https://10.0.0.1", null);
            assertEquals(LoginResult.LoginStatus.ERROR, r.status);
        }
    }

    @Test
    void tokenLoginWithUnreachableHostReturnsErrorNotException() {
        try (var h = open()) {
            LoginResult r = h.handler().loginWithToken("https://192.0.2.1:9999", "some-token");
            assertEquals(LoginResult.LoginStatus.ERROR, r.status,
                    "Connection failure to unreachable host must map to ERROR, got: " + r.message);
        }
    }

    // ── Stay signed in (stored token) ────────────────────────────────────────

    @Test
    void saveSignInTokenRoundTrip() {
        try (var h = open()) {
            h.handler().saveSignInToken("tok-123");
            assertTrue(h.handler().hasSavedSignInToken());
            assertTrue(h.handler().staySignedInPreferred());
        }
    }

    @Test
    void clearSavedSignInTokenClearsTokenAndPreference() {
        try (var h = open()) {
            h.handler().saveSignInToken("tok-123");
            h.handler().clearSavedSignInToken();
            assertFalse(h.handler().hasSavedSignInToken());
            assertFalse(h.handler().staySignedInPreferred());
        }
    }

    @Test
    void logoutClearsSavedSignInToken() {
        try (var h = open()) {
            h.handler().saveSignInToken("tok-123");
            h.handler().logout();
            assertFalse(h.handler().hasSavedSignInToken());
        }
    }

    @Test
    void networkFailureKeepsSavedSignInToken() {
        // A silent sign-in that fails because the server is unreachable must keep the
        // stored token: it may be fine, and only a server rejection means it is dead.
        try (var h = open()) {
            h.handler().preferences.put(SessionHandler.PREF_SERVER, "https://192.0.2.1:9999");
            h.handler().saveSignInToken("tok-123");
            assertNull(h.handler().requireAuthenticated(null));
            assertTrue(h.handler().hasSavedSignInToken(),
                    "Unreachable server must not clear the stored token");
        }
    }

    @Test
    void tokenLoginFailureLeavesTheStoredTokenAlone() {
        // A failed manual token sign-in (bad server here) must not disturb a
        // stored stay-signed-in token from an earlier session.
        try (var h = open()) {
            h.handler().saveSignInToken("tok-stored");
            h.handler().loginWithToken("", "tok-typed");
            assertTrue(h.handler().hasSavedSignInToken());
        }
    }
}
