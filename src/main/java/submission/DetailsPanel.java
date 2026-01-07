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
        detailsPane.setContentType("text/html");
        summaryButton = new JToggleButton();

        initializeDetailsPanel();

        // JUST FOR TESTING
        setDetailsText("<html>This is the detailed content.<br>It can span multiple lines and include <b>HTML formatting</b>.</html>");

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

        detailsPane.revalidate();
    }

    private void initializeDetailsPanel() {
        this.setLayout(new BorderLayout()); // Use a layout that manages space well

        // The content panel starts hidden
        detailsPane.setEditable(false);
        detailsPane.setMargin(new Insets(5, 10, 5, 10)); // padding: 10px horizontal, 5px vertical
        updateSummary(false);

        // Add a border around detailsPane
        detailsPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));


        // Wrap the JEditorPane in a JPanel to control visibility more cleanly with pack()
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.add(detailsPane, BorderLayout.CENTER);
        contentWrapper.setVisible(false); // Initially hidden

        // Add the listener
        summaryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: maybe find a better way to do this
                //  this current method could cause the window to become super tall (taller than monitor)
                //  when it should just get wider instead..
                // Save old width so window width does not change
//                int old_width = parentFrame.getWidth();
//                System.out.println(old_width);
//                old_width = parentFrame.getPreferredSize().width;
//                System.out.println(old_width);

//                int old_width = summaryButton.getWidth();


                boolean isSelected = summaryButton.isSelected();
                contentWrapper.setVisible(isSelected);
                updateSummary(isSelected);

//                System.out.println(detailsPane.getHeight());

                // Repack the window to adjust layout for the new content size
                parentFrame.pack();
//                parentFrame.setPreferredSize(new Dimension(old_width, parentFrame.getHeight()));
//                parentFrame.pack();
//                detailsPane.setPreferredSize(new Dimension(old_width, detailsPane.getHeight()));

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
