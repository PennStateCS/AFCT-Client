package submission;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static gui.Globals.setConstraints;


public class DetailsPanel extends JPanel {
    private JFrame parentFrame;
    private JToggleButton summaryButton;
    private String summaryText;
    private JTextPane detailsPane;
    private String detailsText = null;
    private JScrollPane detailsScrollPane;
    private boolean useScrollPane = false;
    private boolean useBorderLayout = true;

    public DetailsPanel(JFrame parentFrame, String summaryText) {
        this.parentFrame = parentFrame;
        this.summaryText = summaryText;

        detailsPane = new JTextPane();
        detailsPane.setContentType("text/html");
        summaryButton = new JToggleButton();

        detailsScrollPane = new JScrollPane(detailsPane);

        initializeDetailsPanel();

        // JUST FOR TESTING
        setDetailsText("<html>This is the detailed content.<br>It can span multiple lines and include <b>HTML formatting</b>.</html>");

    }

    public void setDetailsText(String detailsText) {
        this.detailsText = detailsText;
        // padding: 10px horizontal, 5px vertical
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
        if (useBorderLayout) {
            this.setLayout(new BorderLayout()); // Use a layout that manages space well
        } else {
            this.setLayout(new GridBagLayout());
        }

        // The content panel starts hidden
        detailsPane.setEditable(false);
        //detailsPane.setMargin(new Insets(5, 10, 5, 10)); // padding: 10px horizontal, 5px vertical
        updateSummary(false);

        if (useScrollPane) {
            // Add a border around detailsScrollPane
            detailsScrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        } else {
            // Add a border around detailsPane
            detailsPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }

        JPanel contentWrapper;
        GridBagConstraints c;
        if (useBorderLayout) {
            // Wrap the JEditorPane in a JPanel to control visibility more cleanly with pack()
            contentWrapper = new JPanel(new BorderLayout());
            if (useScrollPane) {
                contentWrapper.add(detailsScrollPane, BorderLayout.CENTER);
            } else {
                contentWrapper.add(detailsPane, BorderLayout.CENTER);
            }
        } else {
            contentWrapper = new JPanel(new GridBagLayout());
            c = setConstraints(1, 1, 0, 0, GridBagConstraints.CENTER);
            if (useScrollPane) {
                contentWrapper.add(detailsScrollPane, c);
            } else {
                contentWrapper.add(detailsPane, c);
            }
        }

        contentWrapper.setVisible(false); // Initially hidden

        if (useBorderLayout) {
            this.add(summaryButton, BorderLayout.NORTH);
            this.add(contentWrapper, BorderLayout.CENTER);
        } else {
            c = setConstraints(1, 0, 0, 0, GridBagConstraints.NORTH);
            this.add(summaryButton, c);
            c = setConstraints(1, 1, 0, 1, GridBagConstraints.NORTH);
            c.gridheight = 2;
            this.add(contentWrapper, c);
        }
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
    }

    private void updateSummary(boolean isOpen) {
        if (isOpen) {
            summaryButton.setText("Hide " + summaryText);
        } else {
            summaryButton.setText("Show " + summaryText);
        }
    }
}
