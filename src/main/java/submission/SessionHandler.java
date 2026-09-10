package submission;


import gui.environment.Environment;
import gui.environment.Universe;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import static submission.LoginResult.*;


public class SessionHandler {
    public final Preferences preferences;

    private Instant startTime = Instant.MIN;

    private AFCTClient client = null;
    private String token = null;

    private String email = null;

    public boolean loggedIn = false;

    // Submit windows
    private ArrayList<SubmitWindow> submitWindows;

    // The login dialog, created the first time it is shown. Globals constructs a
    // SessionHandler in its class initializer, so building the dialog here would
    // mean any code path that touches Globals creates Swing UI — which is also a
    // HeadlessException on a machine with no display (CI).
    private LoginWindow loginWindow;

    // Preferences
    /** The last successfully used server base URL; the login window prefills from it. */
    public static final String PREF_SERVER = "server";
    public static final String PREF_EMAIL = "email";
    // "Stay signed in on this computer" for token mode. The token is stored as-is:
    // encrypting it with key material derived from public values (the way the saved
    // password is) would be obfuscation, not protection. The real safeguard is that
    // this is opt-in and off by default, because Preferences are per OS user and a
    // lab machine with a shared login is one node for every student who sits down.
    public static final String PREF_STAY_SIGNED_IN = "stay_signed_in";
    public static final String PREF_SIGNIN_TOKEN = "signin_token";

    // Deliberately empty before first use: prefilling a development address
    // taught people to trust whatever was in the box.
    public static final String defaultServer = "";

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.submitWindows = new ArrayList<>();

        // TLS trust is handled per connection in AFCTClient (trust-on-first-use with
        // pinning). Nothing here may ever install a JVM-wide trust-all context.
    }

    /** The login dialog, built on first use (always on the EDT; see the field note). */
    private LoginWindow loginWindow() {
        if (loginWindow == null) {
            loginWindow = new LoginWindow(this);
        }
        return loginWindow;
    }

    public SubmitWindow createNewSubmitWindow(Environment environment) {
        SubmitWindow submitWindow = new SubmitWindow(environment, this);
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
            // A stored "stay signed in" token signs in silently, with no window at all;
            // otherwise the login window opens, prefilled with the last server and email.
            if (!trySavedTokenSignIn()) {
                showLoginWindowBlocking(frame);
            }
        }

        if (this.client == null || !this.client.isAuthenticated()) {
            return null;
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
                // Remember the last server and email that actually worked; the
                // login window prefills from them. Never the password: "stay
                // signed in" keeps the bearer token instead.
                preferences.put(PREF_SERVER, address.baseUrl());
                preferences.put(PREF_EMAIL, userEmail);
                return getSuccessResult();
            } else {
                // Login failed
                this.loggedIn = false;
                this.client = null;
                return getFailureResult();
            }
        } catch (SSLHandshakeException ex) {
            AFCTClient failed = this.client;
            this.loggedIn = false;
            this.client = null;
            return certificateOrError(failed, address, ex);
        } catch (IOException ex) {
            this.loggedIn = false;
            this.client = null;
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
            ApiModels.User user = candidate.loginWithToken(tokenValue);
            if (user != null) {
                this.client = candidate;
                this.loggedIn = true;
                this.email = user.email();
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
            ApiModels.User user = candidate.exchangeCode(code, flow.verifier(), flow.redirectUri());

            this.client = candidate;
            this.loggedIn = true;
            this.email = user != null ? user.email() : null;
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

    /** The last server that signed in successfully, in any mode; "" before the first. */
    public String getSavedServer() {
        return preferences.get(PREF_SERVER, defaultServer);
    }

    /** The last email that signed in successfully with a password; "" before the first. */
    public String getSavedEmail() {
        return preferences.get(PREF_EMAIL, "");
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
            Runnable showLogin = () -> loginWindow().displayLoginWindow(frame);
            SwingUtilities.invokeLater(showLogin);
        }
    }

    // ============================================================
    // Credentials / Remember Me
    // ============================================================

    private void showLoginWindowBlocking(JFrame frame) {
        // No display means no way to ask; behave exactly like a cancelled login.
        // This is also what lets requireAuthenticated run under headless tests.
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        Runnable showLogin = () -> loginWindow().displayLoginWindow(frame);
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

}
