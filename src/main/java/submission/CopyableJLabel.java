package submission;

import javax.swing.*;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static gui.Globals.setDefaultCursor;
import static gui.Globals.setPointerCursor;

public class CopyableJLabel extends JLabel {
    public CopyableJLabel(String text) {
        super(text);

        setPointerCursor(this);

        JLabel label = this;

        // TODO: show some "copied to clipboard" message/popup when clicked
        //  also: handle IllegalStateException when calling clipboard.setContents();
        this.addMouseListener(new  MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Create a StringSelection object with the desired string
                StringSelection selection = new StringSelection(label.getText());

                // Get the system clipboard
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

                // Set the clipboard contents
                clipboard.setContents(selection, selection);

                // Give user feedback that the value was copied
                setDefaultCursor(label);
                int delay = 500;
                delay = 750;
                // Create and start the Swing Timer
                Timer timer = new Timer(delay, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // This code runs after the delay
                        setPointerCursor(label);
                    }
                });
                timer.setRepeats(false); // Ensure the timer only runs once
                timer.start();
            }
        });
    }
}
