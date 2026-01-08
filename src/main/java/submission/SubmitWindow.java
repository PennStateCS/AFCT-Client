package submission;

import gui.Globals;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.prefs.Preferences;

import static gui.Globals.*;
import static submission.LoginWindow.createComboBoxPanel;
import static submission.LoginWindow.createInputPanel;
import static submission.SessionHandler.*;
import static submission.SessionHandler.defaultPassword;
import static submission.SubmitWindow.ComboBoxTarget.*;

public class SubmitWindow {
    private JPanel contentPane;
    // Course
    public JComboBox<CourseItem> courseBox;
    private JButton courseRefreshButton;
    // Assignment
    public JComboBox<AssignmentItem> assignmentBox;
    private JButton assignmentRefreshButton;
    private DetailsPanel2 assignmentDetailsPanel;
    public JRadioButton allAssignments;
    public JRadioButton upcomingAssignments;
    // Problem
    public JComboBox<ProblemItem> problemBox;
    private JButton problemRefreshButton;
    private DetailsPanel2 problemDetailsPanel;
    public JRadioButton allProblems;
    public JRadioButton uncompletedProblems;
    // Current FIle
    private JLabel currentFileLabel;
    private JButton viewCurrentButton;
    private JButton submitButton;
    // TODO: replace this with better, more modern user feedback methods
    private JTextArea result;
    private String resultText = "";

    private JScrollPane scrollPane;

    // Tracking for optimization
    private int selectedCourse = 0;
    private int selectedAssignment = 0;
    private int selectedProblem = 0;

    // Placeholder for combo boxes
    public static final String PLACEHOLDER = "— Select —";

    // Event guarding
    public volatile boolean isPopulating = false;

    public SubmitWindow() {
        contentPane = new JPanel();
        courseBox = new JComboBox<>();
        courseRefreshButton = new JButton();
        assignmentBox = new JComboBox<>();
        assignmentRefreshButton = new JButton();
        assignmentDetailsPanel = new DetailsPanel2();
        allAssignments = new JRadioButton("All Assignments");
        upcomingAssignments = new JRadioButton("Upcoming Assignments");
        problemBox = new JComboBox<>();
        problemRefreshButton = new JButton();
        problemDetailsPanel = new DetailsPanel2();
        allProblems = new JRadioButton("All Problems");
        uncompletedProblems = new JRadioButton("Uncompleted Problems");
        currentFileLabel = new JLabel("No File Selected");
        viewCurrentButton = new JButton("View");
        submitButton = new JButton("Submit");

        result = new JTextArea();

        setupGui();
        populateGui();
        setupEventHandlers();

        scrollPane = new JScrollPane(contentPane);
    }

    public JScrollPane getContentPane() {
        //return contentPane;
        return scrollPane;
    }

    public void appendResult(String line) {
        resultText += (line.endsWith("\n") ? line : (line + "\n"));
        result.setText(resultText);
        result.setCaretPosition(result.getDocument().getLength());
    }

    public void toggleCourseBox(boolean enabled) {
        courseBox.setEnabled(enabled);
        courseRefreshButton.setEnabled(enabled);
    }

    public void toggleAssignmentBox(boolean enabled) {
        assignmentBox.setEnabled(enabled);
        assignmentRefreshButton.setEnabled(enabled);
        if (!enabled) {
            assignmentDetailsPanel.setEnabled(false);
        }
        allAssignments.setEnabled(enabled);
        upcomingAssignments.setEnabled(enabled);
    }

    public void toggleProblemBox(boolean enabled) {
        problemBox.setEnabled(enabled);
        problemRefreshButton.setEnabled(enabled);
        if (!enabled) {
            problemDetailsPanel.setEnabled(false);
        }
        allProblems.setEnabled(enabled);
        uncompletedProblems.setEnabled(enabled);
    }

    public enum ComboBoxTarget {
        COURSE, ASSIGNMENT, PROBLEM
    }

    public <T> JComboBox<T> getTargetComboBox(ComboBoxTarget target) {
        return switch (target) {
            case COURSE -> (JComboBox<T>) courseBox;
            case ASSIGNMENT -> (JComboBox<T>) assignmentBox;
            case PROBLEM -> (JComboBox<T>) problemBox;
        };
    }

    public void toggleTargetComboBox(ComboBoxTarget target, boolean enabled) {
        switch (target) {
            case COURSE -> toggleCourseBox(enabled);
            case ASSIGNMENT -> toggleAssignmentBox(enabled);
            case PROBLEM -> toggleProblemBox(enabled);
        }
    }

    public void resetTargetComboBox(ComboBoxTarget target) {
        switch (target) {
            case COURSE -> setModel(courseBox, List.of(PLACEHOLDER), false);
            case ASSIGNMENT -> setModel(assignmentBox, List.of(PLACEHOLDER), false);
            case PROBLEM -> setModel(problemBox, List.of(PLACEHOLDER), false);
        }
    }

    public void disableAndResetTargetComboBox(ComboBoxTarget target) {
        toggleTargetComboBox(target, false);
        resetTargetComboBox(target);
    }

    public void disableAndResetAllComboBoxes() {
        // Disable and reset CourseBox
        disableAndResetTargetComboBox(COURSE);
        // Disable and reset AssignmentBox
        disableAndResetTargetComboBox(ASSIGNMENT);
        // Disable and reset ProblemBox
        disableAndResetTargetComboBox(PROBLEM);
    }

    private void setupGui() {
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        int vrtInset = 15;
        int hozInset = 20;

        // Create headerLabel
        JLabel headerLabel = new JLabel("AFCT Server - Submit");
        changeSize(headerLabel, 24);

        // Add headerLabel to contentPane
        c = setConstraints(1, 1, 0, y++, GridBagConstraints.NORTH);
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(vrtInset, hozInset, vrtInset, hozInset);
        contentPane.add(headerLabel, c);

        // Add combo boxes
        c.insets = new Insets(vrtInset, hozInset, 0, hozInset);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = y++;
        contentPane.add(createComboBoxWithRefreshPanel(courseBox, courseRefreshButton, "Course"), c);
        c.gridy = y++;
        contentPane.add(createComboBoxWithButtonsPanel(assignmentBox, assignmentRefreshButton, assignmentDetailsPanel, allAssignments, upcomingAssignments, "Assignment"), c);
        c.insets = new Insets(vrtInset-5, hozInset, 0, hozInset);
        c.gridy = y++;
        contentPane.add(createComboBoxWithButtonsPanel(problemBox, problemRefreshButton, problemDetailsPanel, allProblems, uncompletedProblems, "Problem"), c);

        // Add current file info
        c.gridy = y++;
        contentPane.add(createCurrentFilePanel(), c);

        // Add submitButton to contentPane
        changeSize(submitButton, 16);
        setPointerCursor(submitButton);
        //submitButton.setPreferredSize(new Dimension(360, 36));
        //submitButton.setMargin(new Insets(6, 12, 6, 12));
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        //c.insets = new Insets(5, hozInset, vrtInset, hozInset);
        c.insets = new Insets(vrtInset + 5, hozInset, vrtInset, hozInset);
        contentPane.add(submitButton, c);

        // Add result to contentPane
        result.setBorder(new LineBorder(new Color(210, 210, 210)));
        c.gridy = y++;
        contentPane.add(result, c);
    }

    private JPanel createCurrentFilePanel() {
        GridBagConstraints c;

        // Stylize currentFileLabel
        changeSize(currentFileLabel, 14);
        unBoldFont(currentFileLabel);
        //italicFont(currentFileLabel);

        // Create fileLabelPanel
        JPanel fileLabelPanel = new JPanel(new GridBagLayout());
        fileLabelPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

        // Add currentFileLabel to fileLabelPanel
        c = setConstraints(1, 1, 0, 0);
        c.insets = new Insets(6, 12, 6, 12);
        fileLabelPanel.add(currentFileLabel, c);

        // Create filePanel
        JPanel filePanel = createInputPanel(fileLabelPanel, "File to Submit", false);

        // Add viewCurrentButton to filePanel
        setPointerCursor(viewCurrentButton);
        c = setConstraints(0, 0, 1, 1);
        changeSize(viewCurrentButton, 16);
        filePanel.add(viewCurrentButton, c);

        return filePanel;
    }

    private <T> JPanel createComboBoxWithRefreshPanel(JComboBox<T> comboBox, JButton refreshButton, String headerText) {
        JPanel inputPanel = createComboBoxPanel(comboBox, headerText);
        GridBagConstraints c;

        // Add refreshButton to inputPanel
        int y = 1;
        c = setConstraints(0, 0, 1, y);
        setAllInsets(c, 0);
        Icon icon = styleRefreshButton(refreshButton);
        //refreshButton.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
        inputPanel.add(refreshButton, c);

        return inputPanel;
    }

    private <T> JPanel createComboBoxWithButtonsPanel(JComboBox<T> comboBox, JButton refreshButton, DetailsPanel2 detailsPanel, JRadioButton radioButton1, JRadioButton radioButton2, String headerText) {
        JPanel inputPanel = createComboBoxWithRefreshPanel(comboBox, refreshButton, headerText);
        GridBagConstraints c;
        int y = 2;

        // Add detailsPanel
        c = setConstraints(1, 0, 0, y++);
        c.gridwidth = GridBagConstraints.REMAINDER;
        //c.fill = GridBagConstraints.HORIZONTAL;
        //changeSize(radioButton1, 14);
        inputPanel.add(detailsPanel, c);

        // Add radio buttons to a group
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioButton1);
        buttonGroup.add(radioButton2);

        // Create buttonPanel
        JPanel buttonPanel = new JPanel(new GridBagLayout());

        // Add radioButton1 to buttonPanel
        c = setConstraints(0.5, 0, 0, 0);
        setPointerCursor(radioButton1);
        changeSize(radioButton1, 14);
        unBoldFont(radioButton1);
        buttonPanel.add(radioButton1, c);

        // Add radioButton2 to buttonPanel
        c = setConstraints(0.5, 0, 1, 0);
        setPointerCursor(radioButton2);
        changeSize(radioButton2, 14);
        unBoldFont(radioButton2);
        buttonPanel.add(radioButton2, c);

        // Add buttonPanel to inputPanel
        c = setConstraints(0, 0, 0, y);
        c.insets = new Insets(5, 0, 0, 0);
        inputPanel.add(buttonPanel, c);

        return inputPanel;
    }

    private void populateGui() {
        // Visuals for combo boxes
        courseBox.setBackground(Color.WHITE);
        assignmentBox.setBackground(Color.WHITE);
        problemBox.setBackground(Color.WHITE);

        currentFileLabel.setBackground(Color.WHITE);

        // Set default renderer for combo boxes
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        renderer.setBackground(Color.WHITE);
        renderer.setOpaque(true);
        courseBox.setRenderer(renderer);
        assignmentBox.setRenderer(renderer);
        problemBox.setRenderer(renderer);

        // Initialize clean models with a placeholder
        setModel(courseBox, List.of(PLACEHOLDER), false);
        setModel(assignmentBox, List.of(PLACEHOLDER), false);
        setModel(problemBox, List.of(PLACEHOLDER), false);

        // Appropriately enable/disable interactive elements
        toggleCourseBox(false);
        toggleAssignmentBox(false);
        toggleProblemBox(false);
    }

    /** Function used to fill a drop-down menu with options
     *   - params:
     *       - box: drop-down menu being used to store options (must be courseBox, assignmentBox, or problemBox)
     *       - items: a list of items used as values for the drop-down menu
     *       - enable: should the drop-down menu be enabled or not
     *   - return:
     *       - void (none)
     */
    /**
     * Function used to fill a drop-down menu with options.
     *
     * @param box drop-down menu being used to store options (must be courseBox, assignmentBox, or problemBox)
     * @param items a list of items used as values for the drop-down menu
     * @param enable should the drop-down menu be enabled or not
     * @param <T> must be courseBox, assignmentBox, or problemBox
     */
    private <T> void setModel(JComboBox<T> box, List<String> items, boolean enable) {
        isPopulating = true;
        try {
            DefaultComboBoxModel<T> model = new DefaultComboBoxModel<>();

            for (String item : items) {
                T value;

                if (box == courseBox) {
                    value = (T) new CourseItem("", item);
                } else if (box == assignmentBox) {
                    value = (T) new AssignmentItem("", item);
                    allAssignments.setEnabled(enable);
                    upcomingAssignments.setEnabled(enable);
                } else if (box == problemBox) {
                    value = (T) new ProblemItem("", item);
                    allProblems.setEnabled(enable);
                    uncompletedProblems.setEnabled(enable);
                } else {
                    value = (T) (Object) item;
                }

                model.addElement(value);
            }

            box.setModel(model);
            box.setSelectedIndex(0);
            box.setEnabled(enable);
        } finally {
            isPopulating = false;
        }
    }

    /**
     * Sets action listeners for user inputs.
     */
    private void setupEventHandlers() {
        handlers_course();
    }

    private void handlers_course() {
        SubmitWindow submitWindow = this;
        courseBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;

                // If the user's selection does not change, return
                if (courseBox.getSelectedIndex() == selectedCourse) return;

                // User selected initial box with no value
                if (courseBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    setModel(assignmentBox, List.of(PLACEHOLDER), false);
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    return;
                }

                // User chose a valid course
                CourseItem selectedCourse = (CourseItem) courseBox.getSelectedItem();
                appendResult("Selected course: " + selectedCourse);
                appendResult("");
                appendResult("Loading assignments for selected course…");
                Globals.sessionHandler.populateAssignments(submitWindow, selectedCourse); // Load assignments for selected course
            }
        });
    }
}
