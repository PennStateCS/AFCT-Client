package submission;

import gui.Globals;
import gui.environment.Universe;

import javax.swing.*;
import java.awt.*;

import static gui.Globals.*;

public class LoginWindow extends JDialog {

    // Palette shared with the Submission Center styling (cosmetic only)
    private static final Color CARD_BG     = Color.WHITE;
    private static final Color CARD_BORDER = new Color(0xE2, 0xE5, 0xEA);
    private static final Color ACCENT      = new Color(0x42, 0x63, 0xEB);
    private static final Color TEXT_DARK   = new Color(0x1F, 0x29, 0x37);

    private final SessionHandler sessionHandler;

    private final JTextField serverTF = new JTextField("https://10.144.18.20");
    private final JTextField portTF = new JTextField("443");
    private final JTextField emailTF = new JTextField();
    private final JPasswordField passwordTF = new JPasswordField();
    private final char defaultPasswordEchoChar = passwordTF.getEchoChar();

    private final JCheckBox validateSSLCheckBox =
            new JCheckBox("Validate SSL Certificate");

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

    public void displayLoginWindow(JFrame frame) {
        resultPane.setText("");
        passwordTF.setText("");
        populateFromSessionState();
        toggleInputs(true);
        setLocationRelativeTo(frame);
        setVisible(true); // modal => blocks until disposed/hidden
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
        panel.add(labeled("Server", serverTF), c);

        c.gridy++;
        panel.add(labeled("Port", portTF), c);

        c.gridy++;
        panel.add(labeled("Email", emailTF), c);

        c.gridy++;
        panel.add(labeled("Password", passwordTF), c);

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

        validateSSLCheckBox.setFocusPainted(false);
        validateSSLCheckBox.setOpaque(false);
        rememberMeCheckBox.setFocusPainted(false);
        rememberMeCheckBox.setOpaque(false);

        setContentPane(outer);
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
        row.add(validateSSLCheckBox, c);

        c.gridx = 2;
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
        serverTF.setEnabled(enabled);
        portTF.setEnabled(enabled);
        emailTF.setEnabled(enabled);
        passwordTF.setEnabled(enabled);
        validateSSLCheckBox.setEnabled(enabled);
        showPasswordCheckBox.setEnabled(enabled);
        rememberMeCheckBox.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }

    // ============================================================
    // LOGIN
    // ============================================================

    private void attemptLogin() {
        final String server = serverTF.getText().trim();
        final String port = portTF.getText().trim();
        final String email = emailTF.getText().trim();
        final String password = new String(passwordTF.getPassword());

        // checkbox means "validate cert" => insecureTls = false
        final boolean insecureTls = !validateSSLCheckBox.isSelected();

        // Show which mode we're using
        String sslMode = insecureTls ? "SSL validation: OFF" : "SSL validation: ON";
        setStatusText("Initializing connection... (" + sslMode + ")");
        toggleInputs(false);

        new SwingWorker<LoginResult, String>() {
            @Override
            protected LoginResult doInBackground() {
                try {
                    publish("Configuring TLS settings...");
                    Thread.sleep(100); // Brief pause so user sees status

                    publish("Connecting to " + server + ":" + port + "...");
                    Thread.sleep(100);

                    publish("Authenticating user...");
                    LoginResult result = sessionHandler.login(server, port, email, password, insecureTls);

                    return result;
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
                    // Respect opt-out immediately, even on failed login attempts.
                    if (!rememberMeCheckBox.isSelected()) {
                        sessionHandler.clearSavedCredentials();
                    }

                    if (result.status == LoginResult.LoginStatus.SUCCESS) {
                        setResultText(result.message, true);

                        // Handle Remember Me
                        if (rememberMeCheckBox.isSelected()) {
                            sessionHandler.saveCredentials(server, port, email, password);
                        }

                        dispose();
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
        // Load SSL validation preference
        validateSSLCheckBox.setSelected(!sessionHandler.isInsecureTls());

        // Load Remember Me credentials if enabled
        if (sessionHandler.hasRememberMe()) {
            rememberMeCheckBox.setSelected(true);
            serverTF.setText(sessionHandler.getSavedServer());
            portTF.setText(sessionHandler.getSavedPort());
            emailTF.setText(sessionHandler.getSavedEmail());
            passwordTF.setText(sessionHandler.getSavedPassword());
        }
    }
}
