package submission;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * The Web browser form (RFC 8252 loopback sign-in). The URL is shown as a matter
 * of course, not only on error: Desktop.browse fails silently on some Linux
 * desktops and under WSL, so a copyable link is always the fallback.
 */
class BrowserLoginPanel extends JPanel {

    private final JCheckBox staySignedIn = LoginUi.staySignedInCheckBox();
    private final JTextField urlField = new JTextField();
    private final JButton copyUrl = new JButton("Copy link");
    private final JButton cancel = new JButton("Cancel sign-in");

    /** @param onCancel aborts the in-flight browser wait (SessionHandler::cancelBrowserSignIn). */
    BrowserLoginPanel(Runnable onCancel) {
        super(new GridBagLayout());
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 2, 0);

        add(LoginUi.wrappedLabel("Click Login and approve the sign-in in your web browser."
                + " Use this if you sign in through your university."
                + " On some servers the browser may warn about the certificate first."), c);

        c.gridy++;
        add(staySignedIn, c);

        c.gridy++;
        c.insets = new Insets(8, 0, 2, 0);
        add(new JLabel("If the browser did not open, visit this link yourself:"), c);

        urlField.setEditable(false);
        // Columns cap the preferred width; the URL scrolls within the field.
        urlField.setColumns(24);
        c.gridy++;
        c.insets = new Insets(0, 0, 2, 0);
        add(urlField, c);

        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints b = new GridBagConstraints();
        b.gridy = 0;
        b.gridx = 0;
        b.insets = new Insets(0, 0, 0, 8);
        copyUrl.setEnabled(false);
        copyUrl.addActionListener(e -> {
            StringSelection selection = new StringSelection(urlField.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        });
        buttons.add(copyUrl, b);
        b.gridx = 1;
        b.insets = new Insets(0, 0, 0, 0);
        cancel.setEnabled(false);
        cancel.addActionListener(e -> onCancel.run());
        buttons.add(cancel, b);
        c.gridy++;
        c.insets = new Insets(4, 0, 4, 0);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        add(buttons, c);
    }

    boolean staySignedIn() {
        return staySignedIn.isSelected();
    }

    void setStaySignedIn(boolean selected) {
        staySignedIn.setSelected(selected);
    }

    /** Shows the consent URL for the in-flight attempt and arms the copy button. */
    void showAuthorizeUrl(String url) {
        urlField.setText(url);
        copyUrl.setEnabled(true);
    }

    /** A shown URL belongs to a dead flow (or the wrong server) once cleared. */
    void clearUrl() {
        urlField.setText("");
        copyUrl.setEnabled(false);
    }

    /**
     * While an attempt runs, everything else is disabled and Cancel is the one
     * control that must keep working.
     */
    void setInputsEnabled(boolean enabled, boolean thisModeActive) {
        staySignedIn.setEnabled(enabled);
        cancel.setEnabled(!enabled && thisModeActive);
    }
}
