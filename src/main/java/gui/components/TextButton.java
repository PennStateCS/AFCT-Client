package gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static gui.Globals.setPointerCursor;

public class TextButton extends JLabel {
    private String text;
    private Color mainColor;
    private Color hoverColor;

    public TextButton(String text, Color mainColor, Color hoverColor, Runnable runnable) {
        super();
        this.text = text;
        this.mainColor = mainColor;
        this.hoverColor = hoverColor;
        setup(runnable);
    }

    public TextButton(String text, Runnable runnable) {
        super();
        this.text = text;
        this.mainColor = new Color(75, 163, 251);
        this.hoverColor = new Color(120, 188, 255);
        setup(runnable);
    }

    private void setup(Runnable runnable) {
        this.setText(this.text);
        this.setForeground(mainColor);

        setPointerCursor(this);
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // the user clicks on the label
                // TODO: which way should this be run?
                SwingUtilities.invokeLater(runnable);
                //runnable.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // the mouse has entered the label
                setForeground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // the mouse has exited the label
                setForeground(mainColor);
            }
        });
    }
}
