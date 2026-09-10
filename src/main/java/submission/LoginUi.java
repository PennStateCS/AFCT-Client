package submission;

import javax.swing.*;
import java.awt.*;

import static gui.Globals.boldFont;

/**
 * Shared styling for the login window and its mode panels: the palette (matching
 * the Submission Center cards) and the labeled-field building blocks.
 */
final class LoginUi {

    static final Color CARD_BG     = Color.WHITE;
    static final Color CARD_BORDER = new Color(0xE2, 0xE5, 0xEA);
    static final Color ACCENT      = new Color(0x42, 0x63, 0xEB);
    static final Color TEXT_DARK   = new Color(0x1F, 0x29, 0x37);

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
