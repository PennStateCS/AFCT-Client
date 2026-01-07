package submission;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class DetailsPanel2 extends JPanel {
    private final JToggleButton detailsToggle;
    private final JPanel detailsPanel;
    private final JTextArea detailsText;

    public DetailsPanel2() {
        detailsToggle = new JToggleButton("View details ▸");
        detailsPanel = new JPanel(new BorderLayout());
        detailsText = new JTextArea();

        initializeDetailsPanel();
    }

    private void initializeDetailsPanel() {
        setLayout(new GridBagLayout()); // to center the card

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new CompoundBorder(
                new LineBorder(new Color(210, 210, 210)),
                new EmptyBorder(16, 16, 16, 16)
        ));
        card.setBackground(Color.WHITE);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, 0, 10, 0);

        // View details toggle (link-like)
        detailsToggle.setBorderPainted(false);
        detailsToggle.setContentAreaFilled(false);
        detailsToggle.setFocusPainted(false);
        detailsToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        detailsToggle.setHorizontalAlignment(SwingConstants.LEFT);

        c.gridy = 0;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(detailsToggle, c);

        // Details panel (collapsible)
        detailsPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(230, 230, 230)),
                new EmptyBorder(10, 12, 10, 12)
        ));
        detailsPanel.setBackground(new Color(250, 250, 250));

        detailsText.setEditable(false);
        detailsText.setOpaque(false);
        detailsText.setLineWrap(true);
        detailsText.setWrapStyleWord(true);
        detailsText.setFont(UIManager.getFont("Label.font"));
        detailsText.setText("Create a Deterministic Finite State Automaton that accepts strings that contain any number of b's and at least one a, in any order.");

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
            detailsPanel.setVisible(show);
            detailsToggle.setText(show ? "Hide details ▾" : "View details ▸");

            // Important for layout recalculation in Swing
            card.revalidate();
            card.repaint();
        });
    }
}
