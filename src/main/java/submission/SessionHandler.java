package submission;


import gui.environment.Environment;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
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

import static submission.AFCTClient.fixUrl;
import static submission.LoginResult.*;


public class SessionHandler {
    public final Preferences preferences;
    private final DateFormat dateFormat;
    public final CertificateHandler certificateHandler;
    private int expireAfterDays = 7;

    private Instant startTime = Instant.MIN;

    private AFCTClient client = null;
    private String token = null;

    private String email = null;
    private boolean insecureTls = true; // default: skip SSL validation for self-signed certs

    public boolean loggedIn = false;

    // Submit windows
    private ArrayList<SubmitWindow> submitWindows;

    // Login GUI elements
    private final LoginWindow loginWindow;

    // Preferences
    public static final String PREF_HAS_USED_SAVED_CREDS = "has_used_saved_creds";
    public static final String PREF_SAVED_CREDS_EXPIRE_AFTER = "saved_creds_expire_after";
    public static final String PREF_SERVER = "server";
    public static final String PREF_PORT = "port";
    public static final String PREF_EMAIL = "email";
    /** Legacy plaintext password key; retained only for migration. */
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_PASSWORD_ENCRYPTED = "password_encrypted";
    public static final String PREF_PASSWORD_SALT = "password_salt";
    public static final String PREF_INSECURE_TLS = "insecure_tls";
    public static final String PREF_REMEMBER_ME = "remember_me";
    public static final String PREF_HOMEWORK = "homework";
    public static final String PREF_PROBLEM = "problem";

    // Default values
    public static final String defaultServer = "https://10.144.18.20";
    public static final String defaultPort = "3000";
    public static final String defaultEmail = "student@example.com";
    public static final String defaultPassword = "";
    private static final int PASSWORD_SALT_BYTES = 16;
    private static final int PASSWORD_IV_BYTES = 12;
    private static final int PASSWORD_PBKDF2_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final String PASSWORD_ENC_VERSION = "v1";

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
        this.certificateHandler = new CertificateHandler();
        this.submitWindows = new ArrayList<>();

        // Load the user's last saved SSL-validation choice so autoReAuthenticate() (which
        // runs before the login window is ever shown) honors it instead of silently
        // falling back to the in-memory default.
        this.insecureTls = this.preferences.getBoolean(PREF_INSECURE_TLS, true);

        // Login GUI elements
        this.loginWindow = new LoginWindow(this);

        // NOTE: we deliberately do NOT call CertificateHandler.enableCustomCertificateValidation()
        // here. That installs a trust-all SSLContext as the JVM-wide default, which would silently
        // disable certificate validation for every HTTPS connection regardless of the user's
        // "Validate SSL Certificate" choice in the login window. Certificate bypass is instead
        // applied per-connection in AFCTClient, gated on the insecureTls flag the user actually set.
    }

    public SubmitWindow createNewSubmitWindow(Environment environment) {
        SubmitWindow submitWindow = new SubmitWindow(environment);
        submitWindows.add(submitWindow);
        return submitWindow;
    }

    public void displayLoginThenSubmission(SubmitWindow submitWindowToShow) {
        // Try to auto login asynchronously
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return autoReAuthenticate();
            }

            @Override
            protected void done() {
                try {
                    boolean successful = get();
                    if (!successful) {
                        // Show login window (modal — blocks until user logs in or cancels)
                        loginWindow.displayLoginWindow();
                    }
                    if (loggedIn) {
                        submitWindowToShow.displaySubmitWindow();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
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
    public AFCTClient requireAuthenticated() {
        boolean needToReAuth = this.client == null || !this.client.isAuthenticated();

        if (needToReAuth) {
            // Try to re-login automatically
            boolean successful = autoReAuthenticate();

            // If unsuccessful, display login window to user (modal)
            if (!successful) {
                loginWindow.displayLoginWindow();
            }
        }

        return this.client;
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

    public LoginResult login(String serverUrl, String portText, String userEmail, String userPassword) {
        return login(serverUrl, portText, userEmail, userPassword, this.insecureTls);
    }

    public LoginResult login(String serverUrl, String portText, String userEmail, String userPassword, boolean insecureTls) {
        String originalServer = serverUrl == null ? "" : serverUrl.trim();
        boolean hasHttpScheme = originalServer.regionMatches(true, 0, "http://", 0, "http://".length());
        boolean useHttps = !hasHttpScheme; // default to HTTPS when scheme is omitted

        serverUrl = fixUrl(originalServer);
        portText = portText.trim();
        userEmail = userEmail.trim();

        this.insecureTls = insecureTls;
        preferences.putBoolean(PREF_INSECURE_TLS, insecureTls);

        if (serverUrl.isBlank()) {
            return getErrorResult("Server is required.");
        }

        // Reconstruct the full URL with protocol so AFCTClient uses the user-selected
        // scheme, defaulting to HTTPS when no scheme is provided.
        String fullUrl = (useHttps ? "https://" : "http://") + serverUrl + ":" + portText;

        try {
            client = new AFCTClient(fullUrl, insecureTls);
            token = client.login(userEmail, userPassword);
            if (token != null && !token.isBlank()) {
                // Login succeeded
                this.loggedIn = true;
                this.email = userEmail;

                // Set creds to expire after 7 days
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, expireAfterDays);
                preferences.put(PREF_SAVED_CREDS_EXPIRE_AFTER, dateFormat.format(calendar.getTime()));
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
            this.certificateHandler.test();
            return getErrorResult(ex.getMessage());
        } catch (IOException ex) {
            this.loggedIn = false;
            this.client = null;
            preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
            return getErrorResult(ex.getMessage());
        }
    }

    public void logout() {
        logout(false);
    }

    public void logout(boolean forceManualReLogin) {
        preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
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
    }

    // ============================================================
    // Credentials / Remember Me
    // ============================================================

    public void saveCredentials(String serverUrl, String port, String userEmail, String userPassword) {
        // Save with protocol so auto-reauth can reconstruct the correct URL
        String trimmed = serverUrl.trim();
        String withProtocol = (trimmed.startsWith("http://") || trimmed.startsWith("https://"))
                ? trimmed : "https://" + trimmed;
        preferences.put(PREF_SERVER, withProtocol);
        preferences.put(PREF_PORT, port.trim());
        preferences.put(PREF_EMAIL, userEmail.trim());
        boolean passwordSaved = storeRememberedPassword(userPassword);
        preferences.putBoolean(PREF_REMEMBER_ME, passwordSaved);
        this.email = userEmail.trim();
    }

    public void clearSavedCredentials() {
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

    public String getSavedPort() {
        return preferences.get(PREF_PORT, defaultPort);
    }

    public String getSavedEmail() {
        return preferences.get(PREF_EMAIL, defaultEmail);
    }

    public String getSavedPassword() {
        String encrypted = preferences.get(PREF_PASSWORD_ENCRYPTED, null);
        if (encrypted != null && !encrypted.isBlank()) {
            try {
                return decryptPassword(encrypted);
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

    public boolean isInsecureTls() {
        return preferences.getBoolean(PREF_INSECURE_TLS, true);
    }

    public void saveLoginInfo(String serverUrl, String portText, String userEmail, String userPassword) {
        preferences.put(PREF_SERVER, fixUrl(serverUrl));
        preferences.put(PREF_PORT, portText.trim());
        preferences.put(PREF_EMAIL, userEmail.trim());
        storeRememberedPassword(userPassword);
        this.email = userEmail.trim();
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

        String expireAfter = preferences.get(PREF_SAVED_CREDS_EXPIRE_AFTER, null);
        if (expireAfter == null) {
            return false;
        }

        String strCurrent = dateFormat.format(new Date());
        try {
            Date current = dateFormat.parse(strCurrent);
            Date saved = dateFormat.parse(expireAfter);
            if (current.before(saved)) {
                String serverUrl = getSavedServer();
                String portText = getSavedPort();
                String userEmail = getSavedEmail();
                String userPassword = getSavedPassword();
                if (userPassword.isBlank()) {
                    return false;
                }
                LoginResult loginResult = login(serverUrl, portText, userEmail, userPassword);
                return loginResult.status == LoginResult.LoginStatus.SUCCESS;
            }
        } catch (ParseException ignored) { }

        return false;
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
        cipher.init(Cipher.ENCRYPT_MODE, getPasswordSecretKey(), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return PASSWORD_ENC_VERSION + ":" +
                Base64.getEncoder().encodeToString(iv) + ":" +
                Base64.getEncoder().encodeToString(ciphertext);
    }

    private String decryptPassword(String encoded) throws GeneralSecurityException {
        String[] parts = encoded.split(":", 3);
        if (parts.length != 3 || !PASSWORD_ENC_VERSION.equals(parts[0])) {
            throw new GeneralSecurityException("Unsupported password encryption format.");
        }

        byte[] iv = Base64.getDecoder().decode(parts[1]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
        if (iv.length != PASSWORD_IV_BYTES) {
            throw new GeneralSecurityException("Invalid password IV.");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getPasswordSecretKey(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private SecretKey getPasswordSecretKey() throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(getPasswordKeyMaterial().toCharArray(), getOrCreatePasswordSalt(),
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
                preferences.absolutePath() + "|" +
                System.getProperty("user.name", "") + "|" +
                System.getProperty("os.name", "") + "|" +
                System.getProperty("os.arch", "");
    }
}
