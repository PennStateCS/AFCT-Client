package submission;


import gui.environment.Environment;
import gui.environment.Universe;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.Base64;
import java.util.prefs.Preferences;

import static submission.LoginResult.*;


public class SessionHandler {
    public final Preferences preferences;
    private int expireAfterDays = 7;

    private Instant startTime = Instant.MIN;

    private AFCTClient client = null;
    private String token = null;

    private String email = null;

    public boolean loggedIn = false;

    // Submit windows
    private ArrayList<SubmitWindow> submitWindows;

    // Login GUI elements
    private final LoginWindow loginWindow;

    // Preferences
    public static final String PREF_HAS_USED_SAVED_CREDS = "has_used_saved_creds";
    /** Legacy locale-formatted expiry key; retained only for migration. */
    public static final String PREF_SAVED_CREDS_EXPIRE_AFTER = "saved_creds_expire_after";
    public static final String PREF_SAVED_CREDS_EXPIRE_AT_MS = "saved_creds_expire_at_ms";
    /** The last successfully used server base URL; the login window prefills from it. */
    public static final String PREF_SERVER = "server";
    public static final String PREF_EMAIL = "email";
    /** Legacy plaintext password key; retained only for migration. */
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_PASSWORD_ENCRYPTED = "password_encrypted";
    public static final String PREF_PASSWORD_SALT = "password_salt";
    public static final String PREF_REMEMBER_ME = "remember_me";
    // "Stay signed in on this computer" for token mode. The token is stored as-is:
    // encrypting it with key material derived from public values (the way the saved
    // password is) would be obfuscation, not protection. The real safeguard is that
    // this is opt-in and off by default, because Preferences are per OS user and a
    // lab machine with a shared login is one node for every student who sits down.
    public static final String PREF_STAY_SIGNED_IN = "stay_signed_in";
    public static final String PREF_SIGNIN_TOKEN = "signin_token";
    public static final String PREF_HOMEWORK = "homework";
    public static final String PREF_PROBLEM = "problem";

    // Default values. The server default is deliberately empty: prefilling a
    // development address taught people to trust whatever was in the box.
    public static final String defaultServer = "";
    public static final String defaultEmail = "student@example.com";
    public static final String defaultPassword = "";
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_IV_BYTES = 12;
    private static final int PASSWORD_PBKDF2_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final String PASSWORD_ENC_VERSION = "v2";
    private static final String LEGACY_PASSWORD_ENC_VERSION = "v1";

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.submitWindows = new ArrayList<>();

        // Login GUI elements
        this.loginWindow = new LoginWindow(this);

        // TLS trust is handled per connection in AFCTClient (trust-on-first-use with
        // pinning). Nothing here may ever install a JVM-wide trust-all context.
    }

    public SubmitWindow createNewSubmitWindow(Environment environment) {
        SubmitWindow submitWindow = new SubmitWindow(environment);
        submitWindows.add(submitWindow);
        return submitWindow;
    }

    public void displayLoginThenSubmission(SubmitWindow submitWindowToShow, Environment environment) {
        AFCTClient authenticated = requireAuthenticated(Universe.frameForEnvironment(environment));
        if (authenticated != null && authenticated.isAuthenticated()) {
            submitWindowToShow.displaySubmitWindow();
        }
    }

    public void updateStartTime() {
        startTime = Instant.now();
    }

    public void clearStartTime() {
        startTime = Instant.MIN;
    }

    /**
     * Returns the authenticated client, triggering a login window if not authenticated.
     * Returns null if the user cancelled login.
     * The bearer token has a sliding 30-day expiry and every call renews it, so there
     * is no idle-timeout check here — if the server ever returns 401, log in again.
     */
    public AFCTClient requireAuthenticated(JFrame frame) {
        boolean needToReAuth = this.client == null || !this.client.isAuthenticated();

        if (needToReAuth) {
            // A stored "stay signed in" token signs in silently, with no window at all.
            // Remember Me (password mode) only pre-fills the form; the user must log in.
            if (!trySavedTokenSignIn()) {
                showLoginWindowBlocking(frame, shouldAutoLogin());
            }
        }

        if (this.client == null || !this.client.isAuthenticated()) {
            return null;
        }
        return this.client;
    }


    private boolean shouldAutoLogin() {
        if (!hasRememberMe()) {
            return false;
        }

        long expiresAtMs = getSavedCredentialsExpiryMillis();
        if (expiresAtMs <= 0 || Instant.now().toEpochMilli() >= expiresAtMs) {
            return false;
        }
        return true;
    }

    /**
     * Returns the stored user email address, or null if none saved.
     */
    public String getUserEmail() {
        return email;
    }

    // ============================================================
    // Login / Logout
    // ============================================================

    public LoginResult login(String serverAddress, String userEmail, String userPassword) {
        userEmail = userEmail.trim();

        ServerAddress address;
        try {
            address = ServerAddress.parse(serverAddress);
        } catch (IllegalArgumentException ex) {
            return getErrorResult(ex.getMessage());
        }
        LoginResult cleartextRefusal = refuseCleartext(address);
        if (cleartextRefusal != null) return cleartextRefusal;

        try {
            client = new AFCTClient(address.baseUrl());
            token = client.login(userEmail, userPassword);
            if (token != null && !token.isBlank()) {
                // Login succeeded
                this.loggedIn = true;
                this.email = userEmail;
                // Remember the last server that actually worked, whatever sign-in
                // mode was used; the login window prefills from it.
                preferences.put(PREF_SERVER, address.baseUrl());

                // Set creds to expire after 7 days
                long expiresAtMs = Instant.now().plus(Duration.ofDays(expireAfterDays)).toEpochMilli();
                preferences.putLong(PREF_SAVED_CREDS_EXPIRE_AT_MS, expiresAtMs);
                preferences.remove(PREF_SAVED_CREDS_EXPIRE_AFTER);
                preferences.put(PREF_HAS_USED_SAVED_CREDS, "yes");
                return getSuccessResult();
            } else {
                // Login failed
                this.loggedIn = false;
                this.client = null;
                preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
                return getFailureResult();
            }
        } catch (SSLHandshakeException ex) {
            AFCTClient failed = this.client;
            this.loggedIn = false;
            this.client = null;
            preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
            return certificateOrError(failed, address, ex);
        } catch (IOException ex) {
            this.loggedIn = false;
            this.client = null;
            preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
            return getErrorResult(ex.getMessage());
        }
    }

    /**
     * A bearer token over cleartext has no protection at all, so plain http is
     * refused everywhere except a server on this machine (a dev stack).
     */
    private static LoginResult refuseCleartext(ServerAddress address) {
        if ("http".equals(address.scheme()) && !address.isLoopback()) {
            return getErrorResult("This address is not secure. Use https, or http only for a server running on this computer.");
        }
        return null;
    }

    /**
     * Turns a failed TLS handshake into the specific certificate result when the
     * pinning trust manager refused it, or a plain error otherwise.
     */
    private static LoginResult certificateOrError(AFCTClient failedClient, ServerAddress address, SSLHandshakeException ex) {
        PinningTrustManager trust = failedClient != null ? failedClient.trustFailure() : null;
        if (trust != null) {
            LoginResult.LoginStatus status = trust.failure() == PinningTrustManager.Failure.CHANGED
                    ? LoginResult.LoginStatus.CERT_CHANGED
                    : LoginResult.LoginStatus.UNTRUSTED_CERT;
            return LoginResult.certificateResult(status, address.baseUrl(), trust.rejectedChain());
        }
        return getErrorResult(ex.getMessage());
    }

    public void pinServer(String origin, java.security.cert.X509Certificate leaf) {
        CertificatePins.defaultStore().pin(origin, CertificatePins.fingerprintOf(leaf));
    }

    public void forgetServer(String origin) {
        CertificatePins.defaultStore().forget(origin);
    }

    /**
     * Signs in with a token created on the web account page instead of an email and
     * password. This is the only path for a student whose AFCT session lives inside
     * an LMS iframe, so it is a first-class mode, not a fallback. Deliberately does
     * not touch the Remember Me machinery: no saved password, no expiry window.
     */
    public LoginResult loginWithToken(String serverAddress, String tokenText) {
        String tokenValue = tokenText == null ? "" : tokenText.trim();

        ServerAddress address;
        try {
            address = ServerAddress.parse(serverAddress);
        } catch (IllegalArgumentException ex) {
            return getErrorResult(ex.getMessage());
        }
        LoginResult cleartextRefusal = refuseCleartext(address);
        if (cleartextRefusal != null) return cleartextRefusal;
        if (tokenValue.isBlank()) {
            return getErrorResult("Sign-in token is required.");
        }

        AFCTClient candidate = null;
        try {
            candidate = new AFCTClient(address.baseUrl());
            Map<String, Object> user = candidate.loginWithToken(tokenValue);
            if (user != null) {
                this.client = candidate;
                this.loggedIn = true;
                Object userEmail = user.get("email");
                this.email = userEmail != null ? String.valueOf(userEmail) : null;
                preferences.put(PREF_SERVER, address.baseUrl());
                return getSuccessResult();
            }
            this.loggedIn = false;
            this.client = null;
            return new LoginResult(LoginResult.LoginStatus.FAILURE,
                    "That token was not accepted. It may have expired or been revoked. "
                            + "Create a new one from your AFCT account page.");
        } catch (SSLHandshakeException ex) {
            this.loggedIn = false;
            this.client = null;
            return certificateOrError(candidate, address, ex);
        } catch (IOException ex) {
            this.loggedIn = false;
            this.client = null;
            return getErrorResult(ex.getMessage());
        }
    }

    /**
     * Signs in with the stored "stay signed in" token without showing a window.
     * Returns true on success. A token the server rejects is cleared, so the next
     * login window does not retry a dead token; a network failure keeps it, since
     * the token itself may be fine.
     */
    private boolean trySavedTokenSignIn() {
        if (!hasSavedSignInToken()) {
            return false;
        }
        LoginResult result = loginWithToken(getSavedServer(),
                preferences.get(PREF_SIGNIN_TOKEN, ""));
        if (result.status == LoginResult.LoginStatus.SUCCESS) {
            return true;
        }
        if (result.status == LoginResult.LoginStatus.FAILURE) {
            // Drop the dead token but keep the stay-signed-in flag, so the login
            // window opens on the token form for a student who chose that mode.
            preferences.remove(PREF_SIGNIN_TOKEN);
        }
        return false;
    }

    // The in-flight browser sign-in, so the window's Cancel can reach it.
    private volatile BrowserSignIn activeBrowserSignIn = null;

    /**
     * Browser sign-in (RFC 8252): a loopback listener, the system browser at the
     * server's consent page, then a code-for-token exchange. Blocking for up to
     * {@link BrowserSignIn#APPROVAL_TIMEOUT}; call from a background thread.
     * `onAuthorizeUrl` receives the consent URL before the browser is asked to
     * open, and always: Desktop.browse fails silently on some Linux desktops and
     * under WSL, so the UI shows the URL with a copy control as a matter of
     * course, not only on error.
     */
    public LoginResult loginWithBrowser(String serverAddress, java.util.function.Consumer<String> onAuthorizeUrl) {
        ServerAddress address;
        try {
            address = ServerAddress.parse(serverAddress);
        } catch (IllegalArgumentException ex) {
            return getErrorResult(ex.getMessage());
        }
        LoginResult cleartextRefusal = refuseCleartext(address);
        if (cleartextRefusal != null) return cleartextRefusal;

        AFCTClient candidate = null;
        try (BrowserSignIn flow = new BrowserSignIn()) {
            this.activeBrowserSignIn = flow;
            String url = flow.authorizeUrl(address.baseUrl(), AFCTClient.defaultDeviceName());
            onAuthorizeUrl.accept(url);
            try {
                Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception ignored) {
                // The URL is already on screen; opening it by hand is the fallback.
            }

            String code = flow.awaitCode(BrowserSignIn.APPROVAL_TIMEOUT);

            candidate = new AFCTClient(address.baseUrl());
            Map<String, Object> user = candidate.exchangeCode(code, flow.verifier(), flow.redirectUri());

            this.client = candidate;
            this.loggedIn = true;
            Object userEmail = user != null ? user.get("email") : null;
            this.email = userEmail != null ? String.valueOf(userEmail) : null;
            preferences.put(PREF_SERVER, address.baseUrl());
            return getSuccessResult();
        } catch (SSLHandshakeException ex) {
            this.loggedIn = false;
            this.client = null;
            return certificateOrError(candidate, address, ex);
        } catch (IOException ex) {
            this.loggedIn = false;
            this.client = null;
            return getErrorResult(ex.getMessage());
        } finally {
            this.activeBrowserSignIn = null;
        }
    }

    /** Aborts an in-flight browser sign-in, if any; the blocked call returns an error result. */
    public void cancelBrowserSignIn() {
        BrowserSignIn flow = this.activeBrowserSignIn;
        if (flow != null) flow.cancel();
    }

    /**
     * Stores the signed-in client's current bearer token for silent sign-in, for
     * modes (browser sign-in) where the token never passed through a text field.
     */
    public void persistCurrentTokenForStaySignedIn() {
        if (client != null && client.currentToken() != null && !client.currentToken().isBlank()) {
            saveSignInToken(client.currentToken());
        }
    }

    /** Called after a successful token sign-in; the server was already remembered there. */
    public void saveSignInToken(String tokenValue) {
        preferences.put(PREF_SIGNIN_TOKEN, tokenValue);
        preferences.putBoolean(PREF_STAY_SIGNED_IN, true);
    }

    public void clearSavedSignInToken() {
        preferences.remove(PREF_SIGNIN_TOKEN);
        preferences.putBoolean(PREF_STAY_SIGNED_IN, false);
    }

    public boolean hasSavedSignInToken() {
        return preferences.getBoolean(PREF_STAY_SIGNED_IN, false)
                && !preferences.get(PREF_SIGNIN_TOKEN, "").isBlank();
    }

    /** The user's last "stay signed in" choice; survives a dead token being cleared. */
    public boolean staySignedInPreferred() {
        return preferences.getBoolean(PREF_STAY_SIGNED_IN, false);
    }

    public void logout() {
        logout(false, null);
    }

    public void logout(boolean forceManualReLogin, JFrame frame) {
        preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
        // Clear the stored token before the best-effort revoke thread starts: if the
        // revoke fails, a working token must not be left behind on this machine.
        clearSavedSignInToken();
        this.loggedIn = false;

        // Revoke the bearer token server-side (best-effort, non-blocking)
        final AFCTClient oldClient = this.client;
        this.client = null;
        if (oldClient != null) {
            new Thread(oldClient::logout, "afct-logout").start();
        }

        // Hide all submit windows
        for (SubmitWindow submitWindow : submitWindows) {
            submitWindow.setVisible(false);
        }

        // Refresh all submit windows so they reset to logged-out state
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                for (SubmitWindow submitWindow : submitWindows) {
                    submitWindow.refreshDialog();
                }
                return null;
            }
        }.execute();

        if (forceManualReLogin) {
            Runnable showLogin = () -> loginWindow.displayLoginWindow(frame);
            SwingUtilities.invokeLater(showLogin);
        }
    }

    // ============================================================
    // Credentials / Remember Me
    // ============================================================

    /** Called after a successful password sign-in; the server was already remembered there. */
    public void saveCredentials(String userEmail, String userPassword) {
        preferences.put(PREF_EMAIL, userEmail.trim());
        boolean passwordSaved = storeRememberedPassword(userPassword);
        preferences.putBoolean(PREF_REMEMBER_ME, passwordSaved);
        this.email = userEmail.trim();
    }

    public void clearSavedCredentials() {
        preferences.remove(PREF_SAVED_CREDS_EXPIRE_AT_MS);
        preferences.remove(PREF_SAVED_CREDS_EXPIRE_AFTER);
        preferences.remove(PREF_PASSWORD_ENCRYPTED);
        preferences.remove(PREF_PASSWORD_SALT);
        preferences.remove(PREF_PASSWORD);
        preferences.putBoolean(PREF_REMEMBER_ME, false);
    }

    public boolean hasRememberMe() {
        return preferences.getBoolean(PREF_REMEMBER_ME, false);
    }

    public String getSavedServer() {
        return preferences.get(PREF_SERVER, defaultServer);
    }

    public String getSavedEmail() {
        return preferences.get(PREF_EMAIL, defaultEmail);
    }

    public String getSavedPassword() {
        String encrypted = preferences.get(PREF_PASSWORD_ENCRYPTED, null);
        if (encrypted != null && !encrypted.isBlank()) {
            try {
                String decrypted = decryptPassword(encrypted);
                if (encrypted.startsWith(LEGACY_PASSWORD_ENC_VERSION + ":")) {
                    storeRememberedPassword(decrypted);
                }
                return decrypted;
            } catch (GeneralSecurityException | IllegalArgumentException ex) {
                System.err.println("[SessionHandler] Failed to decrypt saved password: " + ex.getMessage());
                return defaultPassword;
            }
        }

        // Migrate any previously stored plaintext password into encrypted storage.
        String legacyPlaintext = preferences.get(PREF_PASSWORD, defaultPassword);
        if (!legacyPlaintext.isBlank()) {
            storeRememberedPassword(legacyPlaintext);
            preferences.remove(PREF_PASSWORD);
        }
        return legacyPlaintext;
    }

    // ============================================================
    // Auto re-authenticate
    // ============================================================

    /**
     * Attempts to re-authenticate using saved credentials.
     * Returns true on success, false if creds are missing, expired, or login fails.
     */
    private boolean autoReAuthenticate() {
        if (!hasRememberMe()) {
            return false;
        }

        long expiresAtMs = getSavedCredentialsExpiryMillis();
        if (expiresAtMs <= 0 || Instant.now().toEpochMilli() >= expiresAtMs) {
            return false;
        }

        String serverUrl = getSavedServer();
        String userEmail = getSavedEmail();
        String userPassword = getSavedPassword();
        if (userPassword.isBlank()) {
            return false;
        }
        LoginResult loginResult = login(serverUrl, userEmail, userPassword);
        return loginResult.status == LoginResult.LoginStatus.SUCCESS;
    }

    private boolean storeRememberedPassword(String password) {
        try {
            if (password == null || password.isBlank()) {
                preferences.remove(PREF_PASSWORD_ENCRYPTED);
                preferences.remove(PREF_PASSWORD);
                return false;
            }
            preferences.put(PREF_PASSWORD_ENCRYPTED, encryptPassword(password));
            preferences.remove(PREF_PASSWORD);
            return true;
        } catch (GeneralSecurityException ex) {
            preferences.remove(PREF_PASSWORD_ENCRYPTED);
            preferences.remove(PREF_PASSWORD);
            System.err.println("[SessionHandler] Failed to encrypt saved password: " + ex.getMessage());
            return false;
        }
    }

    private String encryptPassword(String plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[PASSWORD_IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getPasswordSecretKey(getPasswordKeyMaterial()), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return PASSWORD_ENC_VERSION + ":" +
                Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(ciphertext);
    }

    private String decryptPassword(String encoded) throws GeneralSecurityException {
        String[] parts = encoded.split(":", 3);
        if (parts.length != 3) {
            throw new GeneralSecurityException("Unsupported password encryption format.");
        }
        String version = parts[0];

        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
        if (iv.length != PASSWORD_IV_BYTES) {
            throw new GeneralSecurityException("Invalid password IV.");
        }

        String keyMaterial;
        if (PASSWORD_ENC_VERSION.equals(version)) {
            keyMaterial = getPasswordKeyMaterial();
        } else if (LEGACY_PASSWORD_ENC_VERSION.equals(version)) {
            keyMaterial = getLegacyPasswordKeyMaterial();
        } else {
            throw new GeneralSecurityException("Unsupported password encryption format version.");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getPasswordSecretKey(keyMaterial), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private SecretKey getPasswordSecretKey(String keyMaterial) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(keyMaterial.toCharArray(), getOrCreatePasswordSalt(),
                PASSWORD_PBKDF2_ITERATIONS, PASSWORD_KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] getOrCreatePasswordSalt() {
        String encodedSalt = preferences.get(PREF_PASSWORD_SALT, null);
        if (encodedSalt != null && !encodedSalt.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encodedSalt);
                if (decoded.length == PASSWORD_SALT_BYTES) {
                    return decoded;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid salt persisted previously; generate a new one below.
            }
        }

        byte[] salt = new byte[PASSWORD_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        preferences.put(PREF_PASSWORD_SALT, Base64.getEncoder().encodeToString(salt));
        return salt;
    }

    private String getPasswordKeyMaterial() {
        return "afct-remember-me|" +
                SessionHandler.class.getName() + "|" +
                preferences.absolutePath() + "|" +
                System.getProperty("user.name", "");
    }

    private String getLegacyPasswordKeyMaterial() {
        return "afct-remember-me|" +
                preferences.absolutePath() + "|" +
                System.getProperty("user.name", "") + "|" +
                System.getProperty("os.name", "") + "|" +
                System.getProperty("os.arch", "");
    }

    private void showLoginWindowBlocking(JFrame frame, boolean shouldAutoLogin) {
        Runnable showLogin = () -> loginWindow.displayLoginWindow(frame, shouldAutoLogin);
        if (SwingUtilities.isEventDispatchThread()) {
            showLogin.run();
            return;
        }

        try {
            SwingUtilities.invokeAndWait(showLogin);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException("Unable to display login dialog.", ex.getCause());
        }
    }

    private long getSavedCredentialsExpiryMillis() {
        long expiresAt = preferences.getLong(PREF_SAVED_CREDS_EXPIRE_AT_MS, -1L);
        if (expiresAt > 0) {
            return expiresAt;
        }

        String legacyExpiry = preferences.get(PREF_SAVED_CREDS_EXPIRE_AFTER, null);
        if (legacyExpiry == null || legacyExpiry.isBlank()) {
            return -1L;
        }

        try {
            Date parsed = DateFormat.getDateInstance(DateFormat.SHORT).parse(legacyExpiry);
            if (parsed != null) {
                long migrated = parsed.getTime();
                preferences.putLong(PREF_SAVED_CREDS_EXPIRE_AT_MS, migrated);
                preferences.remove(PREF_SAVED_CREDS_EXPIRE_AFTER);
                return migrated;
            }
        } catch (ParseException ignored) {
        }

        return -1L;
    }
}
