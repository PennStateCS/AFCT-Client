package submission;

import javax.swing.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Color;

import file.XMLCodec;
import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.Universe;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.prefs.Preferences;

public class SubmitDialog extends JFrame implements ActionListener {
    private Environment env;
    private JTextField email;
    private JPasswordField password;
    private JButton signInButton;
    private JButton workingButton;
    private JButton submitButton;
    private JTextArea result;
    private JComboBox<CourseItem> courseBox;
    private JComboBox<AssignmentItem> assignmentBox;
    private JComboBox<ProblemItem> problemBox;
    private JButton browseButton;
    private JLabel path;
    private JTextField server;
    private JTextField port;
    private JPanel mainForm;
    private JRadioButton allAssignments;
    private JRadioButton upcomingAssignments;
    private JRadioButton allProblems;
    private JRadioButton uncompletedProblems;

    private AFCTClient client;
    private String token;
    private File selectedFile;
    private List<Map<String, Object>> courses;
    private List<Map<String, Object>> assignments;
    private List<Map<String, Object>> problems;
    private String resultText;

    // Default values
    private final String defaultServer = "http://localhost";
    private final String defaultPort = "3000";
    private final String defaultEmail = "student@example.com";
    private final String defaultPassword = "password123";

    // Preferences
    private final String PREF_SERVER = "server";
    private final String PREF_PORT = "port";
    private final String PREF_EMAIL = "email";
    private final String PREF_PASSWORD = "password";
    private final String PREF_HOMEWORK = "homework";
    private final String PREF_PROBLEM = "problem";

    // Event guarding
    private volatile boolean isPopulating = false;

    // Placeholder for combo boxes
    private static final String PLACEHOLDER = "— Select —";

    public SubmitDialog(Environment env)
    {
        initializeComponents(env);
        setupEventHandlers();
    }

    private void initializeComponents(Environment env)
    {
        Preferences prefs = Preferences.userNodeForPackage(submission.LegacySubmitDialog.class);

        // Initialize
        this.setEnv(env);
        server.setText(prefs.get(PREF_SERVER, defaultServer));
        port.setText(prefs.get(PREF_PORT, defaultPort));
        email.setText(prefs.get(PREF_EMAIL, defaultEmail));
        password.setText(prefs.get(PREF_PASSWORD, defaultPassword));
        path.setText("No File Selected");
        browseButton.setEnabled(false);
        resultText = "";

        // Visuals for combo boxes
        courseBox.setBackground(Color.WHITE);
        assignmentBox.setBackground(Color.WHITE);
        problemBox.setBackground(Color.WHITE);

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

        // Appropriately enable/disable buttons
        assignmentBox.setEnabled(false);
        allAssignments.setEnabled(false);
        upcomingAssignments.setEnabled(false);

        problemBox.setEnabled(false);
        submitButton.setEnabled(false);
        workingButton.setEnabled(false);
    }

    private void savePreferences(String serverUrl, String portText, String userEmail, String userPassword) {
        Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);
        prefs.put(PREF_SERVER, serverUrl);
        prefs.put(PREF_PORT, portText);
        prefs.put(PREF_EMAIL, userEmail);
        prefs.put(PREF_PASSWORD, userPassword);
    }

    /*
    * Sets the selected file to the JFLAP file that the user was working on
    * formatted as: [email]_[assignment].jff
    * @param email (String): the users email used for the file name
    * @param assignment (String): the assignment name used for the file name
     */
    private void setCurrJFLAP(String email, String assignment)
    {
        assignment = assignment.replaceAll("[\\s\\\\/:*?\"<>|]", ""); // Remove illegal filename characters (and whitespace)
        String fileName = email.split("@")[0] + "_" + assignment; // [email]_[assignment] (no whitespace in assignment)

        // Try to create a temp file for the file that the user was working with
        try{
            // Create a temp file and encode the user's JFLAP program as the file
            String tmpDir = System.getProperty("java.io.tmpdir");
            File f = new File(tmpDir, fileName + ".jff");
            XMLCodec x = new XMLCodec();
            x.encode(this.env.getObject(), f, null);

            // Set the selected file and notify user
            selectedFile = f;
            path.setText(selectedFile.getName());
            appendResult("Selected file: " + selectedFile.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Error saving temp file: " + e.getMessage());
        }
        appendResult("");
    }

    // Set action listeners for user inputs
    private void setupEventHandlers() {
        signInButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                // SwingWorker
                final String serverUrl = server.getText();
                final String portText = port.getText();
                final String userEmail = email.getText();
                final String userPassword = new String(password.getPassword());

                signInButton.setEnabled(false);
                setModel(courseBox, List.of(PLACEHOLDER), false);
                setModel(assignmentBox, List.of(PLACEHOLDER), false);
                setModel(problemBox, List.of(PLACEHOLDER), false);
                resetSelectedFile();
                updateSelectFileEnabled();
                updateSubmitEnabled();

                appendResult("Authenticating…");

                savePreferences(serverUrl, portText, userEmail, userPassword);

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() {
                        try {
                            // TODO: make it so that you only have to log in once during a session,
                            //  and it will keep you logged in,
                            //  and use the existing session across different submit dialogs
                            client = new AFCTClient(serverUrl + ":" + portText);
                            token = client.login(userEmail, userPassword);
                            if (token != null && !token.isBlank()) {
                                publish("Authentication Success.");
                                publish("");
                                publish("Loading courses…");

                                // Load courses on worker thread
                                courses = client.getCourses(userEmail);

                                // Generate model
                                DefaultComboBoxModel<CourseItem> model = new DefaultComboBoxModel<>();
                                model.addElement(new CourseItem("", PLACEHOLDER));

                                for (Map<String, Object> course : courses) {
                                    model.addElement(new CourseItem(course.get("id").toString(), course.get("name").toString()));
                                }

                                courseBox.setModel(model);
                                courseBox.setEnabled(true);

                                // Display number of courses loaded
                                int numCourses = courses.size();
                                publish(String.format("Loaded %s %s", numCourses, numCourses == 1 ? "course" : "courses"));
                            } else {
                                publish("Authentication failed.");
                            }
                        } catch (IOException ex) {
                            publish("Authentication error: " + ex.getMessage());
                        }
                        publish("");
                        return null;
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (String s : chunks) appendResult(s);
                    }

                    @Override
                    protected void done() {
                        signInButton.setEnabled(true);
                    }
                }.execute();
            }
        });

        courseBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;

                // User selected initial box with no value
                if (courseBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    setModel(assignmentBox, List.of(PLACEHOLDER), false);
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    updateSelectFileEnabled();
                    updateSubmitEnabled();
                    resetSelectedFile();
                    return;
                }

                // User chose a valid course
                appendResult("Selected course: " + courseBox.getSelectedItem());
                appendResult("");
                appendResult("Loading assignments for selected course…");
                loadAssignmentsAsync(); // Load assignments for selected course
            }
        });

        assignmentBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;

                // User selected initial box with no value
                if (assignmentBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    updateSelectFileEnabled();
                    updateSubmitEnabled();
                    resetSelectedFile();
                    return;
                }

                // User chose a valid assignment
                appendResult("Selected assignment: " + assignmentBox.getSelectedItem());
                appendResult("");
                appendResult("Loading problems for selected assignment…");
                loadProblemsAsync(); // Load problems for selected assignments
            }
        });

        // Assignment radio button (1 of 2)
        allAssignments.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                appendResult("Reloading assignments...");
                loadAssignmentsAsync();
            }
        });

        // Assignment radio button (2 of 2)
        upcomingAssignments.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                appendResult("Reloading assignments...");
                loadAssignmentsAsync();
            }
        });

        problemBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProblemItem selectedProblem;

                if (isPopulating) return;

                // User selected initial box with no value
                if (problemBox.getSelectedIndex() <= 0) {
                    // Reset inputs appropriately
                    updateSelectFileEnabled();
                    updateSubmitEnabled();
                    resetSelectedFile();
                    return;
                }

                // User chose a valid assignment
                selectedProblem = (ProblemItem) problemBox.getSelectedItem();
                assert selectedProblem != null;

                appendResult("Selected problem: " + parseProblemTitle(selectedProblem.toString()));
                appendResult("");
                updateSelectFileEnabled(); // Not needed for other boxes because this one cannot be filled without the others
                updateSubmitEnabled(); // Not needed for other boxes because this one cannot be filled without the others
            }
        });

        // Problem radio button (1 of 2)
        allProblems.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                appendResult("Reloading problems...");
                loadProblemsAsync();
            }
        });

        // Problem radio button (1 of 2)
        uncompletedProblems.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                appendResult("Reloading problems...");
                loadProblemsAsync();
            }
        });

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (client == null || !client.isAuthenticated()) {
                    JOptionPane.showMessageDialog(mainForm, "You must be authenticated to add file.", "Authentication Required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (assignmentBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(mainForm, "No assignment selected", "Please select an assignment to add file.", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (problemBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(mainForm, "No problem selected", "Please select a problem to add file.", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                JFileChooser fileChooser = new JFileChooser();
                int choice = fileChooser.showOpenDialog(mainForm);

                if (choice == JFileChooser.APPROVE_OPTION) {
                    selectedFile = fileChooser.getSelectedFile();
                    path.setText(selectedFile.getName());

                    appendResult("Setting file to selected file...");
                    updateSubmitEnabled(selectedFile);
                }
            }
        });

        workingButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (client == null || !client.isAuthenticated()) {
                    JOptionPane.showMessageDialog(mainForm, "You must be authenticated to add file.", "Authentication Required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (assignmentBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(mainForm, "No assignment selected", "Please select an assignment to add file.", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (problemBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(mainForm, "No problem selected", "Please select a problem to add file.", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                appendResult("Setting file to the working file…");
                setCurrJFLAP(email.getText(), parseProblemTitle(problemBox.getSelectedItem().toString()));
            }
        });

        submitButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (client == null || !client.isAuthenticated()) {
                    JOptionPane.showMessageDialog(mainForm, "You must be authenticated to submit.", "Authentication Required", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (assignmentBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(mainForm, "No assignment selected", "Please select an assignment to submit.", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (problemBox.getSelectedIndex() <= 0) {
                    JOptionPane.showMessageDialog(mainForm, "No problem selected", "Please select a problem to submit.", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (selectedFile == null) {
                    JOptionPane.showMessageDialog(mainForm, "No File Selected", "Please select a file to submit.", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                appendResult("Submitting…");
                submitButton.setEnabled(false);

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

                            publish("Submission successful!");
                            publish("Data: " + submission);
                            publish("ID: " + submission.get("id"));
                            publish("Submitted At: " + submission.get("submittedAt"));
                            publish("Grade: " + submission.get("grade"));
                            publish("Feedback: " + submission.get("feedback"));
                        } catch (IOException ex) {
                            publish("Submission failed: " + ex.getMessage());
                        }
                        publish("");
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

    public JPanel getMainPanel() {
        return mainForm;
    }

    public void refreshDialog() {
        // TODO: load current file here
        EnvironmentFrame frame = Universe.frameForEnvironment(this.env);
        this.setTitle(frame.getDescription() + " - Submit");
        updateSelectFileEnabled();
        updateSubmitEnabled();
    }

    private void loadAssignmentsAsync() {
        resetSelectedFile();

        assignmentBox.setEnabled(false);
        allAssignments.setEnabled(false);
        upcomingAssignments.setEnabled(false);

        problemBox.setEnabled(false);
        allProblems.setEnabled(false);
        uncompletedProblems.setEnabled(false);

        setModel(assignmentBox, List.of(PLACEHOLDER), false);
        setModel(problemBox, List.of(PLACEHOLDER), false);

        final CourseItem selectedCourse = (CourseItem) courseBox.getSelectedItem();

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    String courseId;
                    String dueDateStr;
                    LocalDateTime currTime;
                    boolean isUpcoming;
                    int numTotalAssignments;
                    int numDisplayAssignments;

                    assert selectedCourse != null;
                    courseId = selectedCourse.id;

                    // Load assignments on worker thread
                    assignments = client.getAssignments(courseId);

                    // Get current time for default boxes
                    currTime = LocalDateTime.now();

                    // Generate model
                    DefaultComboBoxModel<AssignmentItem> model = new DefaultComboBoxModel<>();
                    model.addElement(new AssignmentItem("",  PLACEHOLDER));

                    // Get assignments based on default parameters
                    for (Map<String, Object> assignment : assignments)
                    {
                        // Get the date this assignment is due
                        dueDateStr = assignment.get("dueDate").toString();
                        assert dueDateStr != null;

                        // Parse the date correctly
                        dueDateStr = dueDateStr.charAt(dueDateStr.length() - 1) == 'Z' ? dueDateStr.substring(0, dueDateStr.length() - 1) : dueDateStr;

                        // Find if the assignment is upcoming
                        isUpcoming = LocalDateTime.parse(dueDateStr).isAfter(currTime);

                        // Add to drop-down menu if applicable
                        if (allAssignments.isSelected() || upcomingAssignments.isSelected() && isUpcoming) {
                            model.addElement((new AssignmentItem(
                                    assignment.get("id").toString(),
                                    assignment.get("title").toString()
                            )));
                        }
                    }

                    // Add model to drop-down menu
                    assignmentBox.setModel(model);

                    // Enable assignment inputs
                    assignmentBox.setEnabled(true);
                    allAssignments.setEnabled(true);
                    upcomingAssignments.setEnabled(true);

                    // Display number of assignments loaded
                    numTotalAssignments = assignments.size();
                    numDisplayAssignments = assignmentBox.getItemCount()-1;
                    publish(String.format("Loaded %s total %s", numTotalAssignments, numTotalAssignments == 1 ? "assignment" : "assignments"));
                    publish(String.format("Displaying %s %s", numDisplayAssignments, numDisplayAssignments == 1 ? "assignment" : "assignments"));
                } catch (IOException ex) {
                    publish("Failed to load assignments: " + ex.getMessage());
                    setModel(assignmentBox, List.of(PLACEHOLDER), true);
                }
                publish("");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendResult(s);
            }

            @Override
            protected void done() {
                updateSelectFileEnabled();
                updateSubmitEnabled();
            }
        }.execute();
    }

    private void loadProblemsAsync() {
        resetSelectedFile();

        problemBox.setEnabled(false);
        allProblems.setEnabled(false);
        uncompletedProblems.setEnabled(false);

        setModel(problemBox, List.of(PLACEHOLDER), false);

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    // Get item and assignment id
                    final AssignmentItem selectedAssignment = (AssignmentItem) assignmentBox.getSelectedItem();

                    assert selectedAssignment != null;

                    problems = client.getProblems(selectedAssignment.id);

                    // Generate model
                    DefaultComboBoxModel<ProblemItem> model = new DefaultComboBoxModel<>();
                    model.addElement(new ProblemItem("", PLACEHOLDER));

                    // Get problems based on default parameters
                    for (Map<String, Object> problem : problems) {
                        Boolean isSolved = (Boolean) problem.get("solved");
                        String instTitle = String.format("%s %s", problem.get("title"), isSolved ? "\u2714" : "");

                        if (allProblems.isSelected() || uncompletedProblems.isSelected() && !isSolved) {
                            model.addElement(new ProblemItem(
                                    problem.get("id").toString(),
                                    instTitle
                            ));
                        }
                    }

                    // Add model to drop-down menu
                    problemBox.setModel(model);

                    // Enable problem inputs
                    problemBox.setEnabled(true);
                    allProblems.setEnabled(true);
                    uncompletedProblems.setEnabled(true);

                    // Display number of problems loaded
                    int totalNumProblems = problems.size();
                    int dispNumProblems = problemBox.getItemCount()-1;

                    publish(String.format("Loaded %s total %s", totalNumProblems, totalNumProblems == 1 ? "problem" : "problems"));
                    publish(String.format("Displaying %s %s", dispNumProblems, dispNumProblems == 1 ? "problem" : "problems"));
                } catch (IOException ex) {
                    publish("Failed to load problems: " + ex.getMessage());
                    setModel(problemBox, List.of(PLACEHOLDER), true);
                }
                publish("");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendResult(s);
            }

            @Override
            protected void done() {
                updateSelectFileEnabled();
                updateSubmitEnabled();
            }
        }.execute();
    }

    /* Function used to fill a drop-down menu with options
    *   - params:
    *       - box: drop-down menu being used to store options (must be courseBox, assignmentBox, or problemBox)
    *       - items: a list of items used as values for the drop-down menu
    *       - enable: should the drop-down menu be enabled or not
    *   - return:
    *       - void (none)
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

    private void setEnv(Environment env) { this.env = env; }

    private void appendResult(String line) {
        resultText += (line.endsWith("\n") ? line : (line + "\n"));
        result.setText(resultText);
        result.setCaretPosition(result.getDocument().getLength());
    }

    private boolean isClientReady() {
        boolean ready = client != null
                && client.isAuthenticated()
                && assignmentBox.isEnabled()
                && assignmentBox.getSelectedIndex() > 0
                && problemBox.isEnabled()
                && problemBox.getSelectedIndex() > 0;
        return ready;
    }

    private void updateSelectFileEnabled() {
        boolean ready = isClientReady();

        // Enable select file buttons (if ready)
        workingButton.setEnabled(ready);
        browseButton.setEnabled(ready);
    }

    private void updateSubmitEnabled() {
        boolean ready = isClientReady();

        ProblemItem selectedProblem = (ProblemItem) problemBox.getSelectedItem();
        assert selectedProblem != null;

        // Set current submit file to JFLAP file when ready
        if (ready) {
            setCurrJFLAP(email.getText(), parseProblemTitle(selectedProblem.title)); }

        // Enable submit button (if ready)
        submitButton.setEnabled(ready);
    }

    private void updateSubmitEnabled(File selectedFile) {
        boolean ready = isClientReady();

        ProblemItem selectedProblem = (ProblemItem) problemBox.getSelectedItem();
        assert selectedProblem != null;


        if (ready) {
            // Set the selected file and notify user
            path.setText(selectedFile.getName());
            appendResult("Selected file: " + selectedFile.getAbsolutePath());
            appendResult("");
        }

        // Enable submit button (if ready)
        submitButton.setEnabled(ready);
    }

    /* Parser for the problem title, created due to the check mark
    *   - params:
    *       - title: the title of the selected problem that is being parsed
    *   - returns:
    *       - parsedTitle (String): the title with the check mark removed
     */
    private String parseProblemTitle(String title) {
        String parsedTitle = title.stripTrailing();
        parsedTitle = parsedTitle.endsWith(" \u2714") ? parsedTitle.substring(0, parsedTitle.length()-2) : parsedTitle;
        return parsedTitle;
    }

    private void resetSelectedFile() {
        browseButton.setEnabled(false);
        workingButton.setEnabled(false);
        path.setText("No File Selected");
        selectedFile = null;
    }

    @Override
    public void actionPerformed(ActionEvent e) { }
}