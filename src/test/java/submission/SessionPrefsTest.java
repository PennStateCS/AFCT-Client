package submission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/** What the client remembers between runs, and the three distinct forget paths. */
class SessionPrefsTest {

    private Preferences node;
    private SessionPrefs prefs;

    @BeforeEach
    void setUp() throws Exception {
        node = Preferences.userRoot().node("afct-client-test-session-prefs");
        node.clear();
        prefs = new SessionPrefs(node);
    }

    @AfterEach
    void tearDown() throws Exception {
        node.clear();
    }

    @Test
    void startsEmptyWithNoDevPrefill() {
        assertEquals("", prefs.savedServer());
        assertEquals("", prefs.savedEmail());
        assertFalse(prefs.staySignedInPreferred());
        assertFalse(prefs.hasStoredSignInToken());
    }

    @Test
    void remembersTheLastWorkingServerAndEmail() {
        prefs.rememberServer("https://afct.example.edu");
        prefs.rememberEmail("a@b.c");
        assertEquals("https://afct.example.edu", prefs.savedServer());
        assertEquals("a@b.c", prefs.savedEmail());
    }

    @Test
    void storingATokenTurnsThePreferenceOn() {
        prefs.storeSignInToken("tok-1");
        assertTrue(prefs.hasStoredSignInToken());
        assertTrue(prefs.staySignedInPreferred());
        assertEquals("tok-1", prefs.storedSignInToken());
    }

    @Test
    void clearForgetsTheTokenAndThePreference() {
        // Opt-out and Logout: a full forget.
        prefs.storeSignInToken("tok-1");
        prefs.clearSignInToken();
        assertFalse(prefs.hasStoredSignInToken());
        assertFalse(prefs.staySignedInPreferred());
    }

    @Test
    void aDeadTokenIsDroppedButTheChoiceSurvives() {
        // A server rejection kills the token, not the student's stay-signed-in
        // choice: the login window should reopen with the box still ticked.
        prefs.storeSignInToken("tok-1");
        prefs.dropDeadToken();
        assertFalse(prefs.hasStoredSignInToken());
        assertTrue(prefs.staySignedInPreferred());
    }
}
