package submission;

import javax.swing.UIManager;
import java.awt.Color;

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
}
