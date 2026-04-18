package submission;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class DetailsPanel2 extends JPanel {
    private final JToggleButton detailsToggle;
    private final JPanel detailsPanel;
    private final JTextArea detailsText;
    private final JPanel card;
    private String currentDetailsText = " ";

    public DetailsPanel2() {
        detailsToggle = new JToggleButton("View details ▸");
        detailsPanel = new JPanel(new BorderLayout());
        detailsText = new JTextArea();
        card = new JPanel(new GridBagLayout());

        initializeDetailsPanel();
    }

    public void toggle(boolean enable) {
        detailsToggle.setEnabled(enable);
        if (!enable) {
            this.toggleDetailsPanel(false);
        }
    }

    public void disableDetailsPanel() {
        detailsToggle.setEnabled(false);
        this.toggleDetailsPanel(false);
    }

    private void doDetailsTextUpdate() {
        SwingUtilities.invokeLater(() -> {
            if (this.detailsPanel.isVisible()) {
                this.detailsText.setText(currentDetailsText);
            }
            //detailsText.setCaretPosition(0);
            // Important for layout recalculation in Swing
//            detailsPanel.revalidate();
//            detailsPanel.repaint();
//            detailsText.revalidate();
//            detailsText.repaint();
            card.revalidate();
            card.repaint();
        });
    }

    public void setDetailsText(String text) {
        this.currentDetailsText = text;
        if (this.detailsPanel.isVisible()) {
            doDetailsTextUpdate();
        }
    }

    private void toggleDetailsPanel(boolean enable) {
        detailsPanel.setVisible(enable);
        detailsToggle.setText(enable ? "Hide details ▾" : "View details ▸");
        detailsToggle.setSelected(enable);

        doDetailsTextUpdate();
    }

    private void initializeDetailsPanel() {
        setLayout(new GridBagLayout()); // to center the card

//        card.setBorder(new CompoundBorder(
//                new LineBorder(new Color(210, 210, 210)),
//                new EmptyBorder(16, 16, 16, 16)
//        ));
        card.setBorder(new LineBorder(new Color(210, 210, 210)));
        card.setBackground(Color.WHITE);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;

        // View details toggle (link-like)
        detailsToggle.setBorderPainted(false);
        detailsToggle.setContentAreaFilled(false);
        detailsToggle.setFocusPainted(false);
        detailsToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailsToggle.setHorizontalAlignment(SwingConstants.LEFT);

        c.gridy = 0;
        //c.insets = new Insets(0, 0, 12, 0);
        card.add(detailsToggle, c);

        // Details panel (collapsible)
//        detailsPanel.setBorder(new CompoundBorder(
//                new LineBorder(new Color(230, 230, 230)),
//                new EmptyBorder(10, 12, 10, 12)
//        ));
        //detailsPanel.setBorder(new EmptyBorder(6, 12, 6, 12));
        detailsPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)),
                new EmptyBorder(6, 12, 6, 12)
        ));
        detailsPanel.setBackground(new Color(250, 250, 250));

        detailsText.setEditable(false);
        detailsText.setOpaque(false);
        detailsText.setLineWrap(true);
        detailsText.setWrapStyleWord(true);
        detailsText.setFont(UIManager.getFont("Label.font"));

        detailsPanel.add(detailsText, BorderLayout.CENTER);
        detailsPanel.setVisible(false);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(detailsPanel, c);

        // Center the card on the page
        c = new GridBagConstraints();
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(card, c);

        // Behavior: toggle show/hide
        detailsToggle.addActionListener(e -> {
            boolean show = detailsToggle.isSelected();
            toggleDetailsPanel(show);
        });
    }


}
