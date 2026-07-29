package submission;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Custom tree cell renderer with icons for different node types (courses, assignments, problems).
 */
public class ExtendedSubmitTreeCellRenderer extends DefaultTreeCellRenderer {
    private final Icon courseIcon;
    private final Icon assignmentIcon;
    private final Icon problemIcon;
    private final Icon problemSolvedIcon;

    private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
//    private final JLabel mainLabel = new JLabel();
    public final PillRenderer subLabel = new PillRenderer();

    public ExtendedSubmitTreeCellRenderer() {
        // Create simple colored icons for each type
        courseIcon = createColoredIcon(new Color(70, 130, 180), "C");      // Steel blue
        assignmentIcon = createColoredIcon(new Color(255, 140, 0), "A");   // Dark orange
        problemIcon = createColoredIcon(new Color(142, 68, 173), "P");     // Purple (distinct from the blue course icon)
        problemSolvedIcon = createColoredIcon(new Color(34, 139, 34), "✓"); // Forest green with checkmark

        panel.setOpaque(false);
        panel.add(this);
        panel.add(subLabel);
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
                // Flag an empty assignment right in the tree, so it is clear without expanding.
                if (((AssignmentItem) userObject).problemCount == 0) {
                    setText(getText() + "  (no problems)");
                }
            } else if (userObject instanceof ProblemItem) {
                ProblemItem problem = (ProblemItem) userObject;
                setIcon(problem.solved ? problemSolvedIcon : problemIcon);
                subLabel.setText(problem.status);
                subLabel.updateStatusPill(subLabel, problem.status);
            } else {
                // Placeholder / loading / "no items" nodes are informational text, not
                // items. Show no icon rather than Swing's default document/folder icon,
                // which otherwise clashes with the course/assignment/problem circles while
                // data is loading.
                setIcon(null);
                subLabel.setText("");
            }
        }

        return panel;
    }

    /**
     * Creates a simple colored circle icon with a letter.
     */
    private Icon createColoredIcon(final Color color, final String letter) {
        final int size = 16;
        final Font font = new Font("SansSerif", Font.BOLD, 11);

        // Precompute a draw origin that lands the glyph's visual centre of mass
        // (centroid) at the icon centre. Bounding-box centring leaves open letters
        // like "C" (solid on the left, open on the right) looking shifted; centring the
        // centroid instead looks right for every shape. Computed once per icon.
        final TextLayout glyph;
        final float originX;
        final float originY;
        {
            BufferedImage probe = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            glyph = new TextLayout(letter, font, pg.getFontRenderContext());
            Rectangle2D ink = glyph.getBounds();
            // Start from a bounding-box-centred origin, render it, then measure the residual.
            float baseX = (size - (float) ink.getWidth()) / 2f - (float) ink.getX();
            float baseY = (size - (float) ink.getHeight()) / 2f - (float) ink.getY();
            pg.setColor(Color.WHITE);
            glyph.draw(pg, baseX, baseY);
            pg.dispose();

            double sx = 0, sy = 0, sw = 0;
            for (int yy = 0; yy < size; yy++) {
                for (int xx = 0; xx < size; xx++) {
                    int a = (probe.getRGB(xx, yy) >>> 24) & 0xff;
                    if (a > 0) {
                        sx += (xx + 0.5) * a;
                        sy += (yy + 0.5) * a;
                        sw += a;
                    }
                }
            }
            if (sw > 0) {
                originX = baseX + (float) (size / 2.0 - sx / sw);
                originY = baseY + (float) (size / 2.0 - sy / sw);
            } else {
                originX = baseX;
                originY = baseY;
            }
        }

        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw filled circle
                g2d.setColor(color);
                g2d.fillOval(x, y, size, size);

                // Draw border
                g2d.setColor(color.darker());
                g2d.drawOval(x, y, size, size);

                // Draw the letter at the precomputed, centroid-centred origin.
                g2d.setColor(Color.WHITE);
                glyph.draw(g2d, x + originX, y + originY);

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
}

