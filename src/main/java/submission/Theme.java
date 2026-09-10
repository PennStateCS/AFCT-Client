package submission;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * The one palette for the AFCT windows (login and Submission Center). These used
 * to be duplicated per window, which is how the two drift apart.
 */
final class Theme {

    /** Same default LAF panel gray everywhere. */
    static final Color BG = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background") : new Color(0xF5, 0xF6, 0xF8);
    static final Color CARD_BG      = Color.WHITE;                 // card background
    static final Color CARD_BORDER  = new Color(0xE2, 0xE5, 0xEA); // subtle card outline
    static final Color ACCENT       = new Color(0x42, 0x63, 0xEB); // primary blue
    static final Color TEXT_DARK    = new Color(0x1F, 0x29, 0x37);
    static final Color TEXT_MUTED   = new Color(0x6B, 0x72, 0x80);
    static final Color SELECTION_BG = new Color(0xE7, 0xF0, 0xFE); // light-blue row highlight

    static final Color SUCCESS_TEXT = new Color(0x15, 0x80, 0x3D);
    static final Color DANGER_TEXT  = new Color(0xB9, 0x1C, 0x1C);

    private Theme() {}

    /** Solid blue primary action button. */
    static void stylePrimaryButton(JButton b) {
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setFont(b.getFont().deriveFont(Font.BOLD));
    }

    /** Light-blue tinted button that complements the solid primary blue. */
    static void styleTintedButton(JButton b) {
        b.setBackground(SELECTION_BG);
        b.setForeground(ACCENT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC7, 0xD7, 0xFB)),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
    }
}
