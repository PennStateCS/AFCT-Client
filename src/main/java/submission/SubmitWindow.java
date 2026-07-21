package submission;

import gui.Globals;
import gui.environment.Environment;
import gui.environment.Universe;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static gui.Globals.colorHTMLErrorMessage;
import static gui.Globals.colorHTMLSuccessMessage;

public class SubmitWindow extends JFrame implements SubmissionGUI {

    private final Environment environment;

    // ===============================
    // UI
    // ===============================

    private JButton infoBtn;
    private JButton refreshBtn;
    private JButton logoutBtn;
    private JButton submitBtn;

    private JTextField fileTF;
    private JLabel statusLabel;

    // Assignment details display
    private JTextPane assignmentDetailsPane;
    private JScrollPane assignmentDetailsScroll;

    // Problem details display
    private JTextPane problemDetailsPane;
    private JScrollPane problemDetailsScroll;

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

    // Refresh cooldown
    private long lastRefreshMs = 0;
    private static final int REFRESH_COOLDOWN_MS = 30_000;
    private Timer refreshCooldownTimer;

    // Submissions tracked this session, newest first (shown in the info dropdown)
    private final java.util.List<TrackedSubmission> trackedSubmissions =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static final int MAX_TRACKED = 20;

    // Submissions dialog (draggable, toggled by the Submissions button)
    private JDialog submissionsDialog;
    private javax.swing.table.DefaultTableModel submissionsModel;
    private Timer submissionsTicker;

    // Logging — writes to <project>/logs/submissions-YYYY-MM-DD.log
    private static final DateTimeFormatter LOG_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Path LOG_DIR = Paths.get(System.getProperty("user.dir"), "logs");

    // Selected items derived from tree selection
    private CourseItem selectedCourse = null;
    private AssignmentItem selectedAssignment = null;
    private ProblemItem selectedProblem = null;

    public SubmitWindow(Environment environment) {
        super("AFCT Submission");
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
                closeSubmissionsDialog();
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

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 10));

        JLabel title = new JLabel("AFCT Submission");
        Globals.boldFontAndChangeSize(title, 18);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        infoBtn = new JButton("Submissions");
        infoBtn.setToolTipText("Show this session's submissions and their queue status");
        refreshBtn = new JButton("Refresh");
        logoutBtn = new JButton("Logout");

        Globals.setPointerCursor(infoBtn);
        Globals.setPointerCursor(refreshBtn);
        Globals.setPointerCursor(logoutBtn);

        actions.add(infoBtn);
        actions.add(refreshBtn);
        actions.add(logoutBtn);

        header.add(title, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private JComponent buildCenter() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.40);
        split.setBorder(null);

        split.setLeftComponent(buildTreePanel());
        split.setRightComponent(buildDetailsPanel());

        return split;
    }

    private JComponent buildTreePanel() {
        JPanel left = new JPanel(new BorderLayout(10, 10));
        left.setBorder(BorderFactory.createTitledBorder("Select"));

        // Filter panel at top
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 5));

        // Assignment filters
        JPanel assignmentFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel assignmentLabel = new JLabel("Assignments:");
        Globals.boldFont(assignmentLabel);

        allAssignmentsRadio = new JRadioButton("All", true);
        upcomingAssignmentsRadio = new JRadioButton("Upcoming");

        ButtonGroup assignmentGroup = new ButtonGroup();
        assignmentGroup.add(allAssignmentsRadio);
        assignmentGroup.add(upcomingAssignmentsRadio);

        allAssignmentsRadio.setFocusPainted(false);
        upcomingAssignmentsRadio.setFocusPainted(false);

        assignmentFilterPanel.add(assignmentLabel);
        assignmentFilterPanel.add(allAssignmentsRadio);
        assignmentFilterPanel.add(upcomingAssignmentsRadio);

        // Problem filters
        JPanel problemFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel problemLabel = new JLabel("Problems:");
        Globals.boldFont(problemLabel);

        allProblemsRadio = new JRadioButton("All", true);
        unsolvedProblemsRadio = new JRadioButton("Unsolved");

        ButtonGroup problemGroup = new ButtonGroup();
        problemGroup.add(allProblemsRadio);
        problemGroup.add(unsolvedProblemsRadio);

        allProblemsRadio.setFocusPainted(false);
        unsolvedProblemsRadio.setFocusPainted(false);

        problemFilterPanel.add(problemLabel);
        problemFilterPanel.add(allProblemsRadio);
        problemFilterPanel.add(unsolvedProblemsRadio);

        filterPanel.add(assignmentFilterPanel);
        filterPanel.add(problemFilterPanel);

        // Tree
        rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);

        selectionTree = new JTree(treeModel);
        selectionTree.setRootVisible(false); // Hide root so courses appear at top level
        selectionTree.setShowsRootHandles(true);
        selectionTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // A slightly nicer default row height (optional)
        selectionTree.setRowHeight(22);

        // Custom renderer with icons
        selectionTree.setCellRenderer(new SubmitTreeCellRenderer());

        // Use +/- symbols for expand/collapse
        UIManager.put("Tree.expandedIcon", createPlusMinusIcon(true));
        UIManager.put("Tree.collapsedIcon", createPlusMinusIcon(false));
        selectionTree.updateUI();

        JScrollPane sp = new JScrollPane(selectionTree);

        left.add(filterPanel, BorderLayout.NORTH);
        left.add(sp, BorderLayout.CENTER);

        return left;
    }

    private JComponent buildDetailsPanel() {
        // Main container with vertical layout
        JPanel container = new JPanel(new GridBagLayout());
        GridBagConstraints containerConstraints = new GridBagConstraints();
        containerConstraints.gridx = 0;
        containerConstraints.weightx = 1;
        containerConstraints.fill = GridBagConstraints.BOTH;
        containerConstraints.insets = new Insets(0, 0, 0, 0);

        // ============================================================
        // Panel 1: Selected Assignment
        // ============================================================
        JPanel assignmentPanel = new JPanel(new GridBagLayout());
        assignmentPanel.setBorder(BorderFactory.createTitledBorder("Selected Assignment"));

        GridBagConstraints c1 = new GridBagConstraints();
        c1.gridx = 0;
        c1.gridy = 0;
        c1.weightx = 1;
        c1.weighty = 1;
        c1.fill = GridBagConstraints.BOTH;
        c1.insets = new Insets(8, 10, 8, 10);

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
        assignmentDetailsScroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        assignmentDetailsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        assignmentDetailsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        assignmentPanel.add(assignmentDetailsScroll, c1);

        // Add assignment panel to container
        containerConstraints.gridy = 0;
        containerConstraints.weighty = 0.25;
        container.add(assignmentPanel, containerConstraints);

        // ============================================================
        // Panel 2: Selected Problem
        // ============================================================
        JPanel problemPanel = new JPanel(new GridBagLayout());
        problemPanel.setBorder(BorderFactory.createTitledBorder("Selected Problem"));

        GridBagConstraints c2 = new GridBagConstraints();
        c2.gridx = 0;
        c2.gridy = 0;
        c2.weightx = 1;
        c2.weighty = 1;
        c2.fill = GridBagConstraints.BOTH;
        c2.insets = new Insets(8, 10, 8, 10);

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
        problemDetailsScroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        problemDetailsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        problemDetailsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        problemPanel.add(problemDetailsScroll, c2);

        // Add problem panel to container
        containerConstraints.gridy = 1;
        containerConstraints.weighty = 0.25;
        container.add(problemPanel, containerConstraints);

        // ============================================================
        // Panel 3: Submission (Current File + Submit Button)
        // ============================================================
        JPanel submissionPanel = new JPanel(new GridBagLayout());
        submissionPanel.setBorder(BorderFactory.createTitledBorder("Submission"));

        GridBagConstraints c3 = new GridBagConstraints();
        c3.gridx = 0;
        c3.weightx = 1;
        c3.fill = GridBagConstraints.HORIZONTAL;
        c3.insets = new Insets(8, 10, 0, 10);

        // Current file display — click to browse
        fileTF = new JTextField();
        fileTF.setEditable(false);
        fileTF.setMargin(new Insets(6, 10, 6, 10));
        fileTF.setForeground(new Color(60, 60, 60));
        fileTF.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileTF.setToolTipText("Click to choose a file");
        fileTF.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                browseForFile();
            }
        });

        // Set initial file from environment
        File envFile = environment.getFile();
        if (envFile != null) {
            selectedFile = envFile;
            fileTF.setText(envFile.getName());
        } else {
            fileTF.setText("Click to choose a file…");
            fileTF.setForeground(new Color(150, 150, 150));
        }

        c3.gridy = 0;
        submissionPanel.add(labeled("File to Submit", fileTF), c3);

        // Submit button — full width
        submitBtn = new JButton("Submit");
        submitBtn.setPreferredSize(new Dimension(0, 38));
        Globals.setPointerCursor(submitBtn);

        c3.gridy++;
        c3.insets = new Insets(16, 10, 0, 10);
        submissionPanel.add(submitBtn, c3);

        // Spacer to push content to top
        c3.gridy++;
        c3.weighty = 1;
        c3.fill = GridBagConstraints.BOTH;
        c3.insets = new Insets(0, 0, 0, 0);
        submissionPanel.add(Box.createVerticalStrut(1), c3);

        // Add submission panel to container
        containerConstraints.gridy = 2;
        containerConstraints.weighty = 0.5;
        container.add(submissionPanel, containerConstraints);

        return container;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel("<html>&nbsp;</html>");
        statusLabel.setBorder(new EmptyBorder(8, 2, 2, 2));
        Globals.changeSize(statusLabel, 13);
        footer.add(statusLabel, BorderLayout.CENTER);
        return footer;
    }

    private JComponent labeled(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        JLabel l = new JLabel(label);
        Globals.boldFont(l);
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
            long now = System.currentTimeMillis();
            if (now - lastRefreshMs < REFRESH_COOLDOWN_MS) {
                long remaining = (REFRESH_COOLDOWN_MS - (now - lastRefreshMs)) / 1000;
                setStatus(false, "Please wait " + remaining + "s before refreshing again.");
                return;
            }
            lastRefreshMs = now;
            refreshDialog();
            startRefreshCooldown();
        });

        infoBtn.addActionListener(e -> toggleSubmissionsDialog());

        logoutBtn.addActionListener(e -> {
            closeSubmissionsDialog();
            dispose();
            Globals.sessionHandler.logout(true);
        });

        submitBtn.addActionListener(e -> attemptSubmit());

        // Filter change listeners
        allAssignmentsRadio.addActionListener(e -> reloadAssignmentsForSelectedCourse());
        upcomingAssignmentsRadio.addActionListener(e -> reloadAssignmentsForSelectedCourse());
        allProblemsRadio.addActionListener(e -> reloadProblemsForSelectedAssignment());
        unsolvedProblemsRadio.addActionListener(e -> reloadProblemsForSelectedAssignment());

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
        setVisible(true);
        toFront();

        // Ensure current file from environment is loaded
        updateCurrentFileDisplay();
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

    private void updateSelectionStateFromNode(DefaultMutableTreeNode node) {
        clearSelectionState();

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
            if (problem.description != null && !problem.description.isBlank()) {
                descriptionHtml = "<p style='margin: 0 0 8px 0; color: #000000;'>"
                        + escapeHtml(problem.description) + "</p>";
            }

            // Metadata line: type · points · grade
            StringBuilder meta = new StringBuilder();
            if (problem.type != null) meta.append("Type: ").append(escapeHtml(problem.type));
            if (problem.maxPoints >= 0) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Points: ").append(problem.maxPoints);
            }
            if (problem.grade >= 0) {
                if (meta.length() > 0) meta.append(" &nbsp;·&nbsp; ");
                meta.append("Grade: ").append(problem.grade)
                    .append(problem.maxPoints >= 0 ? " / " + problem.maxPoints : "");
            }
            String metaHtml = meta.length() > 0
                ? "<p style='margin: 0 0 6px 0; color: #000000;'>" + meta + "</p>" : "";

            // Submissions used line, colored by how many attempts remain
            String subsHtml = "";
            if (problem.submissionCount >= 0 && problem.maxSubmissions > 0) {
                int left = problem.attemptsLeft();
                String color = left == 0 ? "#c0392b" : (left == 1 ? "#e67e22" : "#27ae60");
                String warn = left == 0 ? " — limit reached!" : (left == 1 ? " — last attempt!" : "");
                subsHtml = String.format(
                    "<p style='margin: 0; color: %s;'><b>Submissions used: %d / %d%s</b></p>",
                    color, problem.submissionCount, problem.maxSubmissions, warn);
            } else if (problem.submissionCount >= 0) {
                subsHtml = "<p style='margin: 0; color: #000000;'>Submissions used: "
                        + problem.submissionCount + " (no limit)</p>";
            }

            String html = String.format(
                "<html><body style='font-family: sans-serif; padding: 4px;'>" +
                "<h3 style='margin: 0 0 8px 0; color: #000000;'>%s</h3>%s%s%s" +
                "</body></html>",
                escapeHtml(title), descriptionHtml, metaHtml, subsHtml
            );

            problemDetailsPane.setText(html);
            problemDetailsPane.setCaretPosition(0);
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
            String description = assignment.description != null && !assignment.description.isBlank()
                ? assignment.description
                : "No description available.";

            String dueHtml = "";
            java.time.Instant due = assignment.dueInstant();
            if (due != null) {
                dueHtml = "<p style='margin: 0 0 8px 0; color: #555555;'><b>Due: "
                        + escapeHtml(formatDueDate(due)) + "</b></p>";
            }

            // Format as HTML for better display
            String html = String.format(
                "<html><body style='font-family: sans-serif; padding: 4px;'>" +
                "<h3 style='margin: 0 0 8px 0; color: #000000;'>%s</h3>%s" +
                "<p style='margin: 0; color: #000000;'>%s</p>" +
                "</body></html>",
                escapeHtml(title),
                dueHtml,
                escapeHtml(description)
            );

            assignmentDetailsPane.setText(html);
            assignmentDetailsPane.setCaretPosition(0);
        }
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

    private void loadCourses() {
        if (loading) return;

        setBusy(true, "Loading courses…");
        clearTree();
        clearSelectionState();

        new SwingWorker<List<Map<String, Object>>, Void>() {
            private String err;

            @Override
            protected List<Map<String, Object>> doInBackground() {
                try {
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated();
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }

                    // The client API derives the user from the bearer token
                    return client.getCourses();
                } catch (Exception ex) {
                    err = ex.getMessage();
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
                        setStatus(false, "Unable to load courses.");
                        return;
                    }

                    for (Map<String, Object> c : raw) {
                        String id = String.valueOf(c.get("id"));
                        String title = String.valueOf(c.getOrDefault("name", "Untitled Course"));
                        Object tz = c.get("timezone");
                        String timezone = (tz != null && !"null".equals(String.valueOf(tz))) ? String.valueOf(tz) : null;

                        CourseItem course = new CourseItem(id, title, timezone);
                        DefaultMutableTreeNode courseNode = new DefaultMutableTreeNode(course);

                        // Add a placeholder child so it shows an expand handle
                        courseNode.add(new DefaultMutableTreeNode(new Placeholder("Expand to load assignments…")));
                        rootNode.add(courseNode);
                    }

                    treeModel.reload();

                    if (rootNode.getChildCount() == 0) {
                        setStatus(false, "No courses found.");
                    } else {
                        setStatus(true, "Courses loaded. Expand a course to view assignments.");
                    }

                } catch (Exception ex) {
                    setStatus(false, ex.getMessage());
                } finally {
                    setControlsEnabled(true);
                    loading = false;
                }
            }
        }.execute();
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
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated();
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    List<Map<String, Object>> result = client.getAssignments(course.id);
                    serverNow = client.getLastAssignmentsServerTime();
                    return result;
                } catch (Exception ex) {
                    err = ex.getMessage();
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

                            displayedCount++;
                            AssignmentItem assignment = new AssignmentItem(id, title, description, dueDateStr);
                            DefaultMutableTreeNode aNode = new DefaultMutableTreeNode(assignment);

                            // placeholder child to show expand handle
                            aNode.add(new DefaultMutableTreeNode(new Placeholder("Expand to load problems…")));
                            courseNode.add(aNode);
                        }

                        if (displayedCount == 0) {
                            String msg = upcomingOnly ? "No upcoming assignments." : "No assignments.";
                            courseNode.add(new DefaultMutableTreeNode(new Placeholder(msg)));
                        }
                    }

                    treeModel.reload(courseNode);
                    setStatus(true, "Assignments loaded. Expand an assignment to view problems.");

                } catch (Exception ex) {
                    setStatus(false, ex.getMessage());
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
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated();
                    if (client == null) {
                        err = "Login cancelled.";
                        return null;
                    }
                    return client.getProblems(assignment.id);
                } catch (Exception ex) {
                    err = ex.getMessage();
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

                            // Create problem item with original title (checkmark added in toString)
                            ProblemItem problem = new ProblemItem(id, title, description, solved,
                                    type, asInt(p.get("maxPoints")), asInt(p.get("maxSubmissions")),
                                    asInt(p.get("submissionCount")), asInt(p.get("grade")));
                            assignmentNode.add(new DefaultMutableTreeNode(problem));
                        }

                        if (displayedCount == 0) {
                            String msg = unsolvedOnly ? "No unsolved problems." : "No problems.";
                            assignmentNode.add(new DefaultMutableTreeNode(new Placeholder(msg)));
                        }
                    }

                    treeModel.reload(assignmentNode);
                    setStatus(true, "Ready. Select a problem and submit.");

                } catch (Exception ex) {
                    setStatus(false, ex.getMessage());
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
    // Filter reload helpers
    // ============================================================

    private void reloadAssignmentsForSelectedCourse() {
        // Find the currently selected or expanded course node
        TreePath selectedPath = selectionTree.getSelectionPath();
        DefaultMutableTreeNode selectedNode = nodeFromPath(selectedPath);

        // Try to find a course node from the selection
        DefaultMutableTreeNode courseNode = null;
        CourseItem course = null;

        if (selectedNode != null) {
            Object uo = selectedNode.getUserObject();
            if (uo instanceof CourseItem) {
                courseNode = selectedNode;
                course = (CourseItem) uo;
            } else if (uo instanceof AssignmentItem) {
                courseNode = (DefaultMutableTreeNode) selectedNode.getParent();
                if (courseNode != null && courseNode.getUserObject() instanceof CourseItem) {
                    course = (CourseItem) courseNode.getUserObject();
                }
            } else if (uo instanceof ProblemItem) {
                DefaultMutableTreeNode assignmentNode = (DefaultMutableTreeNode) selectedNode.getParent();
                if (assignmentNode != null) {
                    courseNode = (DefaultMutableTreeNode) assignmentNode.getParent();
                    if (courseNode != null && courseNode.getUserObject() instanceof CourseItem) {
                        course = (CourseItem) courseNode.getUserObject();
                    }
                }
            }
        }

        // If no course found, try to find any expanded course
        if (course == null) {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) rootNode.getChildAt(i);
                if (selectionTree.isExpanded(new TreePath(node.getPath()))) {
                    if (node.getUserObject() instanceof CourseItem) {
                        courseNode = node;
                        course = (CourseItem) node.getUserObject();
                        break;
                    }
                }
            }
        }

        // Reload if we found a course
        if (course != null && courseNode != null) {
            loadAssignmentsIntoNode(course, courseNode, true);
        }
    }

    private void reloadProblemsForSelectedAssignment() {
        // Find the currently selected or expanded assignment node
        TreePath selectedPath = selectionTree.getSelectionPath();
        DefaultMutableTreeNode selectedNode = nodeFromPath(selectedPath);

        DefaultMutableTreeNode assignmentNode = null;
        AssignmentItem assignment = null;

        if (selectedNode != null) {
            Object uo = selectedNode.getUserObject();
            if (uo instanceof AssignmentItem) {
                assignmentNode = selectedNode;
                assignment = (AssignmentItem) uo;
            } else if (uo instanceof ProblemItem) {
                assignmentNode = (DefaultMutableTreeNode) selectedNode.getParent();
                if (assignmentNode != null && assignmentNode.getUserObject() instanceof AssignmentItem) {
                    assignment = (AssignmentItem) assignmentNode.getUserObject();
                }
            }
        }

        // If no assignment found, try to find any expanded assignment
        if (assignment == null) {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                DefaultMutableTreeNode courseNode = (DefaultMutableTreeNode) rootNode.getChildAt(i);
                for (int j = 0; j < courseNode.getChildCount(); j++) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) courseNode.getChildAt(j);
                    if (selectionTree.isExpanded(new TreePath(node.getPath()))) {
                        if (node.getUserObject() instanceof AssignmentItem) {
                            assignmentNode = node;
                            assignment = (AssignmentItem) node.getUserObject();
                            break;
                        }
                    }
                }
                if (assignment != null) break;
            }
        }

        // Reload if we found an assignment
        if (assignment != null && assignmentNode != null) {
            loadProblemsIntoNode(assignment, assignmentNode, true);
        }
    }

    // ============================================================
    // File selection
    // ============================================================

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
            selectedFile = chosen;
            fileTF.setText(chosen.getName());
            fileTF.setForeground(new Color(60, 60, 60));
        }
    }

    private void updateCurrentFileDisplay() {
        File envFile = environment.getFile();

        // Check if file exists (saved file)
        if (envFile != null && envFile.exists()) {
            selectedFile = envFile;
            fileTF.setText(envFile.getName());
            fileTF.setForeground(new Color(60, 60, 60));
        }
        // Check if file is set but not saved yet (unsaved document)
        else if (envFile != null) {
            selectedFile = envFile;
            // Get the display name from the environment frame
            String displayName = getEnvironmentDisplayName();
            fileTF.setText(displayName);
            fileTF.setForeground(new Color(60, 60, 60));
        }
        // No file at all — show clickable prompt
        else {
            selectedFile = null;
            fileTF.setText("Click to choose a file…");
            fileTF.setForeground(new Color(150, 150, 150));
        }
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

        doSubmit(new QueuedSubmission(selectedCourse.id, selectedAssignment.id,
                selectedProblem.id, selectedFile,
                selectedCourse.name, selectedAssignment.name, selectedProblem.name));
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

        final TrackedSubmission ts = track(qs);

        new SwingWorker<Map<String, Object>, String>() {
            private String err;
            private volatile boolean uploadAccepted = false;

            @Override
            protected Map<String, Object> doInBackground() {
                try {
                    AFCTClient client = Globals.sessionHandler.requireAuthenticated();
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
                    ts.update("In grading queue (PENDING)");

                    // Upload accepted — unlock the UI and keep polling in the background
                    publish("\"" + qs.problemName + "\" submitted — grading in background. "
                            + "Keep working; see the Submissions button for progress.");

                    return client.waitForResult(submissionId, java.time.Duration.ofMinutes(2), sub -> {
                        String st = String.valueOf(sub.get("status"));
                        if ("PROCESSING".equals(st)) ts.update("Grading (PROCESSING)");
                        else if ("PENDING".equals(st)) ts.update("In grading queue (PENDING)");
                    });
                } catch (Exception ex) {
                    err = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void process(java.util.List<String> messages) {
                // First publish means the upload was accepted: re-enable Submit
                // and reflect the used attempt right away.
                submitBtn.setEnabled(true);
                bumpSubmissionCount(qs.problemId);
                if (!messages.isEmpty()) setStatus(true, messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                submitBtn.setEnabled(true);
                if (err != null) {
                    if (uploadAccepted) {
                        // The submission itself went through — only fetching the result failed
                        log("RESULT_FETCH_FAIL", err);
                        ts.update("Submitted — result unavailable (" + err + ")");
                        setStatus(false, "\"" + qs.problemName + "\" was submitted, but the result couldn't be fetched: " + err);
                    } else {
                        log("SUBMIT_FAIL", err);
                        ts.finish("Failed: " + err);
                        setStatus(false, "Submission failed (" + qs.problemName + "): " + err);
                    }
                    return;
                }
                try {
                    Map<String, Object> result = get();
                    if (result == null) {
                        log("SUBMIT_FAIL", "null response");
                        ts.finish("Failed — no response");
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
                            ts.finish("Correct ✔");
                            setStatus(true, "Correct! \"" + qs.problemName + "\" accepted (id: " + id + ")");
                        } else {
                            ts.finish("Incorrect ✘");
                            String fb = (feedback != null && !"null".equals(String.valueOf(feedback)))
                                    ? " Counterexample: " + feedback : "";
                            setStatus(false, "Incorrect: \"" + qs.problemName + "\"." + fb);
                        }
                    } else if ("FAILED".equals(status)) {
                        ts.finish("Grading failed");
                        setStatus(false, "Grading failed for \"" + qs.problemName + "\" — please resubmit.");
                    } else {
                        // Still PENDING/PROCESSING after the polling window
                        ts.update("Still grading — check later");
                        setStatus(true, "\"" + qs.problemName + "\" (id: " + id + ") is taking longer than usual — check the Submissions button later.");
                    }
                } catch (Exception ex) {
                    log("SUBMIT_ERROR", ex.getMessage());
                    ts.finish("Error: " + ex.getMessage());
                    setStatus(false, "Submission error (" + qs.problemName + "): " + ex.getMessage());
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
        if (selectedFile == null || !selectedFile.exists()) { setStatus(false, "No file open. Open a file in the editor first."); return false; }
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

    // ============================================================
    // Refresh cooldown
    // ============================================================

    private void startRefreshCooldown() {
        refreshBtn.setEnabled(false);
        refreshBtn.setText("Refresh (30s)");
        if (refreshCooldownTimer != null) refreshCooldownTimer.stop();
        final int[] remaining = {30};
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
            System.err.println("Log write failed: " + ex.getMessage());
        }
    }

    // ============================================================
    // Submission info dropdown
    // ============================================================

    /** One submission tracked for the info dropdown. Worker threads update status; the EDT reads it. */
    private static class TrackedSubmission {
        final String fileName;
        final String problemName;
        final long startMs = System.currentTimeMillis();
        volatile String status = "Uploading…";
        volatile boolean done = false;
        volatile long endMs = -1;

        TrackedSubmission(String fileName, String problemName) {
            this.fileName = fileName;
            this.problemName = problemName;
        }

        void update(String status) {
            this.status = status;
        }

        void finish(String finalStatus) {
            this.status = finalStatus;
            this.done = true;
            this.endMs = System.currentTimeMillis();
        }

        /** Time from upload until grading finished (still counting if not done). */
        String elapsed() {
            long end = done ? endMs : System.currentTimeMillis();
            long secs = Math.max(0, (end - startMs) / 1000);
            return String.format("%d:%02d", secs / 60, secs % 60);
        }
    }

    private TrackedSubmission track(QueuedSubmission qs) {
        TrackedSubmission ts = new TrackedSubmission(qs.file.getName(), qs.problemName);
        trackedSubmissions.add(0, ts);
        while (trackedSubmissions.size() > MAX_TRACKED) {
            trackedSubmissions.remove(trackedSubmissions.size() - 1);
        }
        return ts;
    }

    /** Toggles the submissions dialog: open if closed, close if open. */
    private void toggleSubmissionsDialog() {
        if (submissionsDialog != null && submissionsDialog.isVisible()) {
            submissionsDialog.setVisible(false);
            return;
        }
        boolean firstOpen = submissionsDialog == null;
        if (firstOpen) {
            buildSubmissionsDialog();
        }
        refreshSubmissionsTable();

        // First open: drop it just under the button. After that it keeps
        // wherever the user dragged it (title bar drag).
        if (firstOpen) {
            Point p = infoBtn.getLocationOnScreen();
            int x = Math.max(0, p.x + infoBtn.getWidth() - submissionsDialog.getWidth());
            submissionsDialog.setLocation(x, p.y + infoBtn.getHeight() + 6);
        }
        submissionsDialog.setVisible(true);
    }

    /** Builds the non-modal, draggable dialog listing this session's submissions. */
    private void buildSubmissionsDialog() {
        submissionsDialog = new JDialog(this, "Submissions", false);
        submissionsDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

        String[] cols = {"File", "Problem", "Status", "In queue"};
        submissionsModel = new javax.swing.table.DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable table = new JTable(submissionsModel);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);

        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(520, 180));
        submissionsDialog.setContentPane(sp);
        submissionsDialog.pack();

        // Tick every second while visible so "In queue" counts up live
        submissionsTicker = new Timer(1000, e -> refreshSubmissionsTable());
        submissionsDialog.addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent e) { submissionsTicker.start(); }
            @Override public void componentHidden(ComponentEvent e) { submissionsTicker.stop(); }
        });
    }

    private void refreshSubmissionsTable() {
        if (submissionsModel == null) return;
        submissionsModel.setRowCount(0);
        synchronized (trackedSubmissions) {
            if (trackedSubmissions.isEmpty()) {
                submissionsModel.addRow(new Object[]{"—", "—", "No submissions this session", "—"});
            } else {
                for (TrackedSubmission ts : trackedSubmissions) {
                    submissionsModel.addRow(new Object[]{ts.fileName, ts.problemName, ts.status, ts.elapsed()});
                }
            }
        }
    }

    private void closeSubmissionsDialog() {
        if (submissionsTicker != null) {
            submissionsTicker.stop();
        }
        if (submissionsDialog != null) {
            submissionsDialog.setVisible(false);
            submissionsDialog.dispose();
            submissionsDialog = null;
            submissionsModel = null;
        }
    }

    // ============================================================
    // QueuedSubmission
    // ============================================================

    private static class QueuedSubmission {
        final String courseId, assignmentId, problemId;
        final File file;
        final String courseName, assignmentName, problemName;

        QueuedSubmission(String courseId, String assignmentId, String problemId,
                         File file, String courseName, String assignmentName, String problemName) {
            this.courseId = courseId;
            this.assignmentId = assignmentId;
            this.problemId = problemId;
            this.file = file;
            this.courseName = courseName;
            this.assignmentName = assignmentName;
            this.problemName = problemName;
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
