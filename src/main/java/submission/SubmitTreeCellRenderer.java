package submission;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Custom tree cell renderer with icons for different node types (courses, assignments, problems).
 */
public class SubmitTreeCellRenderer extends DefaultTreeCellRenderer {
    private final Icon courseIcon;
    private final Icon assignmentIcon;
    private final Icon problemIcon;
    private final Icon problemSolvedIcon;

    public SubmitTreeCellRenderer() {
        // Create simple colored icons for each type
        courseIcon = createColoredIcon(new Color(70, 130, 180), "C");      // Steel blue
        assignmentIcon = createColoredIcon(new Color(255, 140, 0), "A");   // Dark orange
        problemIcon = createColoredIcon(new Color(100, 149, 237), "P");    // Cornflower blue
        problemSolvedIcon = createColoredIcon(new Color(34, 139, 34), "✓"); // Forest green with checkmark
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                   boolean sel, boolean expanded,
                                                   boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        if (value instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObject = node.getUserObject();

            if (userObject instanceof CourseItem) {
                setIcon(courseIcon);
            } else if (userObject instanceof AssignmentItem) {
                setIcon(assignmentIcon);
            } else if (userObject instanceof ProblemItem) {
                ProblemItem problem = (ProblemItem) userObject;
                setIcon(problem.solved ? problemSolvedIcon : problemIcon);
            }
            // For root and placeholder nodes, use default icons
        }

        return this;
    }

    /**
     * Creates a simple colored circle icon with a letter.
     */
    private Icon createColoredIcon(final Color color, final String letter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw filled circle
                g2d.setColor(color);
                g2d.fillOval(x, y, 16, 16);

                // Draw border
                g2d.setColor(color.darker());
                g2d.drawOval(x, y, 16, 16);

                // Draw letter
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
                FontMetrics fm = g2d.getFontMetrics();
                int textX = x + (16 - fm.stringWidth(letter)) / 2;
                int textY = y + ((16 - fm.getHeight()) / 2) + fm.getAscent();
                g2d.drawString(letter, textX, textY);

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
}

