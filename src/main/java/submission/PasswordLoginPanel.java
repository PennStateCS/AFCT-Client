package submission;

import javax.swing.*;
import java.awt.*;

/**
 * The Email and password form. Owns only its widgets and their state; the login
 * window decides when to attempt and what to do with the outcome. The password is
 * never persisted anywhere: "stay signed in" stores the bearer token instead.
 */
class PasswordLoginPanel extends JPanel {

    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final char defaultEchoChar = passwordField.getEchoChar();
    private final JCheckBox showPassword = new JCheckBox("Show password");
    private final JCheckBox staySignedIn = LoginUi.staySignedInCheckBox();

    PasswordLoginPanel() {
        super(new GridBagLayout());
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);

        add(LoginUi.labeled("Email", emailField), c);
        c.gridy++;
        add(LoginUi.labeled("Password", passwordField), c);

        showPassword.setFocusPainted(false);
        showPassword.setOpaque(false);
        showPassword.addActionListener(e ->
                passwordField.setEchoChar(showPassword.isSelected() ? (char) 0 : defaultEchoChar));

        JPanel options = new JPanel(new GridBagLayout());
        options.setOpaque(false);
        GridBagConstraints o = new GridBagConstraints();
        o.gridy = 0;
        o.gridx = 0;
        o.anchor = GridBagConstraints.LINE_START;
        o.insets = new Insets(0, 0, 0, 12);
        options.add(showPassword, o);
        o.gridx = 1;
        o.insets = new Insets(0, 0, 0, 0);
        options.add(staySignedIn, o);

        c.gridy++;
        c.insets = new Insets(8, 0, 2, 0);
        add(options, c);
    }

    String email() {
        return emailField.getText().trim();
    }

    String password() {
        return new String(passwordField.getPassword());
    }

    boolean staySignedIn() {
        return staySignedIn.isSelected();
    }

    void setEmail(String email) {
        emailField.setText(email);
    }

    void clearPassword() {
        passwordField.setText("");
    }

    void setStaySignedIn(boolean selected) {
        staySignedIn.setSelected(selected);
    }

    void setInputsEnabled(boolean enabled) {
        emailField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        showPassword.setEnabled(enabled);
        staySignedIn.setEnabled(enabled);
    }
}
