package submission;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/** White card with rounded corners and a subtle outline, painted manually. */
class CardPanel extends JPanel {

    private static final int ARC = 14;

    CardPanel(LayoutManager lm) {
        super(lm);
        setOpaque(false); // we paint the rounded background ourselves
        setBackground(Theme.CARD_BG);
    }

    /** Subtle rounded-look card border with inner padding, matching the mockup cards. */
    static Border cardBorder() {
        return BorderFactory.createEmptyBorder(5, 5, 5, 5);
    }

    /** Small all-caps blue section heading, like "SELECTED ASSIGNMENT" in the mockup. */
    static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setForeground(Theme.ACCENT);
        return l;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.dispose();
        super.paintComponent(g);
    }
}
