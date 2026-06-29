package submission;


import gui.environment.Environment;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
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
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_INSECURE_TLS = "insecure_tls";
    public static final String PREF_REMEMBER_ME = "remember_me";
    public static final String PREF_HOMEWORK = "homework";
    public static final String PREF_PROBLEM = "problem";

    // Default values
    public static final String defaultServer = "https://10.144.18.20";
    public static final String defaultPort = "3000";
    public static final String defaultEmail = "student@example.com";
    public static final String defaultPassword = "";

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
        this.certificateHandler = new CertificateHandler();
        this.submitWindows = new ArrayList<>();

        // Login GUI elements
        this.loginWindow = new LoginWindow(this);

        // Certificate stuff
        try {
            CertificateHandler.enableCustomCertificateValidation();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
     */
    public AFCTClient requireAuthenticated() {
        // Check if 15 minutes have passed
        Instant currentTime = Instant.now();
        Duration duration = Duration.between(startTime, currentTime);
        long minutesPassed = duration.toMinutes();

        boolean needToReAuth = this.client == null || !this.client.isAuthenticated() || minutesPassed >= 14;

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
        // Preserve the protocol before fixUrl strips it
        boolean isHttps = serverUrl.trim().toLowerCase().startsWith("https://");
        serverUrl = fixUrl(serverUrl);
        portText = portText.trim();
        userEmail = userEmail.trim();

        this.insecureTls = insecureTls;
        preferences.putBoolean(PREF_INSECURE_TLS, insecureTls);

        // Reconstruct the full URL with protocol so AFCTClient uses the right scheme
        String fullUrl = (isHttps ? "https://" : "http://") + serverUrl + ":" + portText;

        try {
            client = new AFCTClient(fullUrl);
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
        this.client = null;

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
        preferences.put(PREF_PASSWORD, userPassword);
        preferences.putBoolean(PREF_REMEMBER_ME, true);
        this.email = userEmail.trim();
    }

    public void clearSavedCredentials() {
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
        return preferences.get(PREF_PASSWORD, defaultPassword);
    }

    public boolean isInsecureTls() {
        return preferences.getBoolean(PREF_INSECURE_TLS, true);
    }

    public void saveLoginInfo(String serverUrl, String portText, String userEmail, String userPassword) {
        preferences.put(PREF_SERVER, fixUrl(serverUrl));
        preferences.put(PREF_PORT, portText.trim());
        preferences.put(PREF_EMAIL, userEmail.trim());
        preferences.put(PREF_PASSWORD, userPassword);
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
                LoginResult loginResult = login(serverUrl, portText, userEmail, userPassword);
                return loginResult.status == LoginResult.LoginStatus.SUCCESS;
            }
        } catch (ParseException ignored) { }

        return false;
    }
}
