package submission;

import gui.components.LinkLabel;

import javax.swing.*;
import java.awt.*;

/**
 * The Sign-in token form: paste a token created on the web account page. The only
 * path for a student whose AFCT session lives inside an LMS iframe (Canvas etc.),
 * so it is a first-class mode, not a fallback.
 */
class TokenLoginPanel extends JPanel {

    private final JTextField tokenField = new JTextField();
    private final JCheckBox staySignedIn = LoginUi.staySignedInCheckBox();
    private final LinkLabel accountLink = new LinkLabel("your AFCT account page", "");

    TokenLoginPanel() {
        super(new GridBagLayout());
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 6, 0);

        add(LoginUi.labeled("Sign-in token", tokenField), c);

        // Off by default on purpose: Preferences are per OS user, so on a lab machine
        // with a shared login a saved token would greet the next student who sits down.
        c.gridy++;
        c.insets = new Insets(2, 0, 2, 0);
        add(staySignedIn, c);

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

        JLabel ltiHint = LoginUi.wrappedLabel(
                "If you open AFCT from Canvas or another LMS, sign in this way.");
        ltiHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.add(ltiHint);

        c.gridy++;
        c.insets = new Insets(2, 0, 6, 0);
        add(hint, c);
    }

    String token() {
        return tokenField.getText().trim();
    }

    boolean staySignedIn() {
        return staySignedIn.isSelected();
    }

    void clearToken() {
        tokenField.setText("");
    }

    void setStaySignedIn(boolean selected) {
        staySignedIn.setSelected(selected);
    }

    /** Points the account link at the typed server; ?tab=tokens lands on App tokens. */
    void setAccountBase(String baseUrl) {
        accountLink.update("your AFCT account page", baseUrl + "/dashboard/account?tab=tokens");
    }

    void setInputsEnabled(boolean enabled) {
        tokenField.setEnabled(enabled);
        staySignedIn.setEnabled(enabled);
    }
}
