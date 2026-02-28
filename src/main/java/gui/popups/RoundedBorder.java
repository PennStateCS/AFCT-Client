package gui.popups;

import javax.swing.border.Border;
import java.awt.*;

public class RoundedBorder implements Border {
    private int radius;
    private int vrtPad = -1;
    private int hozPad = -1;

    public RoundedBorder(int radius) {
        this.radius = radius;
    }

    public RoundedBorder(int radius, int vrtPad, int hozPad) {
        this.vrtPad = vrtPad;
        this.hozPad = hozPad;
        this.radius = radius;
    }

    public Insets getBorderInsets(Component c) {
        return new Insets(this.radius+1, this.radius+1, this.radius+2, this.radius);
    }


    public boolean isBorderOpaque() {
        return true;
    }


    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        //g.drawRoundRect(x, y, width - 1, height -1, radius, radius);
        g.drawRoundRect(x, y, width - 1, height -1, radius, radius);
    }
}

