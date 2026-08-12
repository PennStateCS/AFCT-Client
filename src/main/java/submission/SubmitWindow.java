package submission;

import file.EncodeException;
import file.XMLCodec;
import gui.Globals;
import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.Universe;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.table.JTableHeader;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static gui.Globals.*;
import static submission.SessionHandler.CANT_CONNECT_TO_SERVER_MESSAGE;

public class SubmitWindow extends JFrame implements SubmissionGUI {

    // ===============================
    // Palette (cosmetic only)
    // ===============================
    // Same default LAF panel gray the login window uses.
    private static final Color BG          = UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background") : new Color(0xF5, 0xF6, 0xF8);
    private static final Color CARD_BG     = Color.WHITE;                 // card background
    private static final Color CARD_BORDER = new Color(0xE2, 0xE5, 0xEA); // subtle card outline
    private static final Color ACCENT      = new Color(0x42, 0x63, 0xEB); // primary blue
    private static final Color TEXT_DARK   = new Color(0x1F, 0x29, 0x37);
    private static final Color TEXT_MUTED  = new Color(0x6B, 0x72, 0x80);
    private static final Color SELECTION_BG = new Color(0xE7, 0xF0, 0xFE); // light-blue row highlight

    private final Environment environment;

    private static final String baseTitle = "AFCT Submission Center";

    // ===============================
    // UI
    // ===============================

    private JButton refreshBtn;
    private JButton logoutBtn;
    private JButton submitBtn;

    private JTextField fileTF;
    private JButton browseBtn;
    private JButton useOpenFileBtn;
    private JLabel statusLabel;

    // Assignment details display
    private JTextPane assignmentDetailsPane;
    private JScrollPane assignmentDetailsScroll;

    // Problem details display
    private JTextPane problemDetailsPane;
    private JScrollPane problemDetailsScroll;

    // Submission history (for the selected problem)
    private javax.swing.table.DefaultTableModel submissionHistoryModel;
    private JTable submissionHistoryTable;
    private final HistoryCellRenderer historyCellRenderer = new HistoryCellRenderer();
    private JScrollPane historyScrollPane; // hidden while there are no submissions
    private JLabel submissionHistoryStatus; // "No submissions", "Loading…", or an error
    private java.util.concurrent.atomic.AtomicInteger historyRequestSeq =
            new java.util.concurrent.atomic.AtomicInteger();

    // Tree
    private JTree selectionTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    // Filter radio buttons
    private JRadioButton allAssignmentsRadio;
    private JRadioButton upcomingAssignmentsRadio;
    private JRadioButton allProblemsRadio;
    private JRadioButton unsolvedProblemsRadio;

    // ===============================
    // State
    // ===============================
    private volatile boolean loading = false;
    private File selectedFile = null;
    // True once the user has browsed to a file, so the display stops auto-following the
    // editor's open file. "Use open file" clears it to snap back to the open document.
    private boolean fileManuallyChosen = false;

    // Refresh cooldown
    private long lastRefreshMs = 0;
    private static final int REFRESH_COOLDOWN_MS = 10_000;
    private Timer refreshCooldownTimer;

    // Applies the large default window size once, on the first show (see applyDefaultSize).
    private boolean defaultSizeApplied = false;

    // Logging — writes to <project>/logs/submissions-YYYY-MM-DD.log
    private static final DateTimeFormatter LOG_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Path LOG_DIR = Paths.get(System.getProperty("user.dir"), "logs");

    // Selected items derived from tree selection
    private CourseItem selectedCourse = null;
    private AssignmentItem selectedAssignment = null;
    private ProblemItem selectedProblem = null;
    private DefaultMutableTreeNode selectedNode = null;

    // Selection to restore after a Refresh: captured before the reload, then re-applied
    // level by level as each lazily-loaded tree level (course, assignment, problem) arrives.
    private String restoreCourseId;
    private String restoreAssignmentId;
    private String restoreProblemId;

    // Every expanded branch to restore after a Refresh (not just the selected path), so
    // the whole tree comes back the way the user left it. Because the tree loads lazily
    // and only one load runs at a time (the `loading` flag), the branches are re-expanded
    // one at a time: pumpRestore expands the next branch that still needs loading, and the
    // load's done() pumps again, until nothing is left and the selection is re-applied.
    private final java.util.Set<String> restoreExpandedCourseIds = new java.util.HashSet<>();
    private final java.util.Set<String> restoreExpandedAssignmentIds = new java.util.HashSet<>();
    private boolean restoreInProgress = false;

    // The whole course tree, fetched once (GET /api/client/v1/tree) and cached so the tree
    // builds and re-filters locally without more network calls. The lazy expanders read
    // their children from these maps; a filter toggle rebuilds the tree from them instantly.
    private java.util.List<Map<String, Object>> treeCourseList = new java.util.ArrayList<>();
    private final Map<String, List<Map<String, Object>>> treeAssignmentsByCourse = new java.util.HashMap<>();
    private final Map<String, List<Map<String, Object>>> treeProblemsByAssignment = new java.util.HashMap<>();

    public SubmitWindow(Environment environment) {
        super(baseTitle);
        this.environment = environment;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 560));

        buildUI();
        wireEvents();

        // Listen for file changes in the environment to update filename display
        environment.addFileChangeListener(e -> updateCurrentFileDisplay());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                Universe.unregisterSubmitDialog(environment);
            }
        });

        refreshDialog();
    }

    // ============================================================
    // UI
    // ============================================================

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.setBackground(BG);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 10));
        header.setOpaque(false);

        JLabel title = new JLabel("AFCT Submission Center");
        Globals.boldFontAndChangeSize(title, 18);
        title.setForeground(TEXT_DARK);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        refreshBtn = new JButton("Refresh");
        logoutBtn = new JButton("Logout");

        stylePrimaryButton(refreshBtn);
        stylePrimaryButton(logoutBtn);

        Globals.setPointerCursor(refreshBtn);
        Globals.setPointerCursor(logoutBtn);

        actions.add(refreshBtn);
        actions.add(logoutBtn);

        header.add(title, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JComponent buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.35);
        split.setBorder(null);
        split.setOpaque(false);
        split.setBackground(BG);
        // Hide the draggable divider so the panes sit side by side like the mockup.
        split.setDividerSize(0);
        split.setEnabled(false);

        split.setLeftComponent(buildTreePanel());
        JComponent details = (JComponent) buildDetailsPanel();
        // Gap where the divider used to be.
        details.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 0), details.getBorder()));
        split.setRightComponent(details);

        return split;
    }

    private JComponent buildTreePanel() {
        JPanel left = new CardPanel(new BorderLayout(10, 10));
        left.setBorder(cardBorder());

        // Filter panel at top
        JPanel filterPanel = new JPanel();
        filterPanel.setOpaque(false);
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 5));

        JPanel filterHeaderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        filterHeaderRow.setOpaque(false);
        filterHeaderRow.add(sectionLabel("Filters"));
        filterPanel.add(filterHeaderRow);

        // Assignment / Problem filters in a shared grid so the columns line up.
        JPanel filterGrid = new JPanel(new GridBagLayout());
        filterGrid.setOpaque(false);
        GridBagConstraints fc = new GridBagConstraints();
        fc.anchor = GridBagConstraints.WEST;
        fc.insets = new Insets(2, 5, 2, 5);

        JLabel assignmentLabel = new JLabel("Assignments:");
        Globals.boldFont(assignmentLabel);
        assignmentLabel.setForeground(TEXT_DARK);

        allAssignmentsRadio = new JRadioButton("All", true);
        upcomingAssignmentsRadio = new JRadioButton("Upcoming");

        ButtonGroup assignmentGroup = new ButtonGroup();
        assignmentGroup.add(allAssignmentsRadio);
        assignmentGroup.add(upcomingAssignmentsRadio);

        allAssignmentsRadio.setFocusPainted(false);
        upcomingAssignmentsRadio.setFocusPainted(false);
        allAssignmentsRadio.setOpaque(false);
        upcomingAssignmentsRadio.setOpaque(false);

        fc.gridy = 0;
        fc.gridx = 0;
        filterGrid.add(assignmentLabel, fc);
        fc.gridx = 1;
        filterGrid.add(allAssignmentsRadio, fc);
        fc.gridx = 2;
        filterGrid.add(upcomingAssignmentsRadio, fc);

        JLabel problemLabel = new JLabel("Problems:");
        Globals.boldFont(problemLabel);
        problemLabel.setForeground(TEXT_DARK);

        allProblemsRadio = new JRadioButton("All", true);
        unsolvedProblemsRadio = new JRadioButton("Unsolved");

        ButtonGroup problemGroup = new ButtonGroup();
        problemGroup.add(allProblemsRadio);
        problemGroup.add(unsolvedProblemsRadio);

        allProblemsRadio.setFocusPainted(false);
        unsolvedProblemsRadio.setFocusPainted(false);
        allProblemsRadio.setOpaque(false);
        unsolvedProblemsRadio.setOpaque(false);

        fc.gridy = 1;
        fc.gridx = 0;
        filterGrid.add(problemLabel, fc);
        fc.gridx = 1;
        filterGrid.add(allProblemsRadio, fc);
        fc.gridx = 2;
        filterGrid.add(unsolvedProblemsRadio, fc);

        // Left-align the grid within the vertical BoxLayout.
        JPanel filterGridRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filterGridRow.setOpaque(false);
        filterGridRow.add(filterGrid);

        filterPanel.add(filterGridRow);

        // Tree
        rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);

        selectionTree = new JTree(treeModel);
        selectionTree.setRootVisible(false); // Hide root so courses appear at top level
        selectionTree.setShowsRootHandles(true);
        selectionTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // A slightly nicer default row height (optional)
        selectionTree.setRowHeight(22);
        selectionTree.setBackground(CARD_BG);

        // Light-blue selection highlight to match the mockup
        UIManager.put("Tree.selectionBackground", SELECTION_BG);
        UIManager.put("Tree.selectionForeground", TEXT_DARK);
        UIManager.put("Tree.selectionBorderColor", SELECTION_BG);

        // Custom renderer with icons
        selectionTree.setCellRenderer(new SubmitTreeCellRenderer());

        // Use +/- symbols for expand/collapse
        UIManager.put("Tree.expandedIcon", createPlusMinusIcon(true));
        UIManager.put("Tree.collapsedIcon", createPlusMinusIcon(false));
        selectionTree.updateUI();

        JScrollPane sp = new JScrollPane(selectionTree);
        sp.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        sp.getViewport().setBackground(CARD_BG);

        left.add(filterPanel, BorderLayout.NORTH);
        left.add(sp, BorderLayout.CENTER);

        return left;
    }

    private JComponent buildDetailsPanel() {
        // Main container with vertical layout
        JPanel container = new JPanel(new GridBagLayout());
        container.setOpaque(false);
        GridBagConstraints containerConstraints = new GridBagConstraints();
        containerConstraints.gridx = 0;
        containerConstraints.weightx = 1;
        containerConstraints.fill = GridBagConstraints.BOTH;
        containerConstraints.insets = new Insets(0, 0, 8, 0);

        // ============================================================
        // Panel 1: Selected Assignment
        // ============================================================
        JPanel assignmentPanel = new CardPanel(new GridBagLayout());
        assignmentPanel.setBorder(cardBorder());

        GridBagConstraints c1 = new GridBagConstraints();
        c1.gridx = 0;
        c1.gridy = 0;
        c1.weightx = 1;
        c1.fill = GridBagConstraints.HORIZONTAL;
        c1.anchor = GridBagConstraints.WEST;
        c1.insets = new Insets(8, 10, 0, 10);
        assignmentPanel.add(sectionLabel("Selected Assignment"), c1);

        c1.gridy = 1;
        c1.weighty = 1;
        c1.fill = GridBagConstraints.BOTH;
        c1.insets = new Insets(4, 10, 8, 10);

        // Assignment details section
        assignmentDetailsPane = new JTextPane();
        assignmentDetailsPane.setEditable(false);
        assignmentDetailsPane.setContentType("text/html");
        assignmentDetailsPane.setBackground(assignmentPanel.getBackground());
        assignmentDetailsPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        assignmentDetailsPane.setText("<html><body style='font-family: sans-serif; padding: 4px; color: #888;'>" +
                "<i>Select an assignment to view details</i></body></html>");

        assignmentDetailsScroll = new JScrollPane(assignmentDetailsPane);
        assignmentDetailsScroll.setPreferredSize(new Dimension(280, 100));
        // No inner frame: the panel's titled border is the only box we want.
        assignmentDetailsScroll.setBorder(BorderFactory.createEmptyBorder());
        assignmentDetailsScroll.setViewportBorder(null);
        assignmentDetailsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        assignmentDetailsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        assignmentPanel.add(assignmentDetailsScroll, c1);

        // Add assignment panel to container
        // weighty 0: size to the panel's own content (its details pane fits its text),
        // with the history panel below taking the leftover vertical space.
        containerConstraints.gridy = 0;
        containerConstraints.weighty = 0;
        container.add(assignmentPanel, containerConstraints);

        // ============================================================
        // Panel 2: Selected Problem
        // ============================================================
        JPanel problemPanel = new CardPanel(new GridBagLayout());
        problemPanel.setBorder(cardBorder());

        GridBagConstraints c2 = new GridBagConstraints();
        c2.gridx = 0;
        c2.gridy = 0;
        c2.weightx = 1;
        c2.fill = GridBagConstraints.HORIZONTAL;
        c2.anchor = GridBagConstraints.WEST;
        c2.insets = new Insets(8, 10, 0, 10);
        problemPanel.add(sectionLabel("Selected Problem"), c2);

        c2.gridy = 1;
        c2.weighty = 1;
        c2.fill = GridBagConstraints.BOTH;
        c2.insets = new Insets(4, 10, 8, 10);

        // Problem details section
        problemDetailsPane = new JTextPane();
        problemDetailsPane.setEditable(false);
        problemDetailsPane.setContentType("text/html");
        problemDetailsPane.setBackground(problemPanel.getBackground());
        problemDetailsPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        problemDetailsPane.setText("<html><body style='font-family: sans-serif; padding: 4px; color: #888;'>" +
                "<i>Select a problem to view details</i></body></html>");

        problemDetailsScroll = new JScrollPane(problemDetailsPane);
        problemDetailsScroll.setPreferredSize(new Dimension(280, 100));
        // No inner frame: the panel's titled border is the only box we want.
        problemDetailsScroll.setBorder(BorderFactory.createEmptyBorder());
        problemDetailsScroll.setViewportBorder(null);
        problemDetailsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        problemDetailsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        problemPanel.add(problemDetailsScroll, c2);

        // Add problem panel to container
        containerConstraints.gridy = 1;
        containerConstraints.weighty = 0;
        container.add(problemPanel, containerConstraints);

        // ============================================================
        // Panel 2.5: Submission History (for the selected problem)
        // ============================================================
        JPanel historyPanel = new CardPanel(new BorderLayout(0, 4));
        // Same inner margin as the assignment/problem panels (which inset their content 8,10,8,10).
        historyPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        submissionHistoryModel = new javax.swing.table.DefaultTableModel(
                new Object[]{"Submitted", "File", "Status", "Result", "Feedback"}, 0) {
            @Override
            public boolean isCellEditable(int r, int col) { return false; }
        };
        submissionHistoryTable = new JTable(submissionHistoryModel);
        submissionHistoryTable.setFillsViewportHeight(true);
        submissionHistoryTable.getTableHeader().setReorderingAllowed(false);
        submissionHistoryTable.setRowHeight(28);
        submissionHistoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        submissionHistoryTable.setBackground(CARD_BG);
        submissionHistoryTable.setSelectionBackground(SELECTION_BG);
        submissionHistoryTable.setSelectionForeground(TEXT_DARK);
        submissionHistoryTable.setGridColor(CARD_BORDER);
        // Mockup look: flat light header, roomier rows, light column/row separators.
        submissionHistoryTable.setShowVerticalLines(true);
        submissionHistoryTable.setShowHorizontalLines(true);
        submissionHistoryTable.setIntercellSpacing(new Dimension(1, 1));
        submissionHistoryTable.setDefaultRenderer(Object.class, historyCellRenderer);
        applyHistoryColumnWidths();
        JTableHeader historyHeader = submissionHistoryTable.getTableHeader();
        historyHeader.setFont(historyHeader.getFont().deriveFont(Font.BOLD, 11f));
        // Flat header: plain label with a light background and thin separator lines.
        historyHeader.setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, false, false, row, column);
                setHorizontalAlignment(CENTER);
                setFont(table.getTableHeader().getFont());
                setBackground(new Color(0xF0, 0xF2, 0xF5));
                setForeground(TEXT_MUTED);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, CARD_BORDER),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
                return this;
            }
        });

        submissionHistoryStatus = new JLabel("Select a problem to view its submission history.");
        // Indent/space to line up with the placeholder text in the cards above.
        submissionHistoryStatus.setBorder(BorderFactory.createEmptyBorder(12, 8, 4, 0));
        // Match the italic gray placeholder text used in the assignment/problem cards.
        submissionHistoryStatus.setForeground(new Color(0x88, 0x88, 0x88));
        submissionHistoryStatus.setFont(submissionHistoryStatus.getFont().deriveFont(Font.ITALIC, 14f));

        historyScrollPane = new JScrollPane(submissionHistoryTable);
        historyScrollPane.setPreferredSize(new Dimension(280, 120));
        historyScrollPane.setBorder(BorderFactory.createLineBorder(CARD_BORDER));
        historyScrollPane.getViewport().setBackground(CARD_BG);
        historyScrollPane.setVisible(false); // shown once there are submissions

        JPanel historyNorth = new JPanel(new BorderLayout(0, 4));
        historyNorth.setOpaque(false);
        historyNorth.add(sectionLabel("Submission History"), BorderLayout.NORTH);
        historyNorth.add(submissionHistoryStatus, BorderLayout.CENTER);

        historyPanel.add(historyNorth, BorderLayout.NORTH);
        historyPanel.add(historyScrollPane, BorderLayout.CENTER);

        // History takes the leftover vertical space so its table can grow.
        containerConstraints.gridy = 2;
        containerConstraints.weighty = 1.0;
        container.add(historyPanel, containerConstraints);

        // ============================================================
        // Panel 3: Submission (Current File + Submit Button)
        // ============================================================
        JPanel submissionPanel = new CardPanel(new GridBagLayout());
        submissionPanel.setBorder(cardBorder());

        GridBagConstraints c3 = new GridBagConstraints();
        c3.gridx = 0;
        c3.weightx = 1;
        c3.fill = GridBagConstraints.HORIZONTAL;
        c3.anchor = GridBagConstraints.WEST;
        c3.insets = new Insets(8, 10, 0, 10);

        c3.gridy = 0;
        submissionPanel.add(sectionLabel("Submission"), c3);
        c3.insets = new Insets(4, 10, 0, 10);

        // Current file display. Defaults to the file open in the editor; the buttons
        // beside it let the user browse to a different file or snap back to the open one.
        fileTF = new JTextField();
        fileTF.setEditable(false);
        fileTF.setMargin(new Insets(6, 10, 6, 10));
        fileTF.setForeground(TEXT_DARK);
        fileTF.setBackground(new Color(0xFA, 0xFB, 0xFC));
        fileTF.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        fileTF.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileTF.setToolTipText("The file that will be submitted. Click to browse for another.");
        fileTF.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                browseForFile();
            }
        });

        useOpenFileBtn = new JButton("Use open file");
        useOpenFileBtn.setToolTipText("Submit the file currently open in the editor");
        styleTintedButton(useOpenFileBtn);
        Globals.setPointerCursor(useOpenFileBtn);
        useOpenFileBtn.addActionListener(e -> useOpenFile());

        browseBtn = new JButton("Browse…");
        browseBtn.setToolTipText("Choose a different file to submit");
        styleTintedButton(browseBtn);
        Globals.setPointerCursor(browseBtn);
        browseBtn.addActionListener(e -> browseForFile());

        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fileButtons.setOpaque(false);
        fileButtons.add(useOpenFileBtn);
        fileButtons.add(browseBtn);

        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.setOpaque(false);
        fileRow.add(fileTF, BorderLayout.CENTER);
        fileRow.add(fileButtons, BorderLayout.EAST);

        // Seed the display from the editor's open file (default source).
        updateCurrentFileDisplay();

        c3.gridy = 1;
        submissionPanel.add(labeled("File to Submit", fileRow), c3);

        // Submit button — full width
        submitBtn = new JButton("Submit");
        submitBtn.setPreferredSize(new Dimension(0, 38));
        stylePrimaryButton(submitBtn);
        Globals.setPointerCursor(submitBtn);

        c3.gridy++;
        c3.insets = new Insets(16, 10, 10, 10);
        submissionPanel.add(submitBtn, c3);

        // Spacer to push content to top
        c3.gridy++;
        c3.weighty = 1;
        c3.fill = GridBagConstraints.BOTH;
        c3.insets = new Insets(0, 0, 0, 0);
        submissionPanel.add(Box.createVerticalStrut(1), c3);

        // Add submission panel to container
        containerConstraints.gridy = 3;
        containerConstraints.weighty = 0;
        container.add(submissionPanel, containerConstraints);

        return container;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        statusLabel = new JLabel("<html>&nbsp;</html>");
        statusLabel.setBorder(new EmptyBorder(8, 2, 2, 2));
        Globals.changeSize(statusLabel, 13);
        footer.add(statusLabel, BorderLayout.CENTER);
        return footer;
    }

    private JComponent labeled(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        Globals.boldFont(l);
        l.setForeground(TEXT_DARK);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    /** Subtle rounded-look card border with inner padding, matching the mockup cards. */
    private javax.swing.border.Border cardBorder() {
        return BorderFactory.createEmptyBorder(5, 5, 5, 5);
    }

    /** White card with rounded corners and a subtle outline, painted manually. */
    private static class CardPanel extends JPanel {
        private static final int ARC = 14;

        CardPanel(LayoutManager lm) {
            super(lm);
            setOpaque(false); // we paint the rounded background ourselves
            setBackground(CARD_BG);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(CARD_BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Small all-caps blue section heading, like "SELECTED ASSIGNMENT" in the mockup. */
    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setForeground(ACCENT);
        return l;
    }

    /** Solid blue primary action button. */
    private void stylePrimaryButton(JButton b) {
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setFont(b.getFont().deriveFont(Font.BOLD));
    }

    /** Light-blue tinted button that complements the solid primary blue. */
    private void styleTintedButton(JButton b) {
        b.setBackground(SELECTION_BG);
        b.setForeground(ACCENT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC7, 0xD7, 0xFB)),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
    }

    /**
     * Weights the history columns so Submitted/File get more room and Feedback a bit
     * less. Widths are proportional (AUTO_RESIZE_ALL_COLUMNS scales them to fit).
     */
    private void applyHistoryColumnWidths() {
        javax.swing.table.TableColumnModel cols = submissionHistoryTable.getColumnModel();
        for (int i = 0; i < cols.getColumnCount(); i++) {
            String name = String.valueOf(cols.getColumn(i).getHeaderValue());
            int w;
            switch (name) {
                case "Submitted":    w = 160; break;
                case "File":         w = 160; break;
                case "Group Member": w = 130; break;
                case "Status":       w = 100; break;
                case "Result":       w = 90;  break;
                default:             w = 240; break; // Feedback
            }
            cols.getColumn(i).setPreferredWidth(w);
        }
    }

    /**
     * Cosmetic renderer for the submission-history table: padded cells, and the
     * "Status" column drawn as a colored rounded pill (amber PENDING etc.), like the mockup.
     */
    private class HistoryCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        private boolean pill;
        private Color pillBg;
        private Color pillFg;
        // Wrapping renderer for the Feedback column so long messages fit.
        private final JTextArea wrapArea = new JTextArea();
        // Last measured height per cell (row -> column -> px), so a row can shrink
        // back down after columns settle at their real widths.
        private final java.util.Map<Integer, java.util.Map<Integer, Integer>> cellHeights =
                new java.util.HashMap<>();

        void clearHeights() {
            cellHeights.clear();
        }

        HistoryCellRenderer() {
            wrapArea.setLineWrap(true);
            wrapArea.setWrapStyleWord(true);
            wrapArea.setOpaque(true);
            wrapArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            String colName = table.getColumnName(column);
            String text = value == null ? "" : value.toString().trim();

            if (!"Status".equals(colName)) {
                // Wrap every text column so nothing gets cut off.
                wrapArea.setText(text);
                wrapArea.setFont(table.getFont());
                if (isSelected) {
                    wrapArea.setBackground(table.getSelectionBackground());
                    wrapArea.setForeground(table.getSelectionForeground());
                } else {
                    wrapArea.setBackground(CARD_BG);
                    wrapArea.setForeground(TEXT_DARK);
                }
                // Size to the column width, then fit the row to the tallest cell.
                // Cells re-measure whenever they repaint, so rows shrink back after
                // the columns settle at their real widths.
                int colWidth = table.getColumnModel().getColumn(column).getWidth();
                wrapArea.setSize(Math.max(1, colWidth), Short.MAX_VALUE);
                int needed = Math.max(28, wrapArea.getPreferredSize().height);
                java.util.Map<Integer, Integer> rowMap =
                        cellHeights.computeIfAbsent(row, k -> new java.util.HashMap<>());
                rowMap.put(column, needed);
                int desired = 28;
                for (int h : rowMap.values()) {
                    desired = Math.max(desired, h);
                }
                if (table.getRowHeight(row) != desired) {
                    table.setRowHeight(row, desired);
                }

                if ("Result".equals(colName) && !text.isEmpty()) {
                    boldFont(wrapArea);
                    if (text.equals("Correct")) {
                        wrapArea.setForeground(new Color(0x15, 0x80, 0x3D));
                    } else if (text.equals("Incorrect")) {
                        wrapArea.setForeground(new Color(0xB9, 0x1C, 0x1C));
                    }
                }

                return wrapArea;
            }

            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            // Keep single-line cells vertically top-aligned like the wrapped feedback text.
            setVerticalAlignment(TOP);
            if (!isSelected) {
                setBackground(CARD_BG);
                setForeground(TEXT_DARK);
            }

            pill = false;
            if ("Status".equals(colName) && !text.isEmpty()) {
                pill = true;
                String s = text.toLowerCase(java.util.Locale.ROOT);
                if (s.contains("pend") || s.contains("queue") || s.contains("run")) {
                    pillBg = new Color(0xFE, 0xF3, 0xC7); pillFg = new Color(0xB4, 0x53, 0x09); // amber
                } else if (s.contains("grade") || s.contains("accept") || s.contains("pass")
                        || s.contains("success") || s.contains("complete") || s.contains("solve")) {
                    pillBg = new Color(0xDC, 0xFC, 0xE7); pillFg = new Color(0x15, 0x80, 0x3D); // green
                } else if (s.contains("fail") || s.contains("error") || s.contains("reject")) {
                    pillBg = new Color(0xFE, 0xE2, 0xE2); pillFg = new Color(0xB9, 0x1C, 0x1C); // red
                } else {
                    pillBg = new Color(0xE5, 0xE7, 0xEB); pillFg = TEXT_MUTED; // neutral gray
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

    private Icon createPlusMinusIcon(boolean expanded) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw box
                g2d.setColor(new Color(100, 100, 100));
                g2d.drawRect(x + 2, y + 2, 8, 8);

                // Draw horizontal line (minus)
                g2d.drawLine(x + 4, y + 6, x + 8, y + 6);

                // Draw vertical line (plus) if collapsed
                if (!expanded) {
                    g2d.drawLine(x + 6, y + 4, x + 6, y + 8);
                }

                g2d.dispose();
            }

            @Override
            public int getIconWidth() {
                return 12;
            }

            @Override
            public int getIconHeight() {
                return 12;
            }
        };
    }

    // ============================================================
    // Events
    // ============================================================

    private void wireEvents() {
        refreshBtn.addActionListener(e -> {
            long now = System.currentTimeMillis();
            if (now - lastRefreshMs < REFRESH_COOLDOWN_MS) {
                long remaining = (REFRESH_COOLDOWN_MS - (now - lastRefreshMs)) / 1000;
                setStatus(false, "Please wait " + remaining + "s before refreshing again.");
                return;
            }
            lastRefreshMs = now;
            // Remember what is selected, and every expanded branch, so the reload can
            // return the tree to exactly the state the user left it in.
            restoreCourseId = selectedCourse != null ? selectedCourse.id : null;
            restoreAssignmentId = selectedAssignment != null ? selectedAssignment.id : null;
            restoreProblemId = selectedProblem != null ? selectedProblem.id : null;
            captureExpansionState();
            restoreInProgress = !restoreExpandedCourseIds.isEmpty() || restoreCourseId != null;
            refreshDialog();
            startRefreshCooldown();
        });

        logoutBtn.addActionListener(e -> {
            dispose();
            Globals.sessionHandler.logout(true, environment);
        });

        submitBtn.addActionListener(e -> attemptSubmit());

        // Filter change listeners
        // Filters apply to the whole tree: rebuild it from the cached data (no network),
        // preserving what is expanded/selected.
        allAssignmentsRadio.addActionListener(e -> reapplyFiltersFromCache());
        upcomingAssignmentsRadio.addActionListener(e -> reapplyFiltersFromCache());
        allProblemsRadio.addActionListener(e -> reapplyFiltersFromCache());
        unsolvedProblemsRadio.addActionListener(e -> reapplyFiltersFromCache());

        // Lazy load on expand (best UX)
        selectionTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = nodeFromPath(event.getPath());
                Object uo = node.getUserObject();

                if (uo instanceof CourseItem) {
                    loadAssignmentsIntoNode((CourseItem) uo, node);
                } else if (uo instanceof AssignmentItem) {
                    loadProblemsIntoNode((AssignmentItem) uo, node);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // no-op
            }
        });

        // Track selection (so Submit knows what’s chosen)
        selectionTree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = nodeFromPath(e.getNewLeadSelectionPath());
                updateSelectionStateFromNode(node);
            }
        });
    }

    // ============================================================
    // Display / Universe hook
    // ============================================================

    public void displaySubmitWindow() {
        applyDefaultSize();
        positionFrameNearWindow(
                this,
                Globals.Position.RIGHT,
                Universe.frameForEnvironment(environment)
        );
        setVisible(true);
        toFront();

        // Ensure current file from environment is loaded
        updateCurrentFileDisplay();

    }

    /**
     * Sizes the window to a comfortable fraction of the screen and centres it, once, on
     * the first show. This runs here (not in the constructor) so it wins over the
     * {@code pack()} the caller performs after construction, which would otherwise shrink
     * the window back to its minimum. Only applied once, so a user's later resize/move is
     * preserved across re-shows.
     */
    private void applyDefaultSize() {
        if (defaultSizeApplied) return;
        defaultSizeApplied = true;

        // The layout is content-heavy (three detail panes plus the submission-history
        // table), so it benefits from more room than the 860x560 minimum. Clamps keep it
        // sane on very small and very large displays.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = clamp((int) (screen.width * 0.72), 1000, 1400);
        int h = clamp((int) (screen.height * 0.80), 620, 940);
        setSize(w, h);
        setLocationRelativeTo(null);
    }

    @Override
    public void refreshDialog() {
        loadCourses();
    }

    // ============================================================
    // Tree + Selection State
    // ============================================================

    private void clearSelectionState() {
        selectedCourse = null;
        selectedAssignment = null;
        selectedProblem = null;
        updateAssignmentDetails(null);
        updateProblemDetails(null);
    }

    // ============================================================
    // Restore selection after a Refresh
    // ============================================================

    private void clearRestore() {
        restoreCourseId = null;
        restoreAssignmentId = null;
        restoreProblemId = null;
        restoreExpandedCourseIds.clear();
        restoreExpandedAssignmentIds.clear();
        restoreInProgress = false;
    }

    /** Records the ids of every currently expanded course and assignment, for a Refresh. */
    private void captureExpansionState() {
        restoreExpandedCourseIds.clear();
        restoreExpandedAssignmentIds.clear();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode courseNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (!(courseNode.getUserObject() instanceof CourseItem course)) continue;
            if (!selectionTree.isExpanded(new javax.swing.tree.TreePath(courseNode.getPath()))) continue;
            restoreExpandedCourseIds.add(course.id);
            for (int j = 0; j < courseNode.getChildCount(); j++) {
                DefaultMutableTreeNode aNode = (DefaultMutableTreeNode) courseNode.getChildAt(j);
                if (!(aNode.getUserObject() instanceof AssignmentItem a)) continue;
                if (selectionTree.isExpanded(new javax.swing.tree.TreePath(aNode.getPath()))) {
                    restoreExpandedAssignmentIds.add(a.id);
                }
            }
        }
    }

    /**
     * Re-expands the next remembered branch that still needs its children loaded, then
     * returns. The triggered load's done() calls this again, so branches are restored one
     * at a time (only one lazy load may run at once). When nothing is left to expand, the
     * remembered selection is re-applied and the restore ends.
     */
    private void pumpRestore() {
        if (!restoreInProgress || loading) return;

        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode courseNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (!(courseNode.getUserObject() instanceof CourseItem course)) continue;
            if (!restoreExpandedCourseIds.contains(course.id)) continue;

            if (!hasRealChildren(courseNode, AssignmentItem.class)) {
                // Its assignments are not loaded yet; expanding kicks off that load.
                selectionTree.expandPath(new javax.swing.tree.TreePath(courseNode.getPath()));
                return;
            }
            // Assignments are present; make sure the course shows as expanded, then look
            // for a remembered assignment under it that still needs its problems.
            selectionTree.expandPath(new javax.swing.tree.TreePath(courseNode.getPath()));
            for (int j = 0; j < courseNode.getChildCount(); j++) {
                DefaultMutableTreeNode aNode = (DefaultMutableTreeNode) courseNode.getChildAt(j);
                if (!(aNode.getUserObject() instanceof AssignmentItem a)) continue;
                if (!restoreExpandedAssignmentIds.contains(a.id)) continue;
                if (!hasRealChildren(aNode, ProblemItem.class)) {
                    selectionTree.expandPath(new javax.swing.tree.TreePath(aNode.getPath()));
                    return;
                }
                selectionTree.expandPath(new javax.swing.tree.TreePath(aNode.getPath()));
            }
        }

        // Everything the user had expanded is back; restore the selection and finish.
        finalizeRestore();
    }

    /** Re-selects the remembered course/assignment/problem (their ancestors are now loaded). */
    private void finalizeRestore() {
        DefaultMutableTreeNode courseNode = findChildById(rootNode, CourseItem.class, restoreCourseId);
        if (courseNode != null) {
            DefaultMutableTreeNode target = courseNode;
            if (restoreAssignmentId != null) {
                DefaultMutableTreeNode aNode = findChildById(courseNode, AssignmentItem.class, restoreAssignmentId);
                if (aNode != null) {
                    target = aNode;
                    if (restoreProblemId != null) {
                        DefaultMutableTreeNode pNode = findChildById(aNode, ProblemItem.class, restoreProblemId);
                        if (pNode != null) target = pNode;
                    }
                }
            }
            selectAndReveal(new javax.swing.tree.TreePath(target.getPath()));
        }
        clearRestore();
    }

    private void selectAndReveal(javax.swing.tree.TreePath path) {
        selectionTree.setSelectionPath(path);
        selectionTree.scrollPathToVisible(path);
    }

    /** A direct child of {@code parent} whose user object is a {@code type} with the given id. */
    private DefaultMutableTreeNode findChildById(DefaultMutableTreeNode parent, Class<?> type, String id) {
        if (id == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object uo = child.getUserObject();
            if (!type.isInstance(uo)) continue;
            String childId = uo instanceof CourseItem ? ((CourseItem) uo).id
                    : uo instanceof AssignmentItem ? ((AssignmentItem) uo).id
                    : uo instanceof ProblemItem ? ((ProblemItem) uo).id
                    : null;
            if (id.equals(childId)) return child;
        }
        return null;
    }

    private void updateSelectionStateFromNode(DefaultMutableTreeNode node) {
        clearSelectionState();
        selectedNode = node;

        if (node == null) {
            updateAssignmentDetails(null);
            updateProblemDetails(null);
            return;
        }

        Object uo = node.getUserObject();

        if (uo instanceof ProblemItem) {
            selectedProblem = (ProblemItem) uo;

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null && parent.getUserObject() instanceof AssignmentItem) {
                selectedAssignment = (AssignmentItem) parent.getUserObject();

                DefaultMutableTreeNode grand = (DefaultMutableTreeNode) parent.getParent();
                if (grand != null && grand.getUserObject() instanceof CourseItem) {
                    selectedCourse = (CourseItem) grand.getUserObject();
                }
            }

            // Update both assignment and problem details
            updateAssignmentDetails(selectedAssignment);
            updateProblemDetails(selectedProblem);

        } else if (uo instanceof AssignmentItem) {
            selectedAssignment = (AssignmentItem) uo;

            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
            if (parent != null && parent.getUserObject() instanceof CourseItem) {
                selectedCourse = (CourseItem) parent.getUserObject();
            }

            // Show assignment details, clear problem details
            updateAssignmentDetails(selectedAssignment);
            updateProblemDetails(null);

        } else if (uo instanceof CourseItem) {
            selectedCourse = (CourseItem) uo;

            // Clear both assignment and problem details when course is selected
            updateAssignmentDetails(null);
            updateProblemDetails(null);
        } else {
            // Clear both details for other selections
            updateAssignmentDetails(null);
            updateProblemDetails(null);
        }
    }

    private void updateProblemDetails(ProblemItem problem) {
        if (problem == null) {
            // Show placeholder text when no problem is selected
            problemDetailsPane.setText(
                "<html><body style='font-family: sans-serif; padding: 4px; color: #888;'>" +
                "<i>Select a problem to view details</i></body></html>"
            );
        } else {
            String title = problem.name != null ? problem.name : "Untitled Problem";

            // Description paragraph — only when the server actually sent one
            String descriptionHtml = "";
            if (problem.description != null && !problem.description.isBlank() && !problem.description.equals("null")) {
                descriptionHtml = "<p style='margin: 0 0 6px 0; color: #000000;'>"
                        + escapeHtml(problem.description) + "</p>";
            } else {
                descriptionHtml = "<p style='margin: 0 0 6px 0; color: #000000;'>No description available</p>";
            }

            // One compact metadata line: type, the intrinsic FA/PDA constraints when they
            // apply, points, grade, and the (colored) submissions-used count.
            StringBuilder meta = new StringBuilder();
            String typeName = problem.typeFullName();
            if (typeName != null) meta.append("Type: ").append(escapeHtml(typeName));
            // A non-positive cap (e.g. -1) means "no limit", so only show a real cap.
            if (problem.maxStates != null && problem.maxStates > 0) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Max states: ").append(problem.maxStates);
            }
            if (problem.isDeterministic != null) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Deterministic: ").append(problem.isDeterministic ? "Yes" : "No");
            }
            if (problem.maxPoints >= 0) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Points: ").append(problem.maxPoints);
            }
            if (problem.grade >= 0) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Grade: ").append(problem.grade)
                    .append(problem.maxPoints >= 0 ? " / " + problem.maxPoints : "");
            }
            // Submissions used, colored by how many attempts remain, on the same line.
            if (problem.submissionCount >= 0 && problem.maxSubmissions > 0) {
                int left = problem.attemptsLeft();
                String color = left == 0 ? "#c0392b" : (left == 1 ? "#e67e22" : "#27ae60");
                String warn = left == 0 ? " (limit reached)" : (left == 1 ? " (last attempt)" : "");
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("<span style='color:").append(color).append(";'><b>Submissions: ")
                    .append(problem.submissionCount).append(" / ").append(problem.maxSubmissions)
                    .append(warn).append("</b></span>");
            } else if (problem.submissionCount >= 0) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Submissions: ").append(problem.submissionCount).append(" (no limit)");
            }
            String metaHtml = meta.length() > 0
                ? "<p style='margin: 0; color: #555555;'>" + meta + "</p>" : "";

            String html = String.format(
                "<html><body style='font-family: sans-serif; padding: 4px;'>" +
                "<h3 style='margin: 0 0 4px 0; color: #000000;'>%s</h3>%s%s" +
                "</body></html>",
                escapeHtml(title),
                descriptionHtml,
                metaHtml
            );

            problemDetailsPane.setText(html);
            problemDetailsPane.setCaretPosition(0);
        }
        sizeDetailScrollToContent(problemDetailsScroll, problemDetailsPane, 44, 220, 1);
        // Refresh the submission history to match the selected problem (also runs after a
        // submit, since that path re-calls updateProblemDetails).
        updateSubmissionHistory(problem);
    }

    /**
     * Loads the caller's submission history for the selected problem into the table, off
     * the EDT. A per-request sequence number guards against a slower earlier request
     * overwriting the table after the user has already picked a different problem.
     */
    private void updateSubmissionHistory(ProblemItem problem) {
        final int seq = historyRequestSeq.incrementAndGet();
        submissionHistoryModel.setRowCount(0);
        submissionHistoryTable.setRowHeight(28); // reset per-row heights from wrapped text
        historyCellRenderer.clearHeights();
        setHistoryTableVisible(false);

        if (problem == null || selectedAssignment == null) {
            setHistoryStatus("Select a problem to view its submission history.", false);
            return;
        }

        setHistoryStatus("Loading submission history…", false);
        final String assignmentId = selectedAssignment.id;
        final String problemId = problem.id;

        new SwingWorker<List<Map<String, Object>>, Void>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                AFCTClient client = Globals.sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                if (client == null) return null;
                return client.getSubmissions(assignmentId, problemId);
            }

            @Override
            protected void done() {
                if (seq != historyRequestSeq.get()) return; // a newer selection superseded this
                try {
                    List<Map<String, Object>> subs = get();
                    if (subs == null) {
                        setHistoryStatus("Sign in to view submission history.", false);
                        return;
                    }
                    populateSubmissionHistory(subs);
                } catch (Exception ex) {
                    setHistoryStatus("Could not load submission history.", false);
                }
            }
        }.execute();
    }

    /** Fills the history table from the API rows (newest first) and updates the status line. */
    private void populateSubmissionHistory(List<Map<String, Object>> subs) {
        // A group problem gains a "Group Member" column showing who submitted.
        boolean group = selectedAssignment != null && selectedAssignment.isGroup;
        submissionHistoryModel.setRowCount(0);
        submissionHistoryTable.setRowHeight(28); // reset per-row heights from wrapped text
        historyCellRenderer.clearHeights();
        submissionHistoryModel.setColumnIdentifiers(group
                ? new Object[]{"Submitted", "Group Member", "File", "Status", "Result", "Feedback"}
                : new Object[]{"Submitted", "File", "Status", "Result", "Feedback"});
        applyHistoryColumnWidths(); // columns are rebuilt above, so re-apply widths

        if (subs.isEmpty()) {
            setHistoryStatus("No submissions yet.", false);
            setHistoryTableVisible(false);
            autoSizeSubmissionHistoryColumns();
            return;
        }
        for (Map<String, Object> s : subs) {
            String when = "";
            Object submittedAt = s.get("submittedAt");
            if (submittedAt != null) {
                try {
                    when = formatDueDate(java.time.Instant.parse(String.valueOf(submittedAt)));
                } catch (Exception e) {
                    when = String.valueOf(submittedAt);
                }
            }
            String file = s.get("fileName") != null ? String.valueOf(s.get("fileName")) : "";
            String status = s.get("status") != null ? String.valueOf(s.get("status")) : "";
            // Result: the evaluator verdict, blank while still queued/processing.
            String result;
            Object correct = s.get("correct");
            if (correct instanceof Boolean) {
                result = ((Boolean) correct) ? "Correct" : "Incorrect";
            } else {
                result = "PENDING".equals(status) || "PROCESSING".equals(status) ? "Not evaluated yet" : "";
            }
            String feedback = s.get("feedback") != null ? String.valueOf(s.get("feedback")) : "";

            if (group) {
                String member = s.get("submittedBy") != null ? String.valueOf(s.get("submittedBy")) : "";
                submissionHistoryModel.addRow(new Object[]{when, member, file, status, result, feedback});
            } else {
                submissionHistoryModel.addRow(new Object[]{when, file, status, result, feedback});
            }
        }
        int n = subs.size();
        setHistoryStatus(n + (n == 1 ? " submission" : " submissions"), true);
        setHistoryTableVisible(true);
        autoSizeSubmissionHistoryColumns();
    }

    /** Shows the history table only when there are submissions to display. */
    private void setHistoryTableVisible(boolean visible) {
        if (historyScrollPane != null && historyScrollPane.isVisible() != visible) {
            historyScrollPane.setVisible(visible);
            if (historyScrollPane.getParent() != null) {
                historyScrollPane.getParent().revalidate();
                historyScrollPane.getParent().repaint();
            }
        }
    }

    /**
     * Sets the history status line. Placeholder/info messages use the italic gray
     * style of the other cards' placeholders; the submission count is dark text.
     */
    private void setHistoryStatus(String text, boolean emphasize) {
        submissionHistoryStatus.setText(text);
        if (emphasize) {
            submissionHistoryStatus.setForeground(TEXT_DARK);
            submissionHistoryStatus.setFont(submissionHistoryStatus.getFont().deriveFont(Font.PLAIN, 14f));
        } else {
            submissionHistoryStatus.setForeground(new Color(0x88, 0x88, 0x88));
            submissionHistoryStatus.setFont(submissionHistoryStatus.getFont().deriveFont(Font.ITALIC, 14f));
        }
    }

    /**
     * Sizes each column to fit its header and cell content (with a cap so one long file
     * name can't dominate). The last column (Feedback) is left to absorb the remaining
     * width via AUTO_RESIZE_LAST_COLUMN.
     */
    private void autoSizeSubmissionHistoryColumns() {
        JTable t = submissionHistoryTable;
        javax.swing.table.TableColumnModel cm = t.getColumnModel();
        int lastCol = cm.getColumnCount() - 1;
        for (int col = 0; col < cm.getColumnCount(); col++) {
            javax.swing.table.TableColumn tc = cm.getColumn(col);
            javax.swing.table.TableCellRenderer hr = t.getTableHeader().getDefaultRenderer();
            int width = hr.getTableCellRendererComponent(t, tc.getHeaderValue(), false, false, -1, col)
                    .getPreferredSize().width;
            for (int row = 0; row < t.getRowCount(); row++) {
                javax.swing.table.TableCellRenderer cr = t.getCellRenderer(row, col);
                width = Math.max(width, t.prepareRenderer(cr, row, col).getPreferredSize().width);
            }
            width += 14; // a little padding
            if (col == lastCol) {
                tc.setPreferredWidth(Math.max(width, 220)); // Feedback: roomy, then stretches
            } else {
                tc.setPreferredWidth(Math.min(width, 240)); // cap the fixed columns
            }
        }
    }

    private void updateAssignmentDetails(AssignmentItem assignment) {
        if (assignment == null) {
            // Show placeholder text when no assignment is selected
            assignmentDetailsPane.setText(
                "<html><body style='font-family: sans-serif; padding: 4px; color: #888;'>" +
                "<i>Select an assignment to view details</i></body></html>"
            );
        } else {
            String title = assignment.name != null ? assignment.name : "Untitled Assignment";
            String description = assignment.description != null && !assignment.description.isBlank() && !assignment.description.equals("null")
                ? assignment.description
                : "No description available.";

            // One compact metadata line to save vertical space: due, then individual vs
            // group (with the student's group name), then the late-submission policy,
            // separated by middots.
            StringBuilder meta = new StringBuilder();
            java.time.Instant due = assignment.dueInstant();
            if (due != null) {
                meta.append("<b>Due:</b> ").append(escapeHtml(formatDueDate(due)));
            }

            String typeText = assignment.isGroup
                    ? (assignment.groupName != null && !assignment.groupName.isBlank()
                        ? "Group (your group: " + escapeHtml(assignment.groupName) + ")"
                        : "Group")
                    : "Individual";
            if (meta.length() > 0) meta.append(" &nbsp;&middot;&nbsp; ");
            meta.append(typeText);

            String lateText;
            if (assignment.allowLateSubmissions) {
                java.time.Instant cutoff = assignment.lateCutoffInstant();
                lateText = cutoff != null
                        ? "Late until " + escapeHtml(formatDueDate(cutoff))
                        : "Late accepted";
            } else {
                lateText = "No late submissions";
            }
            meta.append(" &nbsp;&middot;&nbsp; ").append(lateText);

            // Three compact rows: title, the metadata line, and the description.
            String html = String.format(
                "<html><body style='font-family: sans-serif; padding: 4px;'>" +
                "<h3 style='margin: 0 0 4px 0; color: #000000;'>%s</h3>" +
                "<p style='margin: 0 0 6px 0; color: #555555;'>%s</p>" +
                "<p style='margin: 0; color: #000000;'>%s</p>" +
                "</body></html>",
                escapeHtml(title),
                meta.toString(),
                escapeHtml(description)
            );

            assignmentDetailsPane.setText(html);
            assignmentDetailsPane.setCaretPosition(0);
        }
        sizeDetailScrollToContent(assignmentDetailsScroll, assignmentDetailsPane, 44, 220, 2);
    }

    /** Parses a UTC ISO-8601 string to an Instant, or null if missing/blank/unparseable. */
    private static java.time.Instant parseIsoOrNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        if (s.isBlank() || "null".equals(s)) return null;
        try {
            return java.time.Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Clamps {@code v} into the inclusive range [min, max]. */
    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    /**
     * Sizes a details scroll pane to its text pane's content height, clamped to
     * [minH, maxH], so the Selected Assignment / Selected Problem boxes grow and shrink
     * to fit their text (with a floor) instead of holding a fixed slice of the window.
     */
    private void sizeDetailScrollToContent(JScrollPane scroll, JTextPane pane, int minH, int maxH, double minHeightDivisor) {
        int w = scroll.getViewport().getExtentSize().width;
        if (w <= 0) w = scroll.getWidth();
        if (w <= 0) w = 280;
        // Constrain the width so the HTML view reports its wrapped (content) height.
        pane.setSize(new Dimension(w, Integer.MAX_VALUE));
        int contentH = pane.getPreferredSize().height + 6;
        //System.out.printf("pane.getPreferredSize().height = %d, contentH = pane.getPreferredSize().height + 6 = %d\n", pane.getPreferredSize().height, contentH);
        int h = Math.max(minH, Math.min(contentH, maxH));
        int scrollPreferredWidth = scroll.getPreferredSize().width;
        //System.out.printf("scrollPreferredWidth = %d, h = %d\n", scrollPreferredWidth, h);
        scroll.setPreferredSize(new Dimension(scrollPreferredWidth, h));

        // Also set minium size to avoid the details section being compressed too far below its preferred size
        scroll.setMinimumSize(new Dimension(0, (int) (h / minHeightDivisor)));
//        scroll.setMinimumSize(new Dimension(0, h));

        scroll.revalidate();
        //System.out.printf("scroll.getWidth() = %d, scroll.getHeight() = %d\n", scroll.getWidth(), scroll.getHeight());
    }

    /** Formats a due-date Instant in the selected course's timezone (falling back to the local zone). */
    private String formatDueDate(java.time.Instant due) {
        java.time.ZoneId zone;
        try {
            zone = (selectedCourse != null && selectedCourse.timezone != null)
                    ? java.time.ZoneId.of(selectedCourse.timezone)
                    : java.time.ZoneId.systemDefault();
        } catch (Exception e) {
            zone = java.time.ZoneId.systemDefault();
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("MMM d, yyyy 'at' h:mm a z");
        return due.atZone(zone).format(fmt);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("\n", "<br>");
    }

    private DefaultMutableTreeNode nodeFromPath(TreePath path) {
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode) return (DefaultMutableTreeNode) last;
        return null;
    }

    private void clearTree() {
        rootNode.removeAllChildren();
        treeModel.reload();
    }

    private void setBusy(boolean isBusy, String message) {
        loading = isBusy;
        setControlsEnabled(!isBusy);
        setStatus(true, message);
    }

    private void setControlsEnabled(boolean enabled) {
        selectionTree.setEnabled(enabled);
        submitBtn.setEnabled(enabled);
        logoutBtn.setEnabled(enabled);
        // Refresh respects its own cooldown — only re-enable if cooldown has expired
        if (enabled) {
            long elapsed = System.currentTimeMillis() - lastRefreshMs;
            refreshBtn.setEnabled(elapsed >= REFRESH_COOLDOWN_MS);
        } else {
            refreshBtn.setEnabled(false);
        }
    }

    // ============================================================
    // Data loading
    // ============================================================

    /** Fetches the whole course tree once, caches it, and (re)builds the tree from it. */
    private void loadCourses() {
        if (loading) return;

        setBusy(true, "Loading courses…");

        new SwingWorker<Map<String, Object>, Void>() {
            private String err;

            @Override
            protected Map<String, Object> doInBackground() {
                try {
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    // One call returns every course with its assignments and problems.
                    return client.getTree();
                } catch (UnknownHostException ex) {
                    err = ErrorMessages.userMessageWithPrefix(CANT_CONNECT_TO_SERVER_MESSAGE, ex, "Unable to load courses.");
                    return null;
                } catch (Exception ex) {
                    err = ErrorMessages.userMessage(ex, "Unable to load courses.");
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (err != null) {
                        clearRestore();
                        setStatus(false, err);
                        return;
                    }

                    Map<String, Object> wrapper = get();
                    if (wrapper == null) {
                        clearRestore();
                        setStatus(false, "Unable to load courses.");
                        return;
                    }

                    cacheTree(wrapper);
                    buildTreeFromCache();
                } catch (Exception ex) {
                    setStatus(false, ErrorMessages.userMessage(ex, "Unable to load courses."));
                } finally {
                    setControlsEnabled(true);
                    loading = false;
                }
            }
        }.execute();
    }

    /** Indexes the fetched tree ({ courses: [{ assignments: [{ problems: [] }] }] }) into
     *  the per-course / per-assignment caches the lazy expanders and filters read from. */
    @SuppressWarnings("unchecked")
    private void cacheTree(Map<String, Object> wrapper) {
        treeCourseList = new java.util.ArrayList<>();
        treeAssignmentsByCourse.clear();
        treeProblemsByAssignment.clear();

        Object coursesObj = wrapper.get("courses");
        List<Map<String, Object>> courses =
                (coursesObj instanceof List) ? (List<Map<String, Object>>) coursesObj : new java.util.ArrayList<>();
        for (Map<String, Object> c : courses) {
            treeCourseList.add(c);
            String courseId = String.valueOf(c.get("id"));
            Object aObj = c.get("assignments");
            List<Map<String, Object>> assignments =
                    (aObj instanceof List) ? (List<Map<String, Object>>) aObj : new java.util.ArrayList<>();
            treeAssignmentsByCourse.put(courseId, assignments);
            for (Map<String, Object> a : assignments) {
                String aid = String.valueOf(a.get("id"));
                Object pObj = a.get("problems");
                List<Map<String, Object>> problems =
                        (pObj instanceof List) ? (List<Map<String, Object>>) pObj : new java.util.ArrayList<>();
                treeProblemsByAssignment.put(aid, problems);
            }
        }
    }

    /** Rebuilds the course nodes from the cached tree (collapsed, with lazy expand handles),
     *  then restores the pre-existing expanded/selected state. No network. */
    private void buildTreeFromCache() {
        clearTree();
        clearSelectionState();

        for (Map<String, Object> c : treeCourseList) {
            String id = String.valueOf(c.get("id"));
            String title = String.valueOf(c.getOrDefault("name", "Untitled Course"));
            Object tz = c.get("timezone");
            String timezone = (tz != null && !"null".equals(String.valueOf(tz))) ? String.valueOf(tz) : null;

            CourseItem course = new CourseItem(id, title, timezone);
            DefaultMutableTreeNode courseNode = new DefaultMutableTreeNode(course);
            // Placeholder so the node shows an expand handle; children build from cache on expand.
            courseNode.add(new DefaultMutableTreeNode(new Placeholder("Expand to load assignments…")));
            rootNode.add(courseNode);
        }

        treeModel.reload();

        if (rootNode.getChildCount() == 0) {
            setStatus(false, "No courses found.");
            clearRestore();
        } else {
            setStatus(true, "Courses loaded. Expand a course to view assignments.");
            if (restoreInProgress) {
                SwingUtilities.invokeLater(this::pumpRestore);
            }
        }
    }

    /** Re-applies the current filters by rebuilding the tree from the cache (no re-fetch),
     *  preserving the expanded branches and selection. */
    private void reapplyFiltersFromCache() {
        if (loading || treeCourseList.isEmpty()) return;
        restoreCourseId = selectedCourse != null ? selectedCourse.id : null;
        restoreAssignmentId = selectedAssignment != null ? selectedAssignment.id : null;
        restoreProblemId = selectedProblem != null ? selectedProblem.id : null;
        captureExpansionState();
        restoreInProgress = !restoreExpandedCourseIds.isEmpty() || restoreCourseId != null;
        buildTreeFromCache();
    }

    private void loadAssignmentsIntoNode(CourseItem course, DefaultMutableTreeNode courseNode) {
        loadAssignmentsIntoNode(course, courseNode, false);
    }

    private void loadAssignmentsIntoNode(CourseItem course, DefaultMutableTreeNode courseNode, boolean forceReload) {
        if (loading) return;

        // If already loaded (children are AssignmentItem), skip unless forcing reload
        if (!forceReload && hasRealChildren(courseNode, AssignmentItem.class)) return;

        setBusy(true, "Loading assignments…");

        new SwingWorker<List<Map<String, Object>>, Void>() {
            private String err;
            // The server's clock as of this call (from the assignments response), used
            // instead of the local machine's clock so "upcoming" isn't thrown off by
            // clock skew or timezone differences. Falls back to Instant.now() if unset.
            private java.time.Instant serverNow;

            @Override
            protected List<Map<String, Object>> doInBackground() {
                try {
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    // Served from the cached tree (fetched once); the server clock came with it.
                    serverNow = client.getLastAssignmentsServerTime();
                    return new java.util.ArrayList<>(
                            treeAssignmentsByCourse.getOrDefault(course.id, java.util.Collections.emptyList()));
                } catch (Exception ex) {
                    err = ErrorMessages.userMessage(ex, "Unable to load assignments.");
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (err != null) {
                        setStatus(false, err);
                        return;
                    }

                    List<Map<String, Object>> raw = get();
                    if (raw == null) {
                        setStatus(false, "Unable to load assignments.");
                        return;
                    }

                    courseNode.removeAllChildren();

                    if (raw.isEmpty()) {
                        courseNode.add(new DefaultMutableTreeNode(new Placeholder("No assignments.")));
                    } else {
                        java.time.Instant now = serverNow != null ? serverNow : java.time.Instant.now();
                        boolean upcomingOnly = upcomingAssignmentsRadio.isSelected();
                        int displayedCount = 0;

                        // Show assignments earliest-due first; missing/unparseable dates sort last.
                        raw.sort((x, y) -> {
                            java.time.Instant dx = parseIsoOrNull(x.get("dueDate"));
                            java.time.Instant dy = parseIsoOrNull(y.get("dueDate"));
                            if (dx == null && dy == null) return 0;
                            if (dx == null) return 1;
                            if (dy == null) return -1;
                            return dx.compareTo(dy);
                        });

                        for (Map<String, Object> a : raw) {
                            String id = String.valueOf(a.get("id"));
                            String title = String.valueOf(a.getOrDefault("title", "Untitled Assignment"));
                            String description = String.valueOf(a.getOrDefault("description", ""));
                            String dueDateStr = a.get("dueDate") != null ? String.valueOf(a.get("dueDate")) : null;

                            // Apply upcoming filter — dueDate is UTC ISO-8601, so parse as an
                            // Instant and compare against the server's clock (not the local
                            // machine's, and not a naive/timezone-less parse).
                            boolean isUpcoming = false;
                            if (dueDateStr != null && !dueDateStr.equals("null")) {
                                try {
                                    java.time.Instant dueInstant = java.time.Instant.parse(dueDateStr);
                                    isUpcoming = dueInstant.isAfter(now);
                                } catch (Exception e) {
                                    // If date parsing fails, treat as not upcoming
                                    isUpcoming = false;
                                }
                            }

                            // Skip if filtering for upcoming and this isn't upcoming
                            if (upcomingOnly && !isUpcoming) {
                                continue;
                            }

                            // Individual vs group, the caller's group name, and whether late
                            // work is accepted, all straight from the assignments API.
                            boolean isGroup = Boolean.TRUE.equals(a.get("isGroup"));
                            Object groupNameObj = a.get("groupName");
                            String groupName = groupNameObj != null ? String.valueOf(groupNameObj) : null;
                            boolean allowLate = Boolean.TRUE.equals(a.get("allowLateSubmissions"));
                            String lateCutoffStr = a.get("lateCutoff") != null
                                    ? String.valueOf(a.get("lateCutoff")) : null;

                            // Problems come embedded in the assignments response, so we know
                            // the count now (before expanding).
                            Object problemsObj = a.get("problems");
                            int problemCount = (problemsObj instanceof List) ? ((List<?>) problemsObj).size() : 0;

                            displayedCount++;
                            AssignmentItem assignment = new AssignmentItem(
                                    id, title, description, dueDateStr, isGroup, groupName, allowLate,
                                    lateCutoffStr, problemCount);
                            DefaultMutableTreeNode aNode = new DefaultMutableTreeNode(assignment);

                            if (problemCount > 0) {
                                // Placeholder child so the node shows an expand handle; the real
                                // problems load lazily when expanded.
                                aNode.add(new DefaultMutableTreeNode(new Placeholder("Expand to load problems…")));
                            } else {
                                // Empty assignment: say so directly rather than inviting a load
                                // that would only reveal it is empty.
                                aNode.add(new DefaultMutableTreeNode(new Placeholder("No problems in this assignment.")));
                            }
                            courseNode.add(aNode);
                        }

                        if (displayedCount == 0) {
                            String msg = upcomingOnly ? "No upcoming assignments." : "No assignments.";
                            courseNode.add(new DefaultMutableTreeNode(new Placeholder(msg)));
                        }
                    }

                    treeModel.reload(courseNode);
                    setStatus(true, "Assignments loaded. Expand an assignment to view problems.");

                    // Continue restoring the pre-Refresh tree state. Deferred so `loading`
                    // is cleared before the next branch is expanded.
                    if (restoreInProgress) {
                        SwingUtilities.invokeLater(SubmitWindow.this::pumpRestore);
                    }

                } catch (Exception ex) {
                    setStatus(false, ErrorMessages.userMessage(ex, "Unable to load assignments."));
                } finally {
                    setControlsEnabled(true);
                    loading = false;
                }
            }
        }.execute();
    }

    private void loadProblemsIntoNode(AssignmentItem assignment, DefaultMutableTreeNode assignmentNode) {
        loadProblemsIntoNode(assignment, assignmentNode, false);
    }

    private void loadProblemsIntoNode(AssignmentItem assignment, DefaultMutableTreeNode assignmentNode, boolean forceReload) {
        if (loading) return;

        // If already loaded (children are ProblemItem), skip unless forcing reload
        if (!forceReload && hasRealChildren(assignmentNode, ProblemItem.class)) return;

        setBusy(true, "Loading problems…");

        new SwingWorker<List<Map<String, Object>>, Void>() {
            private String err;

            @Override
            protected List<Map<String, Object>> doInBackground() {
                try {
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    // Served from the cached tree (fetched once), not a network call.
                    return new java.util.ArrayList<>(
                            treeProblemsByAssignment.getOrDefault(assignment.id, java.util.Collections.emptyList()));
                } catch (Exception ex) {
                    err = ErrorMessages.userMessage(ex, "Unable to load problems.");
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    if (err != null) {
                        setStatus(false, err);
                        return;
                    }

                    List<Map<String, Object>> raw = get();
                    if (raw == null) {
                        setStatus(false, "Unable to load problems.");
                        return;
                    }

                    assignmentNode.removeAllChildren();

                    if (raw.isEmpty()) {
                        assignmentNode.add(new DefaultMutableTreeNode(new Placeholder("No problems.")));
                    } else {
                        boolean unsolvedOnly = unsolvedProblemsRadio.isSelected();
                        int displayedCount = 0;

                        // Show problems in alphabetical order by title (case-insensitive).
                        raw.sort((x, y) -> {
                            String tx = String.valueOf(x.getOrDefault("title", ""));
                            String ty = String.valueOf(y.getOrDefault("title", ""));
                            return tx.compareToIgnoreCase(ty);
                        });

                        for (Map<String, Object> p : raw) {
                            String id = String.valueOf(p.get("id"));
                            String title = String.valueOf(p.getOrDefault("title", "Untitled Problem"));

                            // Get description, handling null properly (the client API may omit it)
                            Object descObj = p.get("description");
                            String description = (descObj != null && !String.valueOf(descObj).equals("null"))
                                ? String.valueOf(descObj)
                                : "";

                            boolean solved = p.get("solved") != null && (Boolean) p.get("solved");

                            // Skip if filtering for unsolved and this is solved
                            if (unsolvedOnly && solved) {
                                continue;
                            }

                            displayedCount++;

                            Object typeObj = p.get("type");
                            String type = (typeObj != null && !"null".equals(String.valueOf(typeObj)))
                                ? String.valueOf(typeObj) : null;

                            // Intrinsic FA/PDA constraints, null when not set for this problem.
                            Object msObj = p.get("maxStates");
                            Integer maxStates = (msObj instanceof Number) ? ((Number) msObj).intValue() : null;
                            Object detObj = p.get("isDeterministic");
                            Boolean isDeterministic = (detObj instanceof Boolean) ? (Boolean) detObj : null;

                            // Create problem item with original title (checkmark added in toString)
                            ProblemItem problem = new ProblemItem(id, title, description, solved,
                                    type, asInt(p.get("maxPoints")), asInt(p.get("maxSubmissions")),
                                    asInt(p.get("submissionCount")), asInt(p.get("grade")),
                                    maxStates, isDeterministic);
                            assignmentNode.add(new DefaultMutableTreeNode(problem));
                        }

                        if (displayedCount == 0) {
                            String msg = unsolvedOnly ? "No unsolved problems." : "No problems.";
                            assignmentNode.add(new DefaultMutableTreeNode(new Placeholder(msg)));
                        }
                    }

                    treeModel.reload(assignmentNode);
                    setStatus(true, "Ready. Select a problem and submit.");

                    // Continue restoring the pre-Refresh tree state (problems just arrived).
                    if (restoreInProgress) {
                        SwingUtilities.invokeLater(SubmitWindow.this::pumpRestore);
                    }

                } catch (Exception ex) {
                    setStatus(false, ErrorMessages.userMessage(ex, "Unable to load problems."));
                } finally {
                    setControlsEnabled(true);
                    loading = false;
                }
            }
        }.execute();
    }

    /** Parses a JSON number field, returning -1 when missing or non-numeric. */
    private static int asInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean hasRealChildren(DefaultMutableTreeNode node, Class<?> clazz) {
        if (node == null || node.getChildCount() == 0) return false;
        for (int i = 0; i < node.getChildCount(); i++) {
            Object uo = ((DefaultMutableTreeNode) node.getChildAt(i)).getUserObject();
            if (clazz.isInstance(uo)) return true;
        }
        return false;
    }


    // ============================================================
    // File selection
    // ============================================================

    // TODO: currently it is impossible to submit without saving.
    //  As well, the submission window does not display the current window as the selected file if it has not been saved
    //  These are both major issues for the user experience, as trying to submit will just end with them seeing:
    //  "No file open. Open a file in the editor first." which will make no sense as they clearly have a file open,
    //  it just hasn't been saved yet. If we, for some reason, want to force users to save the file first, then trying
    //  to submit an unsaved file should just open the file save dialog, then submit after it has been saved.
    //  As well, being able to submit without saving at the very least needs to be an option that can be enabled
    //  somewhere, as lacking this ability makes testing a major headache.



    private void browseForFile() {
        JFileChooser chooser = new JFileChooser();
        // Start in the directory of the currently selected file, or user home
        if (selectedFile != null && selectedFile.getParentFile() != null) {
            chooser.setCurrentDirectory(selectedFile.getParentFile());
        }
        chooser.setDialogTitle("Choose file to submit");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            // The user picked a file explicitly: keep it, and stop auto-following the
            // editor's open file until they choose "Use open file".
            fileManuallyChosen = true;
            selectedFile = chosen;
            fileTF.setText(chosen.getName());
            fileTF.setForeground(new Color(60, 60, 60));
            fileTF.setToolTipText(chosen.getAbsolutePath());

            // Update the window title to reflect the chosen file
            this.setTitle(chosen.getName() + " - Submit");

            refreshFileButtons();
        }
    }

    /** Discards a browsed override and falls back to the editor's currently open file. */
    private void useOpenFile() {
        fileManuallyChosen = false;
        updateCurrentFileDisplay();
    }

    public void updateCurrentFileDisplay() {
        // Respect a file the user browsed to; only refresh the button state for it.
        if (fileManuallyChosen) {
            refreshFileButtons();
            return;
        }

        // Update the window title to reflect the chosen file
        EnvironmentFrame frame = Universe.frameForEnvironment(this.environment);
        this.setTitle(frame.getDescription() + " - Submit");

        File envFile = environment.getFile();

        // Check if file exists (saved file)
        if (envFile != null && envFile.exists()) {
            selectedFile = envFile;
            fileTF.setText(envFile.getName());
            fileTF.setForeground(new Color(60, 60, 60));
            fileTF.setToolTipText(envFile.getAbsolutePath());
        }
        // Check if file is set but not saved yet (unsaved document)
        else if (envFile != null) {
            selectedFile = envFile;
            // Get the display name from the environment frame
            String displayName = getEnvironmentDisplayName();
            fileTF.setText(displayName);
            fileTF.setForeground(new Color(60, 60, 60));
            fileTF.setToolTipText("Unsaved document — save it in the editor before submitting.");
        }
        // No file at all — show clickable prompt
        else {
            selectedFile = null;
            //fileTF.setText("Click Browse to choose a file…");
            //fileTF.setForeground(new Color(150, 150, 150));
            //fileTF.setToolTipText("The file that will be submitted. Click to browse for another.");

            fileTF.setText(frame.getDescription());
            fileTF.setForeground(new Color(60, 60, 60));
            fileTF.setToolTipText("Unsaved document — save it in the editor before submitting.");
        }

        refreshFileButtons();
    }

    /**
     * Enables "Use open file" only when a browsed override is active and the editor
     * actually has an open file to snap back to.
     */
    private void refreshFileButtons() {
        if (useOpenFileBtn == null) return;
        useOpenFileBtn.setEnabled(fileManuallyChosen && environment.getFile() != null);
    }

    private String getEnvironmentDisplayName() {
        // Try to get the display name from the environment's frame
        try {
            gui.environment.EnvironmentFrame frame = gui.environment.Universe.frameForEnvironment(environment);
            if (frame != null) {
                String desc = frame.getDescription();
                // Remove the dirty marker (*) if present
                if (desc != null && desc.startsWith("*")) {
                    desc = desc.substring(1);
                }
                return desc != null ? desc : "Unsaved document";
            }
        } catch (Exception e) {
            // Fallback if we can't get the frame
        }

        // Fallback: try to get filename from the file object
        File envFile = environment.getFile();
        if (envFile != null) {
            return envFile.getName();
        }

        return "Unsaved document";
    }

    // ============================================================
    // Submit
    // ============================================================

    private void attemptSubmit() {
        if (!validateSelection()) return;

        File fileToUse = selectedFile;

        if (fileManuallyChosen) {
            if (selectedFile == null || !selectedFile.exists()) {
                setStatus(false, "Unable to find the manually chosen file, it may have been moved or deleted.");
                return;
            }
        }
        // File not yet named or saved
        else if (selectedFile == null) {
            fileToUse = createTempFile();
        }
        // File exists (but may or may not be saved)
        else {
            fileToUse = createTempFileWithName(selectedFile.getName());
        }

        if (fileToUse == null) {
            // If fileToUse is null, temp file creation failed, and the status has already been set, so just return
            return;
        }

        doSubmit(new QueuedSubmission(selectedCourse.id, selectedAssignment.id,
                selectedProblem.id, fileToUse,
                selectedCourse.name, selectedAssignment.name, selectedProblem.name, selectedNode, selectedProblem));
    }

    /**
     * Kicks off a background submission. Only the Submit button is disabled,
     * and only until the server accepts the upload (202) — grading is polled
     * in the background so the user can keep browsing other assignments.
     * Progress lives in the Submissions dropdown; the status bar announces
     * the result when it lands.
     */
    private void doSubmit(QueuedSubmission qs) {
        submitBtn.setEnabled(false);
        setStatus(true, "Submitting…");
        log("SUBMIT_START", "course=" + qs.courseName + " assignment=" + qs.assignmentName
                + " problem=" + qs.problemName + " file=" + qs.file.getName());

        new SwingWorker<Map<String, Object>, String>() {
            private String err;
            private volatile boolean uploadAccepted = false;

            @Override
            protected Map<String, Object> doInBackground() {
                try {
                    AFCTClient client = sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                    if (client == null) { err = "Login cancelled."; return null; }

                    // Upload (202 Accepted), then poll for the graded result
                    Map<String, Object> accepted = client.createSubmission(qs.courseId, qs.assignmentId, qs.problemId, qs.file);
                    if (accepted == null || accepted.get("submissionId") == null) {
                        err = "No submission id returned by server.";
                        return null;
                    }
                    String submissionId = String.valueOf(accepted.get("submissionId"));
                    log("SUBMIT_ACCEPTED", "submissionId=" + submissionId + " problem=" + qs.problemName);
                    uploadAccepted = true;

                    // Upload accepted — unlock the UI and keep polling in the background.
                    publish("\"" + qs.problemName + "\" submitted — grading in the background. "
                            + "Its result will appear in Submission History.");

                    return client.waitForResult(submissionId, Duration.ofMinutes(2));
                } catch (UnknownHostException ex) {
                    err = ErrorMessages.userMessageWithPrefix(CANT_CONNECT_TO_SERVER_MESSAGE, ex, "Unexpected submission error.");
                    return null;
                } catch (Exception ex) {
                    err = ErrorMessages.userMessage(ex, "Unexpected submission error.");
                    return null;
                }
            }

            @Override
            protected void process(List<String> messages) {
                // First publish means the upload was accepted: re-enable Submit
                // and reflect the used attempt right away.
                submitBtn.setEnabled(true);
                bumpSubmissionCount(qs.problemId);
                if (!messages.isEmpty()) setStatus(true, messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                submitBtn.setEnabled(true);
                try {
                    if (err != null) {
                        if (uploadAccepted) {
                            // The submission itself went through — only fetching the result failed
                            log("RESULT_FETCH_FAIL", err);
                            setStatus(false, "\"" + qs.problemName + "\" was submitted, but the result couldn't be fetched: " + err);
                        } else {
                            log("SUBMIT_FAIL", err);
                            setStatus(false, "Submission failed (" + qs.problemName + "): " + err);
                        }
                        return;
                    }
                    Map<String, Object> result = get();
                    if (result == null) {
                        log("SUBMIT_FAIL", "null response");
                        setStatus(false, "Submission failed (" + qs.problemName + ") — no response from server.");
                        return;
                    }

                    String id = String.valueOf(result.getOrDefault("id", "?"));
                    String status = String.valueOf(result.get("status"));
                    log("SUBMIT_RESULT", "submissionId=" + id + " status=" + status
                            + " problem=" + qs.problemName);

                    if ("COMPLETED".equals(status)) {
                        boolean correct = Boolean.TRUE.equals(result.get("correct"));
                        Object feedback = result.get("feedback");
                        if (correct) {
                            setStatus(true, "Correct! \"" + qs.problemName + "\" accepted (id: " + id + ")");
                            qs.problemItem.solved = true;
                            if (qs.problemNode != null) {
                                DefaultTreeModel model = (DefaultTreeModel) selectionTree.getModel();
                                model.nodeChanged(qs.problemNode);
                            }
                        } else {
//                            String fb = (feedback != null && !"null".equals(String.valueOf(feedback)))
//                                    ? " Counterexample: " + feedback : "";
//                            setStatus(false, "Incorrect: \"" + qs.problemName + "\"." + fb);
                            String fb = (feedback != null && !"null".equals(String.valueOf(feedback))) ? feedback + "" : "";
                            setStatus(false, "\"" + qs.problemName + "\": " + fb);
                        }
                    } else if ("FAILED".equals(status)) {
                        setStatus(false, "Grading failed for \"" + qs.problemName + "\" — please resubmit.");
                    } else {
                        // Still PENDING/PROCESSING after the polling window
                        setStatus(true, "\"" + qs.problemName + "\" (id: " + id + ") is taking longer than usual — check Submission History later.");
                    }
                } catch (Exception ex) {
                    String friendly = ErrorMessages.userMessage(ex, "Unexpected submission error.");
                    log("SUBMIT_ERROR", friendly);
                    setStatus(false, "Submission error (" + qs.problemName + "): " + friendly);
                } finally {
                    // Reflect the final status in the inline Submission History.
                    if (selectedProblem != null && selectedProblem.id.equals(qs.problemId)) {
                        updateSubmissionHistory(selectedProblem);
                    }
                }
            }
        }.execute();
    }

    /** Increments the locally cached attempt count for a problem and refreshes the panel. EDT-safe. */
    private void bumpSubmissionCount(String problemId) {
        Runnable r = () -> {
            if (selectedProblem != null && selectedProblem.id.equals(problemId)
                    && selectedProblem.submissionCount >= 0) {
                selectedProblem.submissionCount++;
                updateProblemDetails(selectedProblem);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private boolean validateSelection() {
        if (selectedProblem == null) { setStatus(false, "Please select a problem in the tree."); return false; }
        if (selectedAssignment == null || selectedCourse == null) { setStatus(false, "Selection incomplete — re-select the problem."); return false; }
        // Removed to allow submitting unsaved files - IMPORTANT
        //if (selectedFile == null || !selectedFile.exists()) { setStatus(false, "No file open. Open a file in the editor first."); return false; }
        if (selectedProblem.attemptsLeft() == 0) {
            setStatus(false, "Submission limit reached (" + selectedProblem.submissionCount + "/"
                    + selectedProblem.maxSubmissions + ") for this problem.");
            return false;
        }
        if (selectedProblem.attemptsLeft() == 1) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "This is your LAST attempt for \"" + selectedProblem.name + "\" ("
                    + selectedProblem.submissionCount + "/" + selectedProblem.maxSubmissions
                    + " used).\nSubmit anyway?",
                    "Last attempt", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return false;
        }
        return true;
    }

    private File createTempFile() {
        // Try to create a temp file for the file that the user was working with
        try{
            // Create a temp file and encode the user's JFLAP program as the file
            File f = File.createTempFile("afct", ".jff");
            XMLCodec x = new XMLCodec();
            x.encode(this.environment.getObject(), f, null);
            return f;
        } catch (IOException e) {
            setStatus(false, "Error creating temp file: " + e.getMessage());
        } catch (EncodeException e) {
            setStatus(false, "Error saving temp file: " + e.getMessage());
        }
        return null;
    }

    private File createTempFileWithName(String fileName) {
        // Try to create a temp file for the file that the user was working with
        try{
            // Create a temporary directory
            Path tempDir = Files.createTempDirectory("afct");

            // Resolve the target file name inside said temp directory
            Path exactTempFile = tempDir.resolve(fileName);

            // Create the file
            Files.createFile(exactTempFile);
            File f = exactTempFile.toFile();

            XMLCodec x = new XMLCodec();
            x.encode(this.environment.getObject(), f, null);
            return f;
        } catch (IOException e) {
            setStatus(false, "Error creating temp file: " + e.getMessage());
        } catch (EncodeException e) {
            setStatus(false, "Error saving temp file: " + e.getMessage());
        }
        return null;
    }

    // ============================================================
    // Refresh cooldown
    // ============================================================

    private void startRefreshCooldown() {
        final int cooldownSecs = REFRESH_COOLDOWN_MS / 1000;
        refreshBtn.setEnabled(false);
        refreshBtn.setText("Refresh (" + cooldownSecs + "s)");
        if (refreshCooldownTimer != null) refreshCooldownTimer.stop();
        final int[] remaining = {cooldownSecs};
        refreshCooldownTimer = new Timer(1000, null);
        refreshCooldownTimer.addActionListener(e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                refreshCooldownTimer.stop();
                refreshBtn.setEnabled(true);
                refreshBtn.setText("Refresh");
            } else {
                refreshBtn.setText("Refresh (" + remaining[0] + "s)");
            }
        });
        refreshCooldownTimer.start();
    }

    // ============================================================
    // Logging
    // ============================================================

    private void log(String event, String detail) {
        LocalDateTime now = LocalDateTime.now();
        String line = "[" + now.format(LOG_FMT) + "] " + event + ": " + detail;
        System.out.println(line);
        try {
            Files.createDirectories(LOG_DIR);
            Path logFile = LOG_DIR.resolve("submissions-" + now.format(DATE_FMT) + ".log");
            Files.writeString(logFile, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            String msg = ErrorMessages.userMessage(ex, "Unable to write submission log.");
            System.err.println("Log write failed: " + msg);
        }
    }

    // ============================================================
    // QueuedSubmission
    // ============================================================

    private static class QueuedSubmission {
        final String courseId, assignmentId, problemId;
        final File file;
        final String courseName, assignmentName, problemName;
        final DefaultMutableTreeNode problemNode;
        final ProblemItem problemItem;

        QueuedSubmission(String courseId, String assignmentId, String problemId,
                         File file, String courseName, String assignmentName, String problemName,
                         DefaultMutableTreeNode problemNode, ProblemItem problemItem) {
            this.courseId = courseId;
            this.assignmentId = assignmentId;
            this.problemId = problemId;
            this.file = file;
            this.courseName = courseName;
            this.assignmentName = assignmentName;
            this.problemName = problemName;
            this.problemNode = problemNode;
            this.problemItem = problemItem;
        }
    }

    // ============================================================
    // Status
    // ============================================================

    private void setStatus(boolean success, String message) {
        if (message == null || message.isBlank()) {
            statusLabel.setText("<html>&nbsp;</html>");
            return;
        }
        String coloredMessage = success
                ? colorHTMLSuccessMessage(message)
                : colorHTMLErrorMessage(message);
        statusLabel.setText("<html>" + coloredMessage + "</html>");
    }
}
