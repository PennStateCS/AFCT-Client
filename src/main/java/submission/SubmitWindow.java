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

    private final Environment environment;
    // Injected rather than reached for via Globals, so window logic is testable
    // against a mock session.
    private final SessionHandler sessionHandler;

    private static final String baseTitle = "AFCT Submission Center";

    // ===============================
    // UI
    // ===============================

    private JButton refreshBtn;
    private JButton logoutBtn;
    private JButton submitBtn;

    private SubmissionFilePanel filePanel;
    private JLabel statusLabel;

    // Assignment details display
    private JTextPane assignmentDetailsPane;
    private JScrollPane assignmentDetailsScroll;

    // Problem details display
    private JTextPane problemDetailsPane;
    private JScrollPane problemDetailsScroll;

    // Submission history (for the selected problem); see HistoryPanel.
    private HistoryPanel historyPanel;
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

    // Refresh cooldown (rule in RefreshCooldown; the countdown animation is here).
    private final RefreshCooldown refreshCooldown = RefreshCooldown.systemClock();
    private Timer refreshCooldownTimer;

    // Applies the large default window size once, on the first show (see applyDefaultSize).
    private boolean defaultSizeApplied = false;

    // Submission event log; see SubmissionLog.
    private final SubmissionLog submissionLog = SubmissionLog.inWorkingDirectory();


    // What is selected in the tree, derived in one place; see Selection.
    private Selection selection = Selection.empty();

    // Puts expansion + selection back after a Refresh or filter change rebuilds
    // the tree; see TreeStateRestorer. Created lazily because it needs the tree.
    private TreeStateRestorer restorer;

    // The fetched course tree plus the filter/ordering rules; see CourseTreeCache.
    private final CourseTreeCache treeCache = new CourseTreeCache();

    public SubmitWindow(Environment environment, SessionHandler sessionHandler) {
        super(baseTitle);
        this.environment = environment;
        this.sessionHandler = sessionHandler;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(860, 560));

        buildUI();
        wireEvents();

        // Listen for file changes in the environment to update filename display
        environment.addFileChangeListener(e -> filePanel.updateFromEditor());

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
        root.setBackground(Theme.BG);

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
        title.setForeground(Theme.TEXT_DARK);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        refreshBtn = new JButton("Refresh");
        logoutBtn = new JButton("Logout");

        Theme.stylePrimaryButton(refreshBtn);
        Theme.stylePrimaryButton(logoutBtn);

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
        split.setBackground(Theme.BG);
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
        left.setBorder(CardPanel.cardBorder());

        // Filter panel at top
        JPanel filterPanel = new JPanel();
        filterPanel.setOpaque(false);
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 5));

        JPanel filterHeaderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        filterHeaderRow.setOpaque(false);
        filterHeaderRow.add(CardPanel.sectionLabel("Filters"));
        filterPanel.add(filterHeaderRow);

        // Assignment / Problem filters in a shared grid so the columns line up.
        JPanel filterGrid = new JPanel(new GridBagLayout());
        filterGrid.setOpaque(false);
        GridBagConstraints fc = new GridBagConstraints();
        fc.anchor = GridBagConstraints.WEST;
        fc.insets = new Insets(2, 5, 2, 5);

        JLabel assignmentLabel = new JLabel("Assignments:");
        Globals.boldFont(assignmentLabel);
        assignmentLabel.setForeground(Theme.TEXT_DARK);

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
        problemLabel.setForeground(Theme.TEXT_DARK);

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
        selectionTree.setBackground(Theme.CARD_BG);

        // Light-blue selection highlight to match the mockup
        UIManager.put("Tree.selectionBackground", Theme.SELECTION_BG);
        UIManager.put("Tree.selectionForeground", Theme.TEXT_DARK);
        UIManager.put("Tree.selectionBorderColor", Theme.SELECTION_BG);

        // Custom renderer with icons
        selectionTree.setCellRenderer(new SubmitTreeCellRenderer());

        // Use +/- symbols for expand/collapse
        UIManager.put("Tree.expandedIcon", createPlusMinusIcon(true));
        UIManager.put("Tree.collapsedIcon", createPlusMinusIcon(false));
        selectionTree.updateUI();

        JScrollPane sp = new JScrollPane(selectionTree);
        sp.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER));
        sp.getViewport().setBackground(Theme.CARD_BG);

        left.add(filterPanel, BorderLayout.NORTH);
        left.add(sp, BorderLayout.CENTER);

        return left;
    }

    private JComponent buildDetailsPanel() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 8, 0);

        // weighty 0: each card sizes to its own content, except history, which
        // takes the leftover vertical space so its table can grow.
        c.gridy = 0;
        c.weighty = 0;
        container.add(buildAssignmentCard(), c);
        c.gridy = 1;
        container.add(buildProblemCard(), c);
        c.gridy = 2;
        c.weighty = 1.0;
        container.add(buildHistoryCard(), c);
        c.gridy = 3;
        c.weighty = 0;
        container.add(buildSubmissionCard(), c);

        return container;
    }

    /** A titled card holding one read-only HTML details pane; the two details cards are twins. */
    private JPanel buildDetailsCard(String title, JTextPane pane, JScrollPane scroll, String placeholder) {
        JPanel card = new CardPanel(new GridBagLayout());
        card.setBorder(CardPanel.cardBorder());

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(8, 10, 0, 10);
        card.add(CardPanel.sectionLabel(title), c);

        c.gridy = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 10, 8, 10);

        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setBackground(card.getBackground());
        pane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        pane.setText(placeholder);
        // Rich descriptions may carry links; they open in the system browser. The
        // hrefs were validated at save time and again by the renderer.
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED
                    && e.getURL() != null) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    setStatus(false, "Could not open the link: " + e.getURL());
                }
            }
        });

        scroll.setPreferredSize(new Dimension(280, 100));
        // No inner frame: the panel's titled border is the only box we want.
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        card.add(scroll, c);

        return card;
    }

    private JComponent buildAssignmentCard() {
        assignmentDetailsPane = new JTextPane();
        assignmentDetailsScroll = new JScrollPane(assignmentDetailsPane);
        return buildDetailsCard("Selected Assignment", assignmentDetailsPane, assignmentDetailsScroll,
                DetailsHtml.assignmentPlaceholder());
    }

    private JComponent buildProblemCard() {
        problemDetailsPane = new JTextPane();
        problemDetailsScroll = new JScrollPane(problemDetailsPane);
        return buildDetailsCard("Selected Problem", problemDetailsPane, problemDetailsScroll,
                DetailsHtml.problemPlaceholder());
    }

    private JComponent buildHistoryCard() {
        historyPanel = new HistoryPanel();
        return historyPanel;
    }

    private JComponent buildSubmissionCard() {
        JPanel submissionPanel = new CardPanel(new GridBagLayout());
        submissionPanel.setBorder(CardPanel.cardBorder());

        GridBagConstraints c3 = new GridBagConstraints();
        c3.gridx = 0;
        c3.weightx = 1;
        c3.fill = GridBagConstraints.HORIZONTAL;
        c3.anchor = GridBagConstraints.WEST;
        c3.insets = new Insets(8, 10, 0, 10);

        c3.gridy = 0;
        submissionPanel.add(CardPanel.sectionLabel("Submission"), c3);
        c3.insets = new Insets(4, 10, 0, 10);

        filePanel = new SubmissionFilePanel(environment, this::setTitle);

        c3.gridy = 1;
        submissionPanel.add(labeled("File to Submit", filePanel), c3);

        // Submit button — full width
        submitBtn = new JButton("Submit");
        submitBtn.setPreferredSize(new Dimension(0, 38));
        Theme.stylePrimaryButton(submitBtn);
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

        return submissionPanel;
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
        l.setForeground(Theme.TEXT_DARK);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
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
            if (!refreshCooldown.tryRefresh()) {
                setStatus(false, "Please wait " + refreshCooldown.remainingSeconds()
                        + "s before refreshing again.");
                return;
            }
            // Remember what is selected, and every expanded branch, so the reload can
            // return the tree to exactly the state the user left it in.
            restorer().capture(rootNode, selection.course(), selection.assignment(), selection.problem());
            refreshDialog();
            startRefreshCooldownAnimation();
        });

        logoutBtn.addActionListener(e -> {
            dispose();
            sessionHandler.logout(true, environment);
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
        selection = Selection.empty();
        updateAssignmentDetails(null);
        updateProblemDetails(null);
    }

    // ============================================================
    // Restore selection after a Refresh
    // ============================================================

    private TreeStateRestorer restorer() {
        if (restorer == null) {
            restorer = new TreeStateRestorer(selectionTree, this::hasRealChildren);
        }
        return restorer;
    }

    private void clearRestore() {
        restorer().clear();
    }

    private void pumpRestore() {
        if (!loading) restorer().pump(rootNode);
    }

    private boolean restoreInProgress() {
        return restorer().inProgress();
    }

    private void updateSelectionStateFromNode(DefaultMutableTreeNode node) {
        selection = Selection.fromNode(node);
        updateAssignmentDetails(selection.assignment());
        updateProblemDetails(selection.problem());
    }

    private void updateProblemDetails(ProblemItem problem) {
        if (problem == null) {
            problemDetailsPane.setText(DetailsHtml.problemPlaceholder());
        } else {
            DetailsHtml.Rendered rendered = DetailsHtml.problemDetailsRendered(problem);
            problemDetailsPane.setText(rendered.html());
            // The math images the HTML refers to; HTMLEditorKit resolves the img
            // URLs through this document property.
            problemDetailsPane.getDocument().putProperty("imageCache", rendered.images());
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

        if (problem == null || selection.assignment() == null) {
            historyPanel.clear("Select a problem to view its submission history.");
            return;
        }

        historyPanel.clear("Loading submission history…");
        final String assignmentId = selection.assignment().id;
        final String problemId = problem.id;

        new SwingWorker<List<ApiModels.Submission>, Void>() {
            @Override
            protected List<ApiModels.Submission> doInBackground() throws Exception {
                AFCTClient client = sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                if (client == null) return null;
                return client.getSubmissions(assignmentId, problemId);
            }

            @Override
            protected void done() {
                if (seq != historyRequestSeq.get()) return; // a newer selection superseded this
                try {
                    List<ApiModels.Submission> subs = get();
                    if (subs == null) {
                        historyPanel.showMessage("Sign in to view submission history.", false);
                        return;
                    }
                    // A group problem gains a "Group Member" column showing who submitted.
                    boolean group = selection.assignment() != null && selection.assignment().isGroup;
                    historyPanel.populate(subs, group, SubmitWindow.this::formatDueDate);
                } catch (Exception ex) {
                    historyPanel.showMessage("Could not load submission history.", false);
                }
            }
        }.execute();
    }

    private void updateAssignmentDetails(AssignmentItem assignment) {
        if (assignment == null) {
            assignmentDetailsPane.setText(DetailsHtml.assignmentPlaceholder());
        } else {
            DetailsHtml.Rendered rendered =
                    DetailsHtml.assignmentDetailsRendered(assignment, this::formatDueDate);
            assignmentDetailsPane.setText(rendered.html());
            assignmentDetailsPane.getDocument().putProperty("imageCache", rendered.images());
            assignmentDetailsPane.setCaretPosition(0);
        }
        sizeDetailScrollToContent(assignmentDetailsScroll, assignmentDetailsPane, 44, 220, 2);
    }

    /** Parses a UTC ISO-8601 string to an Instant, or null if missing/blank/unparseable. */

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
        return ApiTree.formatDueDate(due, selection.course() != null ? selection.course().timezone : null);
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
        // Refresh respects its own cooldown — only re-enable once it has expired.
        refreshBtn.setEnabled(enabled && !refreshCooldown.coolingDown());
    }

    // ============================================================
    // Data loading
    // ============================================================

    /** Fetches the whole course tree once, caches it, and (re)builds the tree from it. */
    private void loadCourses() {
        if (loading) return;

        setBusy(true, "Loading courses…");

        new SwingWorker<ApiModels.Tree, Void>() {
            private String err;

            @Override
            protected ApiModels.Tree doInBackground() {
                try {
                    AFCTClient client = sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
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

                    ApiModels.Tree wrapper = get();
                    if (wrapper == null) {
                        clearRestore();
                        setStatus(false, "Unable to load courses.");
                        return;
                    }

                    treeCache.replaceWith(wrapper);
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


    /** Rebuilds the course nodes from the cached tree (collapsed, with lazy expand handles),
     *  then restores the pre-existing expanded/selected state. No network. */
    private void buildTreeFromCache() {
        clearTree();
        clearSelectionState();

        for (ApiModels.Course c : treeCache.courses()) {
            CourseItem course = ApiTree.course(c);
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
            if (restoreInProgress()) {
                SwingUtilities.invokeLater(this::pumpRestore);
            }
        }
    }

    /** Re-applies the current filters by rebuilding the tree from the cache (no re-fetch),
     *  preserving the expanded branches and selection. */
    private void reapplyFiltersFromCache() {
        if (loading || treeCache.isEmpty()) return;
        restorer().capture(rootNode, selection.course(), selection.assignment(), selection.problem());
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

        // Read on the EDT before the worker starts.
        final boolean upcomingOnly = upcomingAssignmentsRadio.isSelected();

        new SwingWorker<List<ApiModels.Assignment>, Void>() {
            private String err;

            @Override
            protected List<ApiModels.Assignment> doInBackground() {
                try {
                    AFCTClient client = sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    // Served from the cached tree (fetched once). "Upcoming" is judged
                    // against the server's clock from that fetch, not this machine's.
                    java.time.Instant serverNow = client.getLastServerTime();
                    return treeCache.visibleAssignments(course.id, upcomingOnly,
                            serverNow != null ? serverNow : java.time.Instant.now());
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

                    List<ApiModels.Assignment> raw = get();
                    if (raw == null) {
                        setStatus(false, "Unable to load assignments.");
                        return;
                    }

                    courseNode.removeAllChildren();

                    if (raw.isEmpty()) {
                        String msg = upcomingOnly ? "No upcoming assignments." : "No assignments.";
                        courseNode.add(new DefaultMutableTreeNode(new Placeholder(msg)));
                    } else {
                        for (ApiModels.Assignment a : raw) {
                            AssignmentItem assignment = ApiTree.assignment(a);
                            DefaultMutableTreeNode aNode = new DefaultMutableTreeNode(assignment);

                            if (assignment.problemCount > 0) {
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
                    }

                    treeModel.reload(courseNode);
                    setStatus(true, "Assignments loaded. Expand an assignment to view problems.");

                    // Continue restoring the pre-Refresh tree state. Deferred so `loading`
                    // is cleared before the next branch is expanded.
                    if (restoreInProgress()) {
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

        // Read on the EDT before the worker starts.
        final boolean unsolvedOnly = unsolvedProblemsRadio.isSelected();

        new SwingWorker<List<ApiModels.Problem>, Void>() {
            private String err;

            @Override
            protected List<ApiModels.Problem> doInBackground() {
                try {
                    AFCTClient client = sessionHandler.requireAuthenticated(Universe.frameForEnvironment(environment));
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    // Served from the cached tree (fetched once), not a network call.
                    return treeCache.visibleProblems(assignment.id, unsolvedOnly);
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

                    List<ApiModels.Problem> raw = get();
                    if (raw == null) {
                        setStatus(false, "Unable to load problems.");
                        return;
                    }

                    assignmentNode.removeAllChildren();

                    if (raw.isEmpty()) {
                        String msg = unsolvedOnly ? "No unsolved problems." : "No problems.";
                        assignmentNode.add(new DefaultMutableTreeNode(new Placeholder(msg)));
                    } else {
                        for (ApiModels.Problem p : raw) {
                            assignmentNode.add(new DefaultMutableTreeNode(ApiTree.problem(p)));
                        }
                    }

                    treeModel.reload(assignmentNode);
                    setStatus(true, "Ready. Select a problem and submit.");

                    // Continue restoring the pre-Refresh tree state (problems just arrived).
                    if (restoreInProgress()) {
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



    @Override
    public void updateCurrentFileDisplay() {
        filePanel.updateFromEditor();
    }

    // ============================================================
    // Submit
    // ============================================================

    private void attemptSubmit() {
        if (!validateSelection()) return;

        // A manually chosen file is the user's own and is sent as-is (and never
        // deleted afterwards); otherwise the editor's current object is encoded to
        // a temp file, so unsaved work submits exactly as it looks on screen.
        File fileToUse;
        boolean deleteWhenDone;
        File chosen = filePanel.chosenFile();
        if (filePanel.isManuallyChosen()) {
            if (chosen == null || !chosen.exists()) {
                setStatus(false, "Unable to find the manually chosen file, it may have been moved or deleted.");
                return;
            }
            fileToUse = chosen;
            deleteWhenDone = false;
        } else {
            try {
                fileToUse = SubmissionFiles.encodeToTemp(environment,
                        chosen != null ? chosen.getName() : null);
            } catch (IOException e) {
                setStatus(false, "Error creating temp file: " + e.getMessage());
                return;
            } catch (EncodeException e) {
                setStatus(false, "Error saving temp file: " + e.getMessage());
                return;
            }
            deleteWhenDone = true;
        }

        doSubmit(selection.course().id, selection.assignment().id, selection.problem(),
                selection.node(), fileToUse, deleteWhenDone);
    }

    /**
     * Kicks off a background submission (see SubmissionTask). Only the Submit
     * button is disabled, and only until the server accepts the upload — grading
     * is polled in the background so the user can keep browsing; the status bar
     * announces the result when it lands.
     */
    private void doSubmit(String courseId, String assignmentId, ProblemItem problem,
                          DefaultMutableTreeNode problemNode, File file, boolean deleteWhenDone) {
        final String problemName = problem.name;
        final String problemId = problem.id;
        submitBtn.setEnabled(false);
        setStatus(true, "Submitting…");
        log("SUBMIT_START", "problem=" + problemName + " file=" + file.getName());

        SubmissionTask.run(sessionHandler, Universe.frameForEnvironment(environment),
                courseId, assignmentId, problemId, file, deleteWhenDone,
                new SubmissionTask.Listener() {
            @Override
            public void uploadAccepted(String submissionId) {
                // The attempt is consumed the moment the server accepts the upload.
                log("SUBMIT_ACCEPTED", "submissionId=" + submissionId + " problem=" + problemName);
                submitBtn.setEnabled(true);
                replaceProblemItem(problemNode, item ->
                        item.submissionCount >= 0 ? item.withOneMoreSubmission() : item);
                setStatus(true, "\"" + problemName + "\" submitted — grading in the background. "
                        + "Its result will appear in Submission History.");
            }

            @Override
            public void finished(SubmissionTask.Outcome outcome) {
                submitBtn.setEnabled(true);
                String id = outcome.submissionId() != null ? outcome.submissionId() : "?";
                switch (outcome.kind()) {
                    case CORRECT -> {
                        log("SUBMIT_RESULT", "submissionId=" + id + " status=CORRECT problem=" + problemName);
                        setStatus(true, "Correct! \"" + problemName + "\" accepted (id: " + id + ")");
                        replaceProblemItem(problemNode, ProblemItem::asSolved);
                    }
                    case INCORRECT -> {
                        log("SUBMIT_RESULT", "submissionId=" + id + " status=INCORRECT problem=" + problemName);
                        // Feedback can be withheld for the problem; an empty quote
                        // after the colon would read like a glitch.
                        String fb = outcome.feedback();
                        setStatus(false, "\"" + problemName + "\": "
                                + (fb != null && !fb.isBlank() ? fb : "Incorrect."));
                    }
                    case NOT_GRADED -> {
                        log("SUBMIT_RESULT", "submissionId=" + id + " status=NOT_GRADED problem=" + problemName);
                        setStatus(true, "\"" + problemName + "\" submitted (id: " + id
                                + ") — your instructor will grade it.");
                    }
                    case GRADING_FAILED -> {
                        log("SUBMIT_RESULT", "submissionId=" + id + " status=FAILED problem=" + problemName);
                        setStatus(false, "Grading failed for \"" + problemName + "\" — please resubmit.");
                    }
                    case STILL_RUNNING -> {
                        log("SUBMIT_RESULT", "submissionId=" + id + " status=RUNNING problem=" + problemName);
                        setStatus(true, "\"" + problemName + "\" (id: " + id
                                + ") is taking longer than usual — check Submission History later.");
                    }
                    case RESULT_FETCH_FAILED -> {
                        // The submission itself went through — only fetching the result failed.
                        log("RESULT_FETCH_FAIL", String.valueOf(outcome.error()));
                        setStatus(false, "\"" + problemName
                                + "\" was submitted, but the result couldn't be fetched: " + outcome.error());
                    }
                    case UPLOAD_FAILED, LOGIN_CANCELLED -> {
                        log("SUBMIT_FAIL", String.valueOf(outcome.error()));
                        setStatus(false, "Submission failed (" + problemName + "): " + outcome.error());
                    }
                }
                // Reflect the final status in the inline Submission History.
                if (selection.problem() != null && selection.problem().id.equals(problemId)) {
                    updateSubmissionHistory(selection.problem());
                }
            }
        });
    }

    /**
     * Swaps a tree node's ProblemItem for an updated copy (items are immutable)
     * and refreshes whatever shows it: the tree row and, when the node is the
     * current selection, the details card. EDT-only (both callers are listener
     * callbacks delivered on the EDT).
     */
    private void replaceProblemItem(DefaultMutableTreeNode problemNode,
                                    java.util.function.UnaryOperator<ProblemItem> change) {
        if (problemNode == null || !(problemNode.getUserObject() instanceof ProblemItem item)) {
            return;
        }
        ProblemItem updated = change.apply(item);
        if (updated == item) return;
        problemNode.setUserObject(updated);
        ((DefaultTreeModel) selectionTree.getModel()).nodeChanged(problemNode);
        if (selection.node() == problemNode) {
            selection = Selection.fromNode(problemNode);
            updateProblemDetails(updated);
        }
    }

    private boolean validateSelection() {
        if (selection.problem() == null) { setStatus(false, "Please select a problem in the tree."); return false; }
        if (!selection.isSubmittable()) { setStatus(false, "Selection incomplete — re-select the problem."); return false; }
        // Removed to allow submitting unsaved files - IMPORTANT
        //if (selectedFile == null || !selectedFile.exists()) { setStatus(false, "No file open. Open a file in the editor first."); return false; }
        ProblemItem problem = selection.problem();
        if (problem.attemptsLeft() == 0) {
            setStatus(false, "Submission limit reached (" + problem.submissionCount + "/"
                    + problem.maxSubmissions + ") for this problem.");
            return false;
        }
        if (problem.attemptsLeft() == 1) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "This is your LAST attempt for \"" + problem.name + "\" ("
                    + problem.submissionCount + "/" + problem.maxSubmissions
                    + " used).\nSubmit anyway?",
                    "Last attempt", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return false;
        }
        return true;
    }


    // ============================================================
    // Refresh cooldown
    // ============================================================

    private void startRefreshCooldownAnimation() {
        final int cooldownSecs = RefreshCooldown.COOLDOWN_MS / 1000;
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
        submissionLog.log(event, detail);
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
