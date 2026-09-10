package submission;

import gui.Globals;
import gui.components.LinkLabel;
import gui.environment.Universe;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

import static gui.Globals.*;

public class LoginWindow extends JDialog {

    // Palette shared with the Submission Center styling (cosmetic only)
    private static final Color CARD_BG     = Color.WHITE;
    private static final Color CARD_BORDER = new Color(0xE2, 0xE5, 0xEA);
    private static final Color ACCENT      = new Color(0x42, 0x63, 0xEB);
    private static final Color TEXT_DARK   = new Color(0x1F, 0x29, 0x37);

    private final SessionHandler sessionHandler;

    // One address field; ServerAddress.parse handles scheme and port. Starts empty
    // on purpose: prefilling a development address taught people to trust the box.
    private final JTextField serverTF = new JTextField();
    private final JTextField emailTF = new JTextField();
    private final JPasswordField passwordTF = new JPasswordField();
    private final char defaultPasswordEchoChar = passwordTF.getEchoChar();

    // Two ways in: the password form, or a token created on the web account page.
    // The token is the only path for a student whose AFCT session lives inside an
    // LMS iframe (Canvas etc.), so it is a first-class mode, not a fallback.
    private final JRadioButton passwordModeRadio = new JRadioButton("Email and password", true);
    private final JRadioButton tokenModeRadio = new JRadioButton("Sign-in token");
    private final JTextField tokenTF = new JTextField();
    private final JCheckBox staySignedInCheckBox =
            new JCheckBox("Stay signed in on this computer");
    private final JPanel modeCards = new JPanel(new CardLayout());
    private final LinkLabel accountLink = new LinkLabel("your AFCT account page", "");
    private static final String CARD_PASSWORD = "password";
    private static final String CARD_TOKEN = "token";

    private final JCheckBox showPasswordCheckBox =
            new JCheckBox("Show password");

    private final JCheckBox rememberMeCheckBox =
            new JCheckBox("Remember Me");

    private final JButton loginButton = new JButton("Login");
    private final JTextPane resultPane = new JTextPane();
    private final JScrollPane resultScrollPane = new JScrollPane(resultPane);

    public LoginWindow(SessionHandler handler) {
        super((Frame) null, "Login - " + Globals.APP_NAME, true);
        this.sessionHandler = handler;

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

    public void displayLoginWindow(JFrame frame, boolean shouldAutoLogin) {
        resultPane.setText("");
        passwordTF.setText("");
        tokenTF.setText("");
        populateFromSessionState();
        toggleInputs(true);
        setLocationRelativeTo(frame);
        if (shouldAutoLogin) {
            attemptLogin();
        }
        setVisible(true); // modal => blocks until disposed/hidden
    }

    public void displayLoginWindow(JFrame frame) {
        displayLoginWindow(frame, false);
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
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBackground(CARD_BG);
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
        header.setForeground(TEXT_DARK);
        header.setHorizontalAlignment(SwingConstants.CENTER);

        c.insets = new Insets(0, 0, 14, 0);
        panel.add(header, c);

        c.gridy++;
        c.insets = new Insets(6, 0, 6, 0);
        panel.add(labeled("Server address", serverTF), c);

        c.gridy++;
        panel.add(buildModeRow(), c);

        modeCards.setOpaque(false);
        modeCards.add(buildPasswordCard(), CARD_PASSWORD);
        modeCards.add(buildTokenCard(), CARD_TOKEN);
        c.gridy++;
        panel.add(modeCards, c);

        // small options row (show password + SSL validation)
        c.gridy++;
        c.insets = new Insets(10, 0, 4, 0);
        panel.add(buildOptionsRow(), c);

        // Login button — solid blue primary, like the Submit button
        c.gridy++;
        c.insets = new Insets(12, 0, 8, 0);
        loginButton.setPreferredSize(new Dimension(360, 38));
        loginButton.setBackground(ACCENT);
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
        resultPane.setBackground(CARD_BG);
        resultPane.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Set preferred size for wrapping
        resultScrollPane.setPreferredSize(new Dimension(360, 80));
        resultScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        resultScrollPane.getViewport().setBackground(CARD_BG);
        resultScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        resultScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        panel.add(resultScrollPane, c);

        // events
        loginButton.addActionListener(e -> attemptLogin());
        getRootPane().setDefaultButton(loginButton);

        showPasswordCheckBox.setFocusPainted(false);
        showPasswordCheckBox.setOpaque(false);
        showPasswordCheckBox.addActionListener(e -> {
            if (showPasswordCheckBox.isSelected()) {
                passwordTF.setEchoChar((char) 0);
            } else {
                passwordTF.setEchoChar(defaultPasswordEchoChar);
            }
        });

        rememberMeCheckBox.setFocusPainted(false);
        rememberMeCheckBox.setOpaque(false);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(passwordModeRadio);
        modeGroup.add(tokenModeRadio);
        passwordModeRadio.addActionListener(e -> applyMode());
        tokenModeRadio.addActionListener(e -> applyMode());

        // The account link points at whatever server the student has typed.
        DocumentListener relink = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshAccountLink(); }
            public void removeUpdate(DocumentEvent e) { refreshAccountLink(); }
            public void changedUpdate(DocumentEvent e) { refreshAccountLink(); }
        };
        serverTF.getDocument().addDocumentListener(relink);
        refreshAccountLink();

        setContentPane(outer);
    }

    private JPanel buildModeRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 12);
        c.anchor = GridBagConstraints.LINE_START;

        JLabel label = new JLabel("Sign in with");
        boldFont(label);
        label.setForeground(TEXT_DARK);

        c.gridx = 0;
        row.add(label, c);

        passwordModeRadio.setFocusPainted(false);
        passwordModeRadio.setOpaque(false);
        c.gridx = 1;
        row.add(passwordModeRadio, c);

        tokenModeRadio.setFocusPainted(false);
        tokenModeRadio.setOpaque(false);
        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(tokenModeRadio, c);

        return row;
    }

    private JPanel buildPasswordCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);

        card.add(labeled("Email", emailTF), c);
        c.gridy++;
        card.add(labeled("Password", passwordTF), c);

        return card;
    }

    private JPanel buildTokenCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);

        card.add(labeled("Sign-in token", tokenTF), c);

        // Off by default on purpose: Preferences are per OS user, so on a lab machine
        // with a shared login a saved token would greet the next student who sits down.
        staySignedInCheckBox.setFocusPainted(false);
        staySignedInCheckBox.setOpaque(false);
        c.gridy++;
        c.insets = new Insets(2, 0, 2, 0);
        card.add(staySignedInCheckBox, c);

        JPanel hint = new JPanel();
        hint.setLayout(new BoxLayout(hint, BoxLayout.PAGE_AXIS));
        hint.setOpaque(false);

        JPanel linkLine = new JPanel();
        linkLine.setLayout(new BoxLayout(linkLine, BoxLayout.LINE_AXIS));
        linkLine.setOpaque(false);
        linkLine.add(new JLabel("Create one on "));
        linkLine.add(accountLink);
        linkLine.add(new JLabel("."));
        linkLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.add(linkLine);

        JLabel ltiHint = new JLabel("If you open AFCT from Canvas or another LMS, sign in this way.");
        ltiHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.add(ltiHint);

        c.gridy++;
        c.insets = new Insets(2, 0, 6, 0);
        card.add(hint, c);

        return card;
    }

    private void applyMode() {
        boolean tokenMode = tokenModeRadio.isSelected();
        ((CardLayout) modeCards.getLayout()).show(modeCards, tokenMode ? CARD_TOKEN : CARD_PASSWORD);
        // Show password and Remember Me only make sense for the password form.
        showPasswordCheckBox.setEnabled(!tokenMode);
        rememberMeCheckBox.setEnabled(!tokenMode);
        pack();
    }

    private void refreshAccountLink() {
        String base;
        try {
            base = ServerAddress.parse(serverTF.getText()).baseUrl();
        } catch (IllegalArgumentException ex) {
            // Half-typed address: point at what is there, better than a dead link.
            base = "https://" + serverTF.getText().trim();
        }
        // ?tab=tokens lands directly on the App tokens tab of the account page.
        accountLink.update("your AFCT account page", base + "/dashboard/account?tab=tokens");
    }

    private JPanel buildOptionsRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 12);
        c.anchor = GridBagConstraints.LINE_START;

        c.gridx = 0;
        row.add(showPasswordCheckBox, c);

        c.gridx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(rememberMeCheckBox, c);

        return row;
    }

    private JPanel labeled(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);

        JLabel l = new JLabel(label);
        boldFont(l);
        l.setForeground(TEXT_DARK);

        if (comp instanceof JTextField tf) {
            tf.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CARD_BORDER),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        }

        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private void toggleInputs(boolean enabled) {
        boolean tokenMode = tokenModeRadio.isSelected();
        serverTF.setEnabled(enabled);
        emailTF.setEnabled(enabled);
        passwordTF.setEnabled(enabled);
        tokenTF.setEnabled(enabled);
        staySignedInCheckBox.setEnabled(enabled);
        passwordModeRadio.setEnabled(enabled);
        tokenModeRadio.setEnabled(enabled);
        showPasswordCheckBox.setEnabled(enabled && !tokenMode);
        rememberMeCheckBox.setEnabled(enabled && !tokenMode);
        loginButton.setEnabled(enabled);
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private void attemptLogin() {
        final String server = serverTF.getText().trim();
        final String email = emailTF.getText().trim();
        final String password = new String(passwordTF.getPassword());
        final String signInToken = tokenTF.getText().trim();
        final boolean tokenMode = tokenModeRadio.isSelected();

        setStatusText("Initializing connection...");
        toggleInputs(false);

        new SwingWorker<LoginResult, String>() {
            @Override
            protected LoginResult doInBackground() {
                try {
                    publish("Connecting to " + server + "...");
                    Thread.sleep(100); // Brief pause so user sees status

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
                    // Remember Me belongs to the password form only; a token sign-in
                    // leaves any saved credentials exactly as they were.
                    if (!tokenMode && !rememberMeCheckBox.isSelected()) {
                        // Respect opt-out immediately, even on failed login attempts.
                        sessionHandler.clearSavedCredentials();
                    }

                    if (result.status == LoginResult.LoginStatus.SUCCESS) {
                        setResultText(result.message, true);

                        // Handle Remember Me
                        if (!tokenMode && rememberMeCheckBox.isSelected()) {
                            sessionHandler.saveCredentials(email, password);
                        }
                        // Store the token only on a successful sign-in with the box
                        // ticked; unticked means it lives in memory for this run only.
                        if (tokenMode) {
                            if (staySignedInCheckBox.isSelected()) {
                                sessionHandler.saveSignInToken(signInToken);
                            } else {
                                sessionHandler.clearSavedSignInToken();
                            }
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
        // The last server that actually worked, whatever sign-in mode was used.
        serverTF.setText(sessionHandler.getSavedServer());

        // Load Remember Me credentials if enabled
        if (sessionHandler.hasRememberMe()) {
            rememberMeCheckBox.setSelected(true);
            emailTF.setText(sessionHandler.getSavedEmail());
            passwordTF.setText(sessionHandler.getSavedPassword());
        }

        // A student who chose "stay signed in" lands here only when their stored token
        // stopped working, so open on the token form. The dead token itself is never
        // pre-filled: they need a fresh one.
        if (sessionHandler.staySignedInPreferred()) {
            tokenModeRadio.setSelected(true);
            staySignedInCheckBox.setSelected(true);
        }
        applyMode();
    }
}
