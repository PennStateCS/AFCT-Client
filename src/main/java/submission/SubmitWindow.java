package submission;

import file.EncodeException;
import file.XMLCodec;
import gui.Globals;
import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.Universe;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import static gui.Globals.*;
import static submission.LoginWindow.*;
import static submission.SessionHandler.*;
import static submission.SessionHandler.defaultPassword;
import static submission.SubmitWindow.ComboBoxTarget.*;

public class SubmitWindow extends JFrame implements SubmissionGUI {
    private Environment environment;
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
    private JTextPane result;
    private String resultText = "";
    private JScrollPane resultScrollPane;

    private JTextPane feedbackTextPane;
    private JLabel feedbackLabel;

    private JScrollPane scrollPane;

    private String feedbackPrefix = "Feedback: ";


    // Tracking for optimization
    private String selectedCourseID = null;
    private String selectedAssignmentID = null;
    private String selectedProblemID = null;

    // Placeholder for combo boxes
    public static final String PLACEHOLDER = "— Select —";

    // Event guarding
    public volatile boolean isPopulating = false;
    private boolean populateCoursesOnceLoggedIn = false;

    public SubmitWindow(Environment environment) {
        this.environment = environment;

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

        result = new JTextPane();
        result.setContentType("text/html");
        resultScrollPane = new JScrollPane(result);

        feedbackTextPane = new JTextPane();
        feedbackTextPane.setContentType("text/html");
        feedbackLabel = new JLabel("<html> </html>");

        setupGui();
        populateGui();
        setupEventHandlers();

        if (sessionHandler.loggedIn) {
            sessionHandler.populateCourses(this);
        } else {
            populateCoursesOnceLoggedIn = true;
        }

        scrollPane = new JScrollPane(contentPane);

        this.getContentPane().add(scrollPane);
        this.setVisible(false);
    }

    public void displaySubmitWindow() {
        // Safety measure so that GUI doesn't stop working if courseBox is disabled when it shouldn't be
        toggleCourseBox(true);

        if (sessionHandler.loggedIn) {
            this.refreshDialog();
            if (populateCoursesOnceLoggedIn || courseBox.getItemCount() <= 1) {
                populateCoursesOnceLoggedIn = false;
                sessionHandler.populateCourses(this);
            }
            this.setVisible(true);
            this.toFront();
        } else {
            sessionHandler.displayLoginThenSubmission(this);
        }
    }

    @Override
    public void refreshDialog() {
        EnvironmentFrame frame = Universe.frameForEnvironment(this.environment);
        this.setTitle(frame.getDescription() + " - Submit");
        currentFileLabel.setText(frame.getDescription());
    }

    public void appendResult(String line) {
        //resultText += (line.endsWith("\n") ? line : (line + "\n"));
        resultText += (line.endsWith("<br>") ? line : (line + "<br>"));
        result.setText(resultText);
        result.setCaretPosition(result.getDocument().getLength());

        feedbackTextPane.setText(line);
        feedbackTextPane.setCaretPosition(feedbackTextPane.getDocument().getLength());

        if (line.startsWith(feedbackPrefix)) {
            feedbackLabel.setText("<html>" + line.substring(feedbackPrefix.length() - 1, line.length()) + "</html>");
        } else {
            feedbackLabel.setText("<html>" + line + "</html>");
        }
    }

    public void toggleSubmitButton(boolean enabled) {
        submitButton.setEnabled(enabled);
    }

    public void toggleCourseBox(boolean enabled) {
        courseBox.setEnabled(enabled);
        courseRefreshButton.setEnabled(enabled);
    }

    public void toggleAssignmentBox(boolean enabled) {
        assignmentBox.setEnabled(enabled);
        assignmentRefreshButton.setEnabled(enabled);
        if (enabled && assignmentBox.getSelectedIndex() > 0) {
            assignmentDetailsPanel.toggle(true);
        }
        if (!enabled) {
            assignmentDetailsPanel.toggle(false);
        }
        allAssignments.setEnabled(enabled);
        upcomingAssignments.setEnabled(enabled);
    }

    public void toggleProblemBox(boolean enabled) {
        problemBox.setEnabled(enabled);
        problemRefreshButton.setEnabled(enabled);
        if (enabled && problemBox.getSelectedIndex() > 0) {
            problemDetailsPanel.toggle(true);
        }
        if (!enabled) {
            problemDetailsPanel.toggle(false);
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
        contentPane.add(createComboBoxWithTopRadioButtons(assignmentBox, assignmentRefreshButton, assignmentDetailsPanel, allAssignments, upcomingAssignments, "Assignment"), c);
        c.insets = new Insets(vrtInset-5, hozInset, 0, hozInset);
        c.gridy = y++;
        contentPane.add(createComboBoxWithTopRadioButtons(problemBox, problemRefreshButton, problemDetailsPanel, allProblems, uncompletedProblems, "Problem"), c);

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

        addFeedbackSection(c, y, vrtInset, hozInset);
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

    private void addFeedbackSection(GridBagConstraints c, int y, int vrtInset, int hozInset) {
        // Add result to contentPane
        result.setBorder(new LineBorder(new Color(210, 210, 210)));
        c.gridy = y++;
        //contentPane.add(createInputPanel(resultScrollPane, "Result", false), c);

        // Add feedbackTextPane to contentPane
        c.insets = new Insets(vrtInset, hozInset, vrtInset, hozInset);
        feedbackTextPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        feedbackTextPane.setBackground(Color.WHITE);
        //contentPane.add(createInputPanel(feedbackTextPane, "Feedback", false), c);


        // TODO: maybe eventually switch to feedbackTextPane so that the feedback message can be copy and pasted
        // Stylize feedbackLabel
        feedbackLabel.setBackground(Color.WHITE);
        //changeSize(feedbackLabel, 14);
        changeSize(feedbackLabel, 16);
        unBoldFont(feedbackLabel);

        // create feedbackLabelPanel
        JPanel feedbackLabelPanel = new JPanel(new GridBagLayout());
        feedbackLabelPanel.setBackground(Color.WHITE);
        feedbackLabelPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        // Add feedbackLabel to feedbackLabelPanel
        GridBagConstraints c2 = setConstraints(1, 1, 0, 0);
        c2.insets = new Insets(6, 12, 6, 12);
        //feedbackLabelPanel.add(feedbackLabel, c);
        c2.insets = new Insets(10, 12, 10, 12);
        feedbackLabelPanel.add(feedbackLabel, c2);

        // Add feedbackLabelPanel to contentPane
        c.gridy = y++;
        contentPane.add(createInputPanel(feedbackLabelPanel, "Feedback", false), c);
    }

    private void addRefreshButton(JPanel inputPanel, JButton refreshButton, int y) {
        // Add refreshButton to inputPanel
        GridBagConstraints c;
        c = setConstraints(0, 0, 1, y);
        setAllInsets(c, 0);
        Icon icon = styleRefreshButton(refreshButton);
        //refreshButton.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
        inputPanel.add(refreshButton, c);
    }

    private <T> JPanel createComboBoxWithRefreshPanel(JComboBox<T> comboBox, JButton refreshButton, String headerText) {
        JPanel inputPanel = createComboBoxPanel(comboBox, headerText);
        GridBagConstraints c;

        // Add refreshButton to inputPanel
        int y = 1;
        addRefreshButton(inputPanel, refreshButton, y);

        return inputPanel;
    }

    private <T> JPanel createComboBoxWithBottomRadioButtons(JComboBox<T> comboBox, JButton refreshButton, DetailsPanel2 detailsPanel, JRadioButton radioButton1, JRadioButton radioButton2, String headerText) {
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


    private <T> JPanel createComboBoxWithTopRadioButtons(JComboBox<T> comboBox, JButton refreshButton, DetailsPanel2 detailsPanel, JRadioButton radioButton1, JRadioButton radioButton2, String headerText) {
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // Create headerLabel
        JLabel headerLabel = new JLabel(headerText);
        changeSize(headerLabel, 16);
        // Add headerLabel to inputPanel
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        c.insets = new Insets(0, 0, 0, 0);
        inputPanel.add(headerLabel, c);

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
        c = setConstraints(0, 0, 0, y++);
        //c.insets = new Insets(5, 0, 0, 0);
        // TODO: maybe have this span two columns cause of refresh button?
        //  - like the detailsPanel? i.e.:
        //  c.gridwidth = GridBagConstraints.REMAINDER;
        inputPanel.add(buttonPanel, c);

        // Add comboBox to inputPanel
        c = setConstraints(1, 1, 0, y++, GridBagConstraints.LINE_START);
        changeSize(comboBox, 16);
        inputPanel.add(comboBox, c);

        // Add refreshButton to inputPanel
        addRefreshButton(inputPanel, refreshButton, y-1);

        // Add detailsPanel
        c = setConstraints(1, 0, 0, y++);
        c.gridwidth = GridBagConstraints.REMAINDER;
        //c.fill = GridBagConstraints.HORIZONTAL;
        //changeSize(radioButton1, 14);
        inputPanel.add(detailsPanel, c);

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
        toggleCourseBox(true);
        toggleAssignmentBox(false);
        toggleProblemBox(false);
        toggleSubmitButton(false);

        upcomingAssignments.setSelected(true);
        uncompletedProblems.setSelected(true);

        refreshDialog();
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
        handlers_assignment();
        handlers_problem();
        handlers_file();
        handlers_submit();
    }

    private void handlers_course() {
        SubmitWindow submitWindow = this;
        courseBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;

                // If the user's selection does not change, return
                CourseItem selectedCourse = (CourseItem) courseBox.getSelectedItem();
                if (selectedCourse == null) return;
                if (Objects.equals(selectedCourse.id, selectedCourseID)) return;

                selectedCourseID = selectedCourse.id;
                selectedAssignmentID = null;
                selectedProblemID = null;
                assignmentDetailsPanel.disableDetailsPanel();
                problemDetailsPanel.disableDetailsPanel();
                toggleSubmitButton(false);

                // User selected initial box with no value
                if (courseBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    setModel(assignmentBox, List.of(PLACEHOLDER), false);
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    return;
                }

                // User chose a valid course
                //appendResult("Selected course: " + selectedCourse);
                //appendResult("");
                //appendResult("Loading assignments for selected course…");
                // Load assignments for selected course
                Globals.sessionHandler.populateAssignments(submitWindow, selectedCourse);
            }
        });

        courseRefreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedCourseID = null;
                selectedAssignmentID = null;
                selectedProblemID = null;
                toggleSubmitButton(false);
                //appendResult("Re-loading all courses...");
                Globals.sessionHandler.populateCourses(submitWindow, true);
            }
        });
    }

    private void handlers_assignment() {
        SubmitWindow submitWindow = this;
        assignmentBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;

                // If the user's selection does not change, return
                AssignmentItem selectedAssignment = (AssignmentItem) assignmentBox.getSelectedItem();
                if (selectedAssignment == null) return;
                if (Objects.equals(selectedAssignment.id, selectedAssignmentID)) return;

                selectedAssignmentID = selectedAssignment.id;
                selectedProblemID = null;
                problemDetailsPanel.disableDetailsPanel();
                toggleSubmitButton(false);

                // User selected initial box with no value
                if (assignmentBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    assignmentDetailsPanel.disableDetailsPanel();
                    return;
                }

                // User chose a valid assignment
                assignmentDetailsPanel.setDetailsText(selectedAssignment.description);
                assignmentDetailsPanel.toggle(true);
                //appendResult("Selected assignment: " + assignmentBox.getSelectedItem());
                //appendResult("");
                //appendResult("Loading problems for selected assignment…");
                // Load problems for selected assignment
                Globals.sessionHandler.populateProblems(submitWindow, selectedAssignment);
            }
        });

        // Assignment refresh button
        assignmentRefreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedAssignmentID = null;
                selectedProblemID = null;
                toggleSubmitButton(false);
                //appendResult("Re-loading all assignments...");
                CourseItem selectedCourse = (CourseItem) courseBox.getSelectedItem();
                if (selectedCourse != null) {
                    Globals.sessionHandler.populateAssignments(submitWindow, selectedCourse, true);
                }
            }
        });

        // "All Assignments" button (radio button 1 of 2)
        allAssignments.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedAssignmentID = null;
                selectedProblemID = null;
                toggleSubmitButton(false);
                //appendResult("Loading all assignments...");
                CourseItem selectedCourse = (CourseItem) courseBox.getSelectedItem();
                if (selectedCourse != null) {
                    Globals.sessionHandler.populateAssignments(submitWindow, selectedCourse);
                }
            }
        });

        // "Upcoming Assignments" button (radio button 2 of 2)
        upcomingAssignments.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedAssignmentID = null;
                selectedProblemID = null;
                toggleSubmitButton(false);
                //appendResult("Loading upcoming assignments...");
                CourseItem selectedCourse = (CourseItem) courseBox.getSelectedItem();
                if (selectedCourse != null) {
                    Globals.sessionHandler.populateAssignments(submitWindow, selectedCourse);
                }
            }
        });
    }

    private void handlers_problem() {
        SubmitWindow submitWindow = this;
        problemBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;

                // If the user's selection does not change, return
                ProblemItem selectedProblem = (ProblemItem) problemBox.getSelectedItem();
                if (selectedProblem == null) return;
                if (Objects.equals(selectedProblem.id, selectedProblemID)) return;
                selectedProblemID = selectedProblem.id;

                // User selected initial box with no value
                if (problemBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    toggleSubmitButton(false);
                    problemDetailsPanel.disableDetailsPanel();
                    return;
                }


                // User chose a valid assignment
                problemDetailsPanel.setDetailsText(selectedProblem.description);
                problemDetailsPanel.toggle(true);
                toggleSubmitButton(true);
                //appendResult("Selected problem: " + parseProblemTitle(selectedProblem.toString()));
                //appendResult("");
            }
        });

        // Problem refresh button
        problemRefreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedProblemID = null;
                //appendResult("Re-loading all problems...");
                AssignmentItem selectedAssignment = (AssignmentItem) assignmentBox.getSelectedItem();
                if (selectedAssignment != null) {
                    Globals.sessionHandler.populateProblems(submitWindow, selectedAssignment, true);
                }
            }
        });

        // "All Problems" button (radio button 1 of 2)
        allProblems.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedProblemID = null;
                toggleSubmitButton(false);
                //appendResult("Loading all problems...");
                AssignmentItem selectedAssignment = (AssignmentItem) assignmentBox.getSelectedItem();
                if (selectedAssignment != null) {
                    Globals.sessionHandler.populateProblems(submitWindow, selectedAssignment);
                }
            }
        });

        // "Uncompleted Problems" button (radio button 2 of 2)
        uncompletedProblems.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectedProblemID = null;
                toggleSubmitButton(false);
                //appendResult("Loading uncompleted problems...");
                AssignmentItem selectedAssignment = (AssignmentItem) assignmentBox.getSelectedItem();
                if (selectedAssignment != null) {
                    Globals.sessionHandler.populateProblems(submitWindow, selectedAssignment);
                }
            }
        });
    }

    private void handlers_file() {
        SubmitWindow submitWindow = this;
        viewCurrentButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFrame automataFrame = Universe.frameForEnvironment(environment);

                // TODO: should this be LEFT OR RIGHT?
                //  - maybe should add an option to the prefs menu that lets you choose any Position?
                positionFrameNearWindow(automataFrame, Position.LEFT, submitWindow);
//                automataFrame.setLocationRelativeTo(submitWindow);
//
//                int newX = submitWindow.getX() + submitWindow.getWidth();
//                int newY = submitWindow.getY();
//                automataFrame.setLocation(newX, newY);



                //automataFrame.setLocationRelativeTo(automataFrame);




                Universe.frameForEnvironment(environment).toFront();
            }
        });
    }

    private void handlers_submit() {
        SubmitWindow submitWindow = this;
        submitButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                submitButton.setEnabled(false);
                AFCTClient client = Globals.sessionHandler.getClient();

                if (client == null) {
                    // TODO - maybe put the fact that the user was trying to submit into a queue, then once logged in again, auto submit
                    return;
                }
                if (assignmentBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(contentPane, "No assignment selected", "Please select an assignment to submit.", JOptionPane.WARNING_MESSAGE);
                    submitButton.setEnabled(true);
                    return;
                }
                if (problemBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(contentPane, "No problem selected", "Please select a problem to submit.", JOptionPane.WARNING_MESSAGE);
                    submitButton.setEnabled(true);
                    return;
                }

                // Automatically select the current file
                File selectedFile = createTempFile();
                if (selectedFile == null) {
                    submitButton.setEnabled(true);
                    return;
                }

                appendResult("Submitting…");

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() {
                        try {
                            AssignmentItem assignment = (AssignmentItem) assignmentBox.getSelectedItem();
                            ProblemItem problem = (ProblemItem) problemBox.getSelectedItem();

                            assert assignment != null;
                            assert problem != null;

                            Map<String, Object> submission = client.createSubmission(
                                    assignment.id,
                                    problem.id,
                                    "Submission from GUI",
                                    selectedFile
                            );

                            //publish("Submission successful!");
                            //publish("Data: " + submission);
                            //publish("ID: " + submission.get("id"));
                            publish("Submitted At: " + submission.get("submittedAt"));
                            //publish("Grade: " + submission.get("grade"));
                            String feedback = (String) submission.get("feedback");
                            boolean correct = (boolean) submission.get("correct");
                            publish(feedbackPrefix + colorMessage(feedback, correct));
                            if (correct) {
                                // Keep submitted problem selected if the "All Problems" radio button is selected
                                if (allProblems.isSelected()) {
                                    Globals.sessionHandler.populateProblems(submitWindow, assignment, true, true);
                                } else {
                                    selectedProblemID = null;
                                    Globals.sessionHandler.populateProblems(submitWindow, assignment, true);
                                }
                            }
                        } catch (IOException ex) {
                            publish(colorHTMLErrorMessage("Submission failed: " + ex.getMessage()));
                        }
                        //publish("");
                        return null;
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (String s : chunks) appendResult(s);
                    }

                    @Override
                    protected void done() {
                        submitButton.setEnabled(true);
                    }
                }.execute();
            }
        });
    }

    private File createTempFile() {
        // Try to create a temp file for the file that the user was working with
        try{
            // Create a temp file and encode the user's JFLAP program as the file
            File f = File.createTempFile("jflap", ".jff");
            XMLCodec x = new XMLCodec();
            x.encode(this.environment.getObject(), f, null);
            return f;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error creating temp file: " + e.getMessage());
        } catch (EncodeException e) {
            JOptionPane.showMessageDialog(null, "Error saving temp file: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parser for the problem title, created due to the check mark
     *
     * @param title the title of the selected problem that is being parsed
     * @return (String): the title with the check mark removed
     */
    private String parseProblemTitle(String title) {
        String parsedTitle = title.stripTrailing();
        parsedTitle = parsedTitle.endsWith(" \u2714") ? parsedTitle.substring(0, parsedTitle.length()-2) : parsedTitle;
        return parsedTitle;
    }
}
