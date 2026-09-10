package submission;

import javax.swing.*;
import java.awt.*;

import static gui.Globals.boldFont;

/**
 * Shared styling for the login window and its mode panels: the palette (matching
 * the Submission Center cards) and the labeled-field building blocks.
 */
final class LoginUi {

    // The palette lives in Theme, shared with the Submission Center; these are
    // kept as aliases so the login classes read naturally.
    static final Color CARD_BG     = Theme.CARD_BG;
    static final Color CARD_BORDER = Theme.CARD_BORDER;
    static final Color ACCENT      = Theme.ACCENT;
    static final Color TEXT_DARK   = Theme.TEXT_DARK;

    private LoginUi() {}

    /** A bold label above a field; text fields get the shared border treatment. */
    static JPanel labeled(String label, JComponent comp) {
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

    /**
     * A wrapping label. A plain html JLabel reports its unwrapped width as its
     * preferred size, which is what stretches a pack()ed dialog; a body width
     * makes it wrap at the card's width instead.
     */
    static JLabel wrappedLabel(String text) {
        return new JLabel("<html><body style='width:330px'>" + text + "</body></html>");
    }

    static JCheckBox staySignedInCheckBox() {
        JCheckBox box = new JCheckBox("Stay signed in on this computer");
        box.setFocusPainted(false);
        box.setOpaque(false);
        return box;
    }
}
