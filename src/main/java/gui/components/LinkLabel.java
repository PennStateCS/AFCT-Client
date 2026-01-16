package gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static gui.Globals.allowHTMLInComponent;
import static gui.Globals.setPointerCursor;

public class LinkLabel extends JLabel {
    private static final Pattern linkRegex = Pattern.compile("(.*?)(\\[)(.+?)(]\\()(.+?)(\\))(.*?)");
    private String linkText;
    private String underlinedLinkText;
    private String linkUrl;

    public LinkLabel(String linkText, String linkUrl) {
        super();
        this.update(linkText, linkUrl);

        allowHTMLInComponent(this);

        LinkLabel link = this;
        setPointerCursor(this);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // the user clicks on the label
                try {
                    Desktop.getDesktop().browse(new URI(link.linkUrl));
                } catch (IOException | URISyntaxException e1) {
                    e1.printStackTrace();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                // the mouse has entered the label
                link.setText(link.underlinedLinkText);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // the mouse has exited the label
                link.setText(link.linkText);
            }
        });
    }

    public static JPanel getWithLinks(String text) {
        String[] lines = text.split("[\n\r]");
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

        for (String line : lines) {
            JPanel linePanel = new JPanel();
            linePanel.setLayout(new BoxLayout(linePanel, BoxLayout.LINE_AXIS));
            Matcher m = linkRegex.matcher(line);
            int end = 0;
            while (m.find()) {
                linePanel.add(new JLabel(m.group(1)));
                end = m.end();
                linePanel.add(new LinkLabel(m.group(3), m.group(5)));
            }
            linePanel.add(new JLabel(line.substring(end)));
            linePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(linePanel);
        }
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    public void update(String linkText, String linkUrl) {
        this.underlinedLinkText = "<html> <a href=\"" + linkUrl + "\">" + linkText + "</a></html>";
        this.linkText = "<html> <a href=\"" + linkUrl + "\" style=\"text-decoration:none;\">" + linkText + "</a></html>";
        this.setText(this.linkText);
        this.linkUrl = linkUrl;
    }
}

