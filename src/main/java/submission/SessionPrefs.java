package submission;

import java.util.prefs.Preferences;

/**
 * Everything the client remembers between runs, behind typed accessors. One
 * place decides what is stored and under which key; SessionHandler decides when.
 *
 * The sign-in token is stored as-is: encrypting it with key material derived
 * from public values (the way the old saved password was) would be obfuscation,
 * not protection. The real safeguard is that storing it is opt-in and off by
 * default, because Preferences are per OS user and a lab machine with a shared
 * login is one node for every student who sits down.
 */
class SessionPrefs {

    /** The last successfully used server base URL; the login window prefills from it. */
    static final String PREF_SERVER = "server";
    /** The last email that signed in successfully with a password; prefill only. */
    static final String PREF_EMAIL = "email";
    /** The "stay signed in" choice; survives a dead token being dropped. */
    static final String PREF_STAY_SIGNED_IN = "stay_signed_in";
    static final String PREF_SIGNIN_TOKEN = "signin_token";

    private final Preferences preferences;

    SessionPrefs(Preferences preferences) {
        this.preferences = preferences;
    }

    /** Called only after a successful sign-in, so the prefill is a server that works. */
    void rememberServer(String baseUrl) {
        preferences.put(PREF_SERVER, baseUrl);
    }

    String savedServer() {
        return preferences.get(PREF_SERVER, "");
    }

    void rememberEmail(String email) {
        preferences.put(PREF_EMAIL, email);
    }

    String savedEmail() {
        return preferences.get(PREF_EMAIL, "");
    }

    /** Stores the bearer token for silent sign-in and turns the preference on. */
    void storeSignInToken(String token) {
        preferences.put(PREF_SIGNIN_TOKEN, token);
        preferences.putBoolean(PREF_STAY_SIGNED_IN, true);
    }

    /** Opt-out and Logout: forget the token AND the preference. */
    void clearSignInToken() {
        preferences.remove(PREF_SIGNIN_TOKEN);
        preferences.putBoolean(PREF_STAY_SIGNED_IN, false);
    }

    /**
     * Drops a token the server rejected while KEEPING the stay-signed-in flag, so
     * the login window still opens with the student's choice ticked.
     */
    void dropDeadToken() {
        preferences.remove(PREF_SIGNIN_TOKEN);
    }

    String storedSignInToken() {
        return preferences.get(PREF_SIGNIN_TOKEN, "");
    }

    boolean hasStoredSignInToken() {
        return staySignedInPreferred() && !storedSignInToken().isBlank();
    }

    boolean staySignedInPreferred() {
        return preferences.getBoolean(PREF_STAY_SIGNED_IN, false);
    }
}
