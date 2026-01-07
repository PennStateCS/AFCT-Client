package submission;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class DetailsPanel extends JPanel {
    private JFrame parentFrame;
    private JToggleButton summaryButton;
    private String summaryText;
    private JTextPane detailsPane;
    private String detailsText = null;

    public DetailsPanel(JFrame parentFrame, String summaryText) {
        this.parentFrame = parentFrame;
        this.summaryText = summaryText;

        detailsPane = new JTextPane();
//        detailsPane.setContentType("text/html");
        setDetailsText("<html>This is the detailed content.<br>It can span multiple lines and include <b>HTML formatting</b>.</html>");
        summaryButton = new JToggleButton();

        initializeDetailsPanel();
    }

    public void setDetailsText(String detailsText) {
        this.detailsText = detailsText;
        String html =
                "<html>" +
                "<body style='margin:5px 10px;'>" +
                detailsText +
                "</body>" +
                "</html>";
        this.detailsPane.setText(html);
        this.detailsPane.setText(detailsText);
    }

    private void initializeDetailsPanel() {
        this.setLayout(new BorderLayout()); // Use a layout that manages space well

        // The content panel starts hidden
        detailsPane.setEditable(false);
        detailsPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        detailsPane.setMargin(new Insets(5, 10, 5, 10)); // padding: 10px horizontal, 5px vertical
        updateSummary(false);

        // Wrap the JEditorPane in a JPanel to control visibility more cleanly with pack()
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.add(detailsPane, BorderLayout.CENTER);
        contentWrapper.setVisible(false); // Initially hidden

        // Add the listener
        summaryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean isSelected = summaryButton.isSelected();
                contentWrapper.setVisible(isSelected);
                updateSummary(isSelected);
                // Repack the window to adjust layout for the new content size
                parentFrame.pack();
            }
        });

        this.add(summaryButton, BorderLayout.NORTH);
        this.add(contentWrapper, BorderLayout.CENTER);
    }

    private void updateSummary(boolean isOpen) {
        if (isOpen) {
            summaryButton.setText("Hide " + summaryText);
        } else {
            summaryButton.setText("Show " + summaryText);
        }
    }
}
