package submission;

import javax.swing.*;
import java.awt.*;

import static java.awt.Font.getFont;
import static submission.SubmitWindow.TEXT_MUTED;

public class PillRenderer extends JLabel {
    private Color pillBg;
    private Color pillFg;

    public void updateStatusPill(Component component, String text) {
        String s = text.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("pend") || s.contains("queue") || s.contains("run")) {
            pillBg = new Color(0xFE, 0xF3, 0xC7); pillFg = new Color(0xB4, 0x53, 0x09); // amber
        } else if (s.contains("grade") || s.contains("accept") || s.contains("pass")
                || s.contains("success") || s.contains("complete") || s.contains("solve")) {
            pillBg = new Color(0xDC, 0xFC, 0xE7); pillFg = new Color(0x15, 0x80, 0x3D); // green
        } else if (s.contains("fail") || s.contains("error") || s.contains("reject")) {
            pillBg = new Color(0xFE, 0xE2, 0xE2); pillFg = new Color(0xB9, 0x1C, 0x1C); // red
        } else {
            pillBg = new Color(0xE5, 0xE7, 0xEB); pillFg = TEXT_MUTED; // neutral gray
        }
        component.setForeground(pillFg);
        component.setFont(component.getFont().deriveFont(Font.BOLD, 11f));
    }

    public void updatePill(Component component, Color bgColor, Color fgColor) {
        pillBg = bgColor;
        pillFg = fgColor;
        component.setForeground(pillFg);
        component.setFont(component.getFont().deriveFont(Font.BOLD, 11f));
    }

    public void paintPill(Component component, String text, Graphics g) {
        if (text != null && !text.isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Clear cell background first.
            g2.setColor(component.getBackground());
            g2.fillRect(0, 0, component.getWidth(), component.getHeight());
            // Pill behind the text.
            FontMetrics fm = g2.getFontMetrics(component.getFont());
            int textW = fm.stringWidth(text);
            int pillH = fm.getHeight() + 4;
            int pillW = textW + 16;
            // Top-aligned so it matches cells in rows made taller by wrapped feedback.
            int y = 3;
            g2.setColor(pillBg);
            g2.fillRoundRect(2, y, pillW, pillH, pillH, pillH);
            g2.setColor(pillFg);
            g2.drawString(text, 10, y + 2 + fm.getAscent());
            g2.dispose();
        } else {
            super.paintComponent(g);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintPill(this, getText(), g);
    }

}
