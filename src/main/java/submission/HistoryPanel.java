package submission;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static gui.Globals.boldFont;

/**
 * The Submission History card: the table (with the group-member column when the
 * problem is group work), the status line above it, and all the sizing rules.
 * The window fetches; this panel presents.
 */
class HistoryPanel extends CardPanel {

    private final DefaultTableModel model;
    private final JTable table;
    private final JScrollPane scrollPane;
    private final JLabel statusLabel;

    private List<HistoryColumn> columns = HistoryColumn.forGroup(false);

    HistoryPanel() {
        super(new BorderLayout(0, 4));
        // Same inner margin as the assignment/problem panels (which inset their content 8,10,8,10).
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        model = new DefaultTableModel(HistoryColumn.titles(columns), 0) {
            @Override
            public boolean isCellEditable(int r, int col) { return false; }
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(28);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setBackground(Theme.CARD_BG);
        table.setSelectionBackground(Theme.SELECTION_BG);
        table.setSelectionForeground(Theme.TEXT_DARK);
        table.setGridColor(Theme.CARD_BORDER);
        // Mockup look: flat light header, roomier rows, light column/row separators.
        table.setShowVerticalLines(true);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setDefaultRenderer(Object.class, new HistoryCellRenderer());
        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeRowsToFit();
            }
        });
        applyColumnWidths();

        JTableHeader header = table.getTableHeader();
        header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
        // Flat header: plain label with a light background and thin separator lines.
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, false, false, row, column);
                setHorizontalAlignment(CENTER);
                setFont(table.getTableHeader().getFont());
                setBackground(new Color(0xF0, 0xF2, 0xF5));
                setForeground(Theme.TEXT_MUTED);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, Theme.CARD_BORDER),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
                return this;
            }
        });

        statusLabel = new JLabel("Select a problem to view its submission history.");
        // Indent/space to line up with the placeholder text in the cards above.
        statusLabel.setBorder(BorderFactory.createEmptyBorder(12, 8, 4, 0));
        // Match the italic gray placeholder text used in the assignment/problem cards.
        statusLabel.setForeground(new Color(0x88, 0x88, 0x88));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 14f));

        scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(280, 120));
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        scrollPane.setVisible(false); // shown once there are submissions

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        north.add(sectionLabel("Submission History"), BorderLayout.NORTH);
        north.add(statusLabel, BorderLayout.CENTER);

        add(north, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the status line and hides the table (used for loading, empty and error
     * states). Placeholder/info messages use the italic gray style of the other
     * cards' placeholders; emphasized text is dark and plain.
     */
    void showMessage(String text, boolean emphasize) {
        statusLabel.setText(text);
        if (emphasize) {
            statusLabel.setForeground(Theme.TEXT_DARK);
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 14f));
        } else {
            statusLabel.setForeground(new Color(0x88, 0x88, 0x88));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 14f));
        }
    }

    /** Clears the rows and hides the table, keeping the given status message. */
    void clear(String message) {
        model.setRowCount(0);
        table.setRowHeight(28); // reset per-row heights from wrapped text
        setTableVisible(false);
        showMessage(message, false);
    }

    /** Fills the table from the API rows (newest first) and updates the status line. */
    void populate(List<ApiModels.Submission> subs, boolean group, Function<Instant, String> formatWhen) {
        model.setRowCount(0);
        table.setRowHeight(28); // reset per-row heights from wrapped text
        columns = HistoryColumn.forGroup(group);
        model.setColumnIdentifiers(HistoryColumn.titles(columns));
        applyColumnWidths(); // columns are rebuilt above, so re-apply widths

        if (subs.isEmpty()) {
            showMessage("No submissions yet.", false);
            setTableVisible(false);
            autoSizeColumns();
            return;
        }
        for (ApiModels.Submission s : subs) {
            model.addRow(ApiTree.historyRow(s, group, formatWhen));
        }
        int n = subs.size();
        showMessage(n + (n == 1 ? " submission" : " submissions"), true);
        setTableVisible(true);
        autoSizeColumns();
        // Measure after the columns settle at their real widths.
        SwingUtilities.invokeLater(this::resizeRowsToFit);
    }

    private void setTableVisible(boolean visible) {
        if (scrollPane.isVisible() != visible) {
            scrollPane.setVisible(visible);
            revalidate();
            repaint();
        }
    }

    private HistoryColumn columnAt(int column) {
        return column >= 0 && column < columns.size() ? columns.get(column) : HistoryColumn.FEEDBACK;
    }

    /** Weights the columns; AUTO_RESIZE_ALL_COLUMNS scales the widths to fit. */
    private void applyColumnWidths() {
        javax.swing.table.TableColumnModel cols = table.getColumnModel();
        for (int i = 0; i < cols.getColumnCount() && i < columns.size(); i++) {
            cols.getColumn(i).setPreferredWidth(columns.get(i).preferredWidth);
        }
    }

    /**
     * Sizes each column to fit its header and cell content (with a cap so one long
     * file name can't dominate). The last column (Feedback) absorbs the remaining width.
     */
    private void autoSizeColumns() {
        javax.swing.table.TableColumnModel cm = table.getColumnModel();
        int lastCol = cm.getColumnCount() - 1;
        for (int col = 0; col < cm.getColumnCount(); col++) {
            javax.swing.table.TableColumn tc = cm.getColumn(col);
            javax.swing.table.TableCellRenderer hr = table.getTableHeader().getDefaultRenderer();
            int width = hr.getTableCellRendererComponent(table, tc.getHeaderValue(), false, false, -1, col)
                    .getPreferredSize().width;
            for (int row = 0; row < table.getRowCount(); row++) {
                javax.swing.table.TableCellRenderer cr = table.getCellRenderer(row, col);
                width = Math.max(width, table.prepareRenderer(cr, row, col).getPreferredSize().width);
            }
            width += 14; // a little padding
            if (col == lastCol) {
                tc.setPreferredWidth(Math.max(width, 220)); // Feedback: roomy, then stretches
            } else {
                tc.setPreferredWidth(Math.min(width, 240)); // cap the fixed columns
            }
        }
    }

    /**
     * Fits each row to its tallest wrapped cell, measured once after the data or
     * the table width changes — never from inside the renderer while Swing paints,
     * which is a re-layout loop waiting to happen.
     */
    private void resizeRowsToFit() {
        JTextArea measure = new JTextArea();
        measure.setLineWrap(true);
        measure.setWrapStyleWord(true);
        measure.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        measure.setFont(table.getFont());
        for (int row = 0; row < table.getRowCount(); row++) {
            int desired = 28;
            for (int col = 0; col < table.getColumnCount(); col++) {
                if (!columnAt(col).wraps) continue;
                Object value = table.getValueAt(row, col);
                String text = value == null ? "" : value.toString().trim();
                if (text.isEmpty()) continue;
                int colWidth = table.getColumnModel().getColumn(col).getWidth();
                measure.setText(text);
                measure.setSize(Math.max(1, colWidth), Short.MAX_VALUE);
                desired = Math.max(desired, measure.getPreferredSize().height);
            }
            if (table.getRowHeight(row) != desired) {
                table.setRowHeight(row, desired);
            }
        }
    }

    /**
     * Cosmetic renderer: padded wrapped cells, and the "Status" column drawn as a
     * colored rounded pill (amber PENDING etc.), like the mockup. Display only:
     * row heights are measured in resizeRowsToFit, never here.
     */
    private class HistoryCellRenderer extends DefaultTableCellRenderer {
        private boolean pill;
        private Color pillBg;
        private Color pillFg;
        private final JTextArea wrapArea = new JTextArea();

        HistoryCellRenderer() {
            wrapArea.setLineWrap(true);
            wrapArea.setWrapStyleWord(true);
            wrapArea.setOpaque(true);
            wrapArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            HistoryColumn col = columnAt(column);
            String text = value == null ? "" : value.toString().trim();

            if (col.wraps) {
                wrapArea.setText(text);
                wrapArea.setFont(table.getFont());
                if (isSelected) {
                    wrapArea.setBackground(table.getSelectionBackground());
                    wrapArea.setForeground(table.getSelectionForeground());
                } else {
                    wrapArea.setBackground(Theme.CARD_BG);
                    wrapArea.setForeground(Theme.TEXT_DARK);
                }
                // Size to the column so the wrap point matches what was measured.
                int colWidth = table.getColumnModel().getColumn(column).getWidth();
                wrapArea.setSize(Math.max(1, colWidth), Short.MAX_VALUE);

                if (col == HistoryColumn.RESULT && !text.isEmpty()) {
                    boldFont(wrapArea);
                    if (text.equals("Correct")) {
                        wrapArea.setForeground(Theme.SUCCESS_TEXT);
                    } else if (text.equals("Incorrect")) {
                        wrapArea.setForeground(Theme.DANGER_TEXT);
                    }
                }

                return wrapArea;
            }

            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            // Keep single-line cells vertically top-aligned like the wrapped feedback text.
            setVerticalAlignment(TOP);
            if (!isSelected) {
                setBackground(Theme.CARD_BG);
                setForeground(Theme.TEXT_DARK);
            }

            pill = false;
            if (col == HistoryColumn.STATUS && !text.isEmpty()) {
                pill = true;
                String s = text.toLowerCase(java.util.Locale.ROOT);
                if (s.contains("pend") || s.contains("queue") || s.contains("run")) {
                    pillBg = new Color(0xFE, 0xF3, 0xC7); pillFg = new Color(0xB4, 0x53, 0x09); // amber
                } else if (s.contains("grade") || s.contains("accept") || s.contains("pass")
                        || s.contains("success") || s.contains("complete") || s.contains("solve")) {
                    pillBg = new Color(0xDC, 0xFC, 0xE7); pillFg = Theme.SUCCESS_TEXT; // green
                } else if (s.contains("fail") || s.contains("error") || s.contains("reject")) {
                    pillBg = new Color(0xFE, 0xE2, 0xE2); pillFg = Theme.DANGER_TEXT; // red
                } else {
                    pillBg = new Color(0xE5, 0xE7, 0xEB); pillFg = Theme.TEXT_MUTED; // neutral gray
                }
                setForeground(pillFg);
                setFont(getFont().deriveFont(Font.BOLD, 11f));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (pill) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Clear cell background first.
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Pill behind the text.
                FontMetrics fm = g2.getFontMetrics(getFont());
                int textW = fm.stringWidth(getText());
                int pillH = fm.getHeight() + 4;
                int pillW = textW + 16;
                // Top-aligned so it matches cells in rows made taller by wrapped feedback.
                int y = 3;
                g2.setColor(pillBg);
                g2.fillRoundRect(2, y, pillW, pillH, pillH, pillH);
                g2.setColor(pillFg);
                g2.drawString(getText(), 10, y + 2 + fm.getAscent());
                g2.dispose();
            } else {
                super.paintComponent(g);
            }
        }
    }
}
