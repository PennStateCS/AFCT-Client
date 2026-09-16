package submission;

import gui.Globals;
import gui.environment.Environment;
import gui.environment.Universe;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

import static gui.Globals.*;
import static gui.action.SubmitAction.testAuthThenShowSubmitWindow;

/**
 * The sign-in dialog: one server address, three ways in. Each way is its own
 * panel (Password / Token / Browser) owning its widgets; this window owns the
 * frame, the mode switch, the attempt worker, and the certificate-trust dialogs.
 */
public class LoginWindow extends JDialog {

    private final SessionHandler sessionHandler;

    // One address field; ServerAddress.parse handles scheme and port. Starts empty
    // on purpose: prefilling a development address taught people to trust the box.
    private final JTextField serverTF = new JTextField();

    private final JRadioButton passwordModeRadio = new JRadioButton("Email and password", true);
    private final JRadioButton tokenModeRadio = new JRadioButton("Sign-in token");
    private final JRadioButton browserModeRadio = new JRadioButton("Web browser");

    private final PasswordLoginPanel passwordPanel = new PasswordLoginPanel();
    private final TokenLoginPanel tokenPanel = new TokenLoginPanel();
    private final BrowserLoginPanel browserPanel;
    private final JPanel modeCards = new JPanel(new CardLayout());

    private static final String CARD_PASSWORD = "password";
    private static final String CARD_TOKEN = "token";
    private static final String CARD_BROWSER = "browser";

    private final JButton loginButton = new JButton("Login");
    private final JTextPane resultPane = new JTextPane();
    private final JScrollPane resultScrollPane = new JScrollPane(resultPane);

    public LoginWindow(SessionHandler handler) {
        super((Frame) null, "Login - " + Globals.APP_NAME, true);
        this.sessionHandler = handler;
        this.browserPanel = new BrowserLoginPanel(handler::cancelBrowserSignIn);

        buildUI();
        populateFromSessionState();

        pack();
        setResizable(false);
        setLocationRelativeTo(Globals.getActiveWindow());

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    // ============================================================
    // DISPLAY
    // ============================================================

    public void displayLoginWindow(JFrame frame) {
        resultPane.setText("");
        passwordPanel.clearPassword();
        tokenPanel.clearToken();
        browserPanel.clearUrl();
        populateFromSessionState();
        toggleInputs(true);
        setLocationRelativeTo(frame);
        setVisible(true); // modal => blocks until disposed/hidden
    }

    public void displayLoginWindowThenSubmissionCenter(Environment environment) {
        JFrame frame = null;
        if (environment != null) {
            frame = Universe.frameForEnvironment(environment);
        }
        displayLoginWindow(frame);
        if (environment != null) {
            testAuthThenShowSubmitWindow(environment);
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                // White rounded card, matching the Submission Center cards.
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(LoginUi.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(LoginUi.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBackground(LoginUi.CARD_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        // Card sits on the default LAF gray, like the Submission Center window.
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        outer.add(panel, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);

        JLabel header = new JLabel("AFCT Server Login");
        boldFontAndChangeSize(header, 20);
        header.setForeground(LoginUi.TEXT_DARK);
        header.setHorizontalAlignment(SwingConstants.CENTER);

        c.insets = new Insets(0, 0, 14, 0);
        panel.add(header, c);

        c.gridy++;
        c.insets = new Insets(6, 0, 6, 0);
        panel.add(LoginUi.labeled("Server address", serverTF), c);

        c.gridy++;
        panel.add(buildModeRow(), c);

        modeCards.setOpaque(false);
        modeCards.add(passwordPanel, CARD_PASSWORD);
        modeCards.add(tokenPanel, CARD_TOKEN);
        modeCards.add(browserPanel, CARD_BROWSER);
        c.gridy++;
        panel.add(modeCards, c);

        // Login button — solid blue primary, like the Submit button
        c.gridy++;
        c.insets = new Insets(12, 0, 8, 0);
        loginButton.setPreferredSize(new Dimension(360, 38));
        loginButton.setBackground(LoginUi.ACCENT);
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setOpaque(true);
        loginButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        loginButton.setFont(loginButton.getFont().deriveFont(Font.BOLD));
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.add(loginButton, c);

        // Result pane with scroll
        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;

        resultPane.setEditable(false);
        resultPane.setFocusable(false);
        resultPane.setContentType("text/html");
        // Render the HTML status text with a smaller UI font.
        resultPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        resultPane.setFont(resultPane.getFont().deriveFont(11f));
        resultPane.setBackground(LoginUi.CARD_BG);
        resultPane.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Set preferred size for wrapping
        resultScrollPane.setPreferredSize(new Dimension(360, 80));
        resultScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LoginUi.CARD_BORDER, 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        resultScrollPane.getViewport().setBackground(LoginUi.CARD_BG);
        resultScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        resultScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        panel.add(resultScrollPane, c);

        // events
        loginButton.addActionListener(e -> attemptLogin());
        getRootPane().setDefaultButton(loginButton);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(passwordModeRadio);
        modeGroup.add(tokenModeRadio);
        modeGroup.add(browserModeRadio);
        passwordModeRadio.addActionListener(e -> applyMode());
        tokenModeRadio.addActionListener(e -> applyMode());
        browserModeRadio.addActionListener(e -> applyMode());

        // The account link points at whatever server the student has typed, and a
        // browser sign-in URL from an earlier attempt is for the OLD server, so it
        // must not survive an address edit: copying it would open the consent page
        // of a server the student is no longer signing in to.
        DocumentListener relink = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { serverChanged(); }
            public void removeUpdate(DocumentEvent e) { serverChanged(); }
            public void changedUpdate(DocumentEvent e) { serverChanged(); }
        };
        serverTF.getDocument().addDocumentListener(relink);
        serverChanged();

        setContentPane(outer);
    }

    private JPanel buildModeRow() {
        // The label sits above the radios, not beside them: four things in one row
        // is what was stretching the whole dialog past the 360px card width.
        JPanel radios = new JPanel(new GridBagLayout());
        radios.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 8);
        c.anchor = GridBagConstraints.LINE_START;

        JRadioButton[] all = {passwordModeRadio, tokenModeRadio, browserModeRadio};
        for (int i = 0; i < all.length; i++) {
            all[i].setFocusPainted(false);
            all[i].setOpaque(false);
            c.gridx = i;
            if (i == all.length - 1) c.insets = new Insets(0, 0, 0, 0);
            radios.add(all[i], c);
        }

        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel("Sign in with");
        boldFont(l);
        l.setForeground(LoginUi.TEXT_DARK);
        p.add(l, BorderLayout.NORTH);
        p.add(radios, BorderLayout.CENTER);
        return p;
    }

    private void applyMode() {
        String card = browserModeRadio.isSelected() ? CARD_BROWSER
                : tokenModeRadio.isSelected() ? CARD_TOKEN
                : CARD_PASSWORD;
        ((CardLayout) modeCards.getLayout()).show(modeCards, card);
        // A shown URL belongs to a finished or cancelled flow; its state and PKCE
        // pair are dead, so it must not be copied later.
        browserPanel.clearUrl();
        if (browserModeRadio.isSelected()) {
            loginButton.setText("Login in Browser");
        } else {
            loginButton.setText("Login");
        }
        pack();
    }

    private void serverChanged() {
        String base;
        try {
            base = ServerAddress.parse(serverTF.getText()).baseUrl();
        } catch (IllegalArgumentException ex) {
            // Half-typed address: point at what is there, better than a dead link.
            base = "https://" + serverTF.getText().trim();
        }
        tokenPanel.setAccountBase(base);
        browserPanel.clearUrl();
    }

    private void toggleInputs(boolean enabled) {
        serverTF.setEnabled(enabled);
        passwordModeRadio.setEnabled(enabled);
        tokenModeRadio.setEnabled(enabled);
        browserModeRadio.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        passwordPanel.setInputsEnabled(enabled);
        tokenPanel.setInputsEnabled(enabled);
        browserPanel.setInputsEnabled(enabled, browserModeRadio.isSelected());
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private void attemptLogin() {
        final String server = serverTF.getText().trim();
        final String email = passwordPanel.email();
        final String password = passwordPanel.password();
        final String signInToken = tokenPanel.token();
        final boolean tokenMode = tokenModeRadio.isSelected();
        final boolean browserMode = browserModeRadio.isSelected();

        setStatusText("Initializing connection...");
        toggleInputs(false);

        new SwingWorker<LoginResult, String>() {
            @Override
            protected LoginResult doInBackground() {
                try {
                    publish("Connecting to " + server + "...");
                    Thread.sleep(100); // Brief pause so user sees status

                    if (browserMode) {
                        SwingUtilities.invokeLater(browserPanel::clearUrl);
                        publish("Waiting for you to approve the sign-in in your browser...");
                        return sessionHandler.loginWithBrowser(server, url ->
                                SwingUtilities.invokeLater(() -> browserPanel.showAuthorizeUrl(url)));
                    }
                    if (tokenMode) {
                        publish("Checking sign-in token...");
                        return sessionHandler.loginWithToken(server, signInToken);
                    }
                    publish("Authenticating user...");
                    return sessionHandler.login(server, email, password);
                } catch (Exception ex) {
                    return LoginResult.getErrorResult(
                            ErrorMessages.userMessage(ex, "Unable to reach the server. Please try again.")
                    );
                }
            }

            @Override
            protected void process(java.util.List<String> statusMessages) {
                // Update status with the latest message
                if (!statusMessages.isEmpty()) {
                    setStatusText(statusMessages.get(statusMessages.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    LoginResult result = get();
                    if (result.status == LoginResult.LoginStatus.SUCCESS) {
                        setResultText(result.message, true);

                        // Every mode ends holding a bearer token, so "stay signed in"
                        // works the same way for all three: store the token when the
                        // box is ticked, otherwise it lives in memory for this run
                        // only. The password itself is never persisted.
                        boolean stay = tokenMode ? tokenPanel.staySignedIn()
                                : browserMode ? browserPanel.staySignedIn()
                                : passwordPanel.staySignedIn();
                        if (stay) {
                            sessionHandler.persistCurrentTokenForStaySignedIn();
                        } else {
                            sessionHandler.clearSavedSignInToken();
                        }

                        dispose();
                    } else if (result.status == LoginResult.LoginStatus.UNTRUSTED_CERT) {
                        handleUntrustedCertificate(result);
                    } else if (result.status == LoginResult.LoginStatus.CERT_CHANGED) {
                        handleChangedCertificate(result);
                    } else {
                        setResultText(result.message, false);
                    }
                } catch (Exception ex) {
                    setResultText(
                            "Login Failure: " + ErrorMessages.userMessage(ex, "An unexpected error occurred."),
                            false
                    );
                } finally {
                    toggleInputs(true);
                }
            }
        }.execute();
    }

    // ============================================================
    // Certificate trust
    // ============================================================

    /**
     * First contact with a server the platform does not trust: show the certificate
     * once and let the student decide. Trusting pins the fingerprint for this
     * origin and retries the sign-in.
     */
    private void handleUntrustedCertificate(LoginResult result) {
        java.security.cert.X509Certificate leaf = result.chain[0];
        String fingerprint = CertificatePins.fingerprintOf(leaf);

        String text = "AFCT does not recognize this server yet.\n\n"
                + "Server: " + result.origin + "\n"
                + "Issued to: " + leaf.getSubjectX500Principal().getName() + "\n"
                + "Issued by: " + leaf.getIssuerX500Principal().getName() + "\n"
                + "Valid: " + leaf.getNotBefore() + " to " + leaf.getNotAfter() + "\n"
                + "SHA-256: " + CertificatePins.displayFingerprint(fingerprint) + "\n\n"
                + "Many AFCT servers use a certificate they made themselves, and your\n"
                + "instructor can confirm the SHA-256 value above. If it matches, choose\n"
                + "Trust and AFCT will remember this server.";

        int choice = JOptionPane.showOptionDialog(this, text,
                "Trust this server?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                null, new Object[]{"Trust this server", "Cancel"}, "Cancel");

        if (choice == JOptionPane.YES_OPTION) {
            sessionHandler.pinServer(result.origin, leaf);
            attemptLogin();
        } else {
            setResultText("Not connected. The server was not trusted.", false);
        }
    }

    /**
     * The pinned fingerprint no longer matches and the platform does not vouch for
     * the replacement (a real certificate would have been accepted silently). This
     * is exactly what pinning exists to catch, so the default is refusal; Forget
     * covers the legitimate case of a server reinstalled with a new self-signed
     * certificate, and the next attempt shows the new one for approval.
     */
    private void handleChangedCertificate(LoginResult result) {
        String text = "This server's certificate has CHANGED since AFCT last saw it.\n\n"
                + "Server: " + result.origin + "\n\n"
                + "If your instructor reinstalled or reconfigured the server, this can be\n"
                + "normal: choose Forget this server, sign in again, and check the new\n"
                + "certificate when it is shown. If nothing changed on the server, stop\n"
                + "and tell your instructor; someone may be intercepting the connection.";

        int choice = JOptionPane.showOptionDialog(this, text,
                "Certificate changed", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE,
                null, new Object[]{"Forget this server", "Cancel"}, "Cancel");

        if (choice == JOptionPane.YES_OPTION) {
            sessionHandler.forgetServer(result.origin);
            setResultText("Server forgotten. Sign in again to review its current certificate.", false);
        } else {
            setResultText("Not connected. The changed certificate was refused.", false);
        }
    }

    private void setStatusText(String message) {
        resultPane.setText(
            "<html><body style='text-align: center; font-family: sans-serif; font-size: 12px; padding: 4px; color: #555;'>" +
            message +
            "</body></html>"
        );
        resultPane.setCaretPosition(0);
    }

    private void setResultText(String message, boolean isSuccess) {
        String coloredMessage = isSuccess ?
            colorHTMLSuccessMessage(message) :
            colorHTMLErrorMessage(message);

        resultPane.setText(
            "<html><body style='text-align: center; font-family: sans-serif; font-size: 12px; padding: 4px;'>" +
            coloredMessage +
            "</body></html>"
        );
        resultPane.setCaretPosition(0);
    }

    // ============================================================
    // State load
    // ============================================================

    private void populateFromSessionState() {
        // The last server and email that actually worked; never the password.
        serverTF.setText(sessionHandler.getSavedServer());
        passwordPanel.setEmail(sessionHandler.getSavedEmail());

        // A student who chose "stay signed in" lands here only when their stored
        // token stopped working, so keep the choice ticked on every form; the dead
        // token itself is never pre-filled.
        boolean stay = sessionHandler.staySignedInPreferred();
        passwordPanel.setStaySignedIn(stay);
        tokenPanel.setStaySignedIn(stay);
        browserPanel.setStaySignedIn(stay);
        applyMode();
    }
}
