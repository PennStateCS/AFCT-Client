package submission;

import javax.swing.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Color;

import file.XMLCodec;
import gui.environment.Environment;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.time.LocalDateTime;

public class SubmitDialog extends JDialog implements ActionListener {
    private Environment env;
    private JTextField email;
    private JPasswordField password;
    private JButton signInButton;
    private JButton workingButton;
    private JButton submitButton;
    private JTextArea result;
    private JComboBox<String> courseBox;
    private JComboBox<String> assignmentBox;
    private JComboBox<String> problemBox;
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
        // Initialize
        this.setEnv(env);
        server.setText("http://localhost");
        port.setText("3000");
        email.setText("student@example.com");
        password.setText("password123");
        path.setText("JFLAP File");
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
        courseBox.setModel(new DefaultComboBoxModel<>(new String[]{PLACEHOLDER}));
        assignmentBox.setModel(new DefaultComboBoxModel<>(new String[]{PLACEHOLDER}));
        problemBox.setModel(new DefaultComboBoxModel<>(new String[]{PLACEHOLDER}));

        assignmentBox.setEnabled(false);
        problemBox.setEnabled(false);
        submitButton.setEnabled(false);
        workingButton.setEnabled(false);
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
                appendResult("Authenticating…");

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() {
                        try {
                            client = new AFCTClient(serverUrl + ":" + portText);
                            token = client.login(userEmail, userPassword);
                            if (token != null && !token.isBlank()) {
                                publish("Authentication Success.");
                                publish("");
                                publish("Loading courses…");

                                // Load courses on worker thread
                                List<Map<String, Object>> fetched = client.getCourses(userEmail);
                                courses = fetched;
                                List<String> names = new ArrayList<>(fetched.size() + 1);
                                names.add(PLACEHOLDER);
                                for (Map<String, Object> course : fetched) {
                                    names.add((String) course.get("name"));
                                }

                                SwingUtilities.invokeLater(() -> {
                                    setModel(courseBox, names, true);
                                    setModel(assignmentBox, List.of(PLACEHOLDER), false);
                                    setModel(problemBox, List.of(PLACEHOLDER), false);
                                });

                                // Display number of courses loaded
                                int numCourses = courses.size();
                                if (numCourses == 1) { publish(String.format("Loaded %s course", numCourses)); }
                                else { publish(String.format("Loaded %s courses", numCourses)); }
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
                        updateWorkingEnabled();
                        updateSubmitEnabled();
                    }
                }.execute();
            }
        });

        courseBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;
                if (courseBox.getSelectedIndex() <= 0) {
                    // Reset dependents
                    setModel(assignmentBox, List.of(PLACEHOLDER), false);
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    updateWorkingEnabled();
                    updateSubmitEnabled();
                    return;
                }
                appendResult("Selected course: " + courseBox.getSelectedItem());
                appendResult("");
                appendResult("Loading assignments for selected course…");
                loadAssignmentsAsync();
            }
        });

        assignmentBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isPopulating) return;
                if (assignmentBox.getSelectedIndex() <= 0) {
                    setModel(problemBox, List.of(PLACEHOLDER), false);
                    updateWorkingEnabled();
                    updateSubmitEnabled();
                    return;
                }
                appendResult("Selected assignment: " + assignmentBox.getSelectedItem());
                appendResult("");
                appendResult("Loading problems for selected assignment…");
                loadProblemsAsync();
            }
        });

        allAssignments.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                appendResult("Reloading assignments...");
                loadAssignmentsAsync();
            }
        });

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
                if (isPopulating) return;
                if (problemBox.getSelectedIndex() <= 0) {
                    updateWorkingEnabled();
                    updateSubmitEnabled();
                    return;
                }
                appendResult("Selected problem: " + problemBox.getSelectedItem());
                appendResult("");
                updateWorkingEnabled(); // Not needed for other boxes because this one cannot be filled without the others
                updateSubmitEnabled(); // Not needed for other boxes because this one cannot be filled without the others
            }
        });

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                int choice = fileChooser.showOpenDialog(mainForm);
                if (choice == JFileChooser.APPROVE_OPTION) {
                    selectedFile = fileChooser.getSelectedFile();
                    path.setText(selectedFile.getName());
                    appendResult("Selected file: " + selectedFile.getAbsolutePath());
                    appendResult("");
                    updateSubmitEnabled();
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

                setCurrJFLAP(email.getText(), String.valueOf(problemBox.getSelectedItem()));
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
                    JOptionPane.showMessageDialog(mainForm, "No file selected", "Please select a file to submit.", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                appendResult("Submitting…");
                submitButton.setEnabled(false);

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() {
                        try {
                            String assignmentId = getSelectedId(assignments, assignmentBox.getSelectedIndex() - 1);
                            String problemId = getSelectedId(problems, problemBox.getSelectedIndex() - 1);

                            Map<String, Object> submission = client.createSubmission(
                                    assignmentId,
                                    problemId,
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

    private void loadAssignmentsAsync() {
        assignmentBox.setEnabled(false);
        problemBox.setEnabled(false);
        setModel(assignmentBox, List.of(PLACEHOLDER), false);
        setModel(problemBox, List.of(PLACEHOLDER), false);

        final int courseIdx = courseBox.getSelectedIndex() - 1; // adjust for placeholder
        new SwingWorker<Void, String>() {
            List<String> titles;

            @Override
            protected Void doInBackground() {
                try {
                    String courseId = getSelectedId(courses, courseIdx);
                    assignments = client.getAssignments(courseId);
                    titles = new ArrayList<>();
                    titles.add(PLACEHOLDER);

                    // Get users choice
                    String selectedChoice = allAssignments.isSelected() ? allAssignments.getText() : upcomingAssignments.getText();

                    // Load all assignments
                    if (selectedChoice.equals("All Assignments")) {
                        for (Map<String, Object> assignment : assignments) {
                            titles.add((String) assignment.get("title"));
                        }
                    }

                    // Load upcoming assignments
                    else {
                        // Get current time for default boxes
                        LocalDateTime currTime = LocalDateTime.now();

                        // Get assignments based on default parameters
                        for (Map<String, Object> assignment : assignments)
                        {
                            // Get the date this assignment is due
                            String dueDateStr = (String) assignment.get("dueDate");
                            assert dueDateStr != null;

                            dueDateStr = dueDateStr.charAt(dueDateStr.length() - 1) == 'Z' ? dueDateStr.substring(0, dueDateStr.length() - 1) : dueDateStr;

                            if (LocalDateTime.parse(dueDateStr).isAfter(currTime)) { titles.add((String) assignment.get("title")); }
                        }
                    }

                    // Display number of assignments loaded
                    int numTotalAssignments = assignments.size();
                    int numDisplayAssignments = titles.toArray().length-1;
                    publish(String.format("Loaded %s total %s", numTotalAssignments, numTotalAssignments == 1 ? "assignment" : "assignments"));
                    publish(String.format("Displaying %s %s", numDisplayAssignments, numDisplayAssignments == 1 ? "assignment" : "assignments"));
                } catch (IOException ex) {
                    publish("Failed to load assignments: " + ex.getMessage());
                    titles = List.of(PLACEHOLDER);
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
                setModel(assignmentBox, titles, true);
                updateWorkingEnabled();
                updateSubmitEnabled();
            }
        }.execute();
    }

    private void loadProblemsAsync() {
        problemBox.setEnabled(false);
        setModel(problemBox, List.of(PLACEHOLDER), false);

        final int assignmentIdx = assignmentBox.getSelectedIndex() - 1; // adjust for placeholder
        new SwingWorker<Void, String>() {
            List<String> titles;

            @Override
            protected Void doInBackground() {
                try {
                    String assignmentId = getSelectedId(assignments, assignmentIdx);
                    problems = client.getProblems(assignmentId);
                    titles = new ArrayList<>();
                    titles.add(PLACEHOLDER);

                    // Get problems based on default parameters
                    for (Map<String, Object> problem : problems) {
                        titles.add((String) problem.get("title"));
                    }

                    // Display number of problems loaded
                    int numProblems = problems.size();
                    if (numProblems == 1) { publish(String.format("Loaded %s problem", numProblems)); }
                    else { publish(String.format("Loaded %s problems", numProblems)); }
                } catch (IOException ex) {
                    publish("Failed to load problems: " + ex.getMessage());
                    titles = List.of(PLACEHOLDER);
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
                setModel(problemBox, titles, true);
                updateWorkingEnabled();
                updateSubmitEnabled();
            }
        }.execute();
    }

    private void setModel(JComboBox<String> box, List<String> items, boolean enable) {
        isPopulating = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(items.toArray(new String[0]));
            box.setModel(model);
            if (!items.isEmpty()) {
                box.setSelectedIndex(0); // placeholder selected
            }
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

    private void updateWorkingEnabled() {
        boolean ready =
                client != null && client.isAuthenticated() &&
                        assignmentBox.isEnabled() && assignmentBox.getSelectedIndex() > 0 &&
                        problemBox.isEnabled() && problemBox.getSelectedIndex() > 0;

        // Enable working button (if ready)
        workingButton.setEnabled(ready);
    }

    private void updateSubmitEnabled() {
        boolean ready =
                client != null && client.isAuthenticated() &&
                        assignmentBox.isEnabled() && assignmentBox.getSelectedIndex() > 0 &&
                        problemBox.isEnabled() && problemBox.getSelectedIndex() > 0;

        // Set current submit file to JFLAP file, when file is null
        if (selectedFile == null && ready) { setCurrJFLAP(email.getText(), String.valueOf(problemBox.getSelectedItem())); }

        // Enable submit button (if ready)
        submitButton.setEnabled(ready);
    }

    private String getSelectedId(List<Map<String, Object>> items, int index) {
        if (items != null && index >= 0 && index < items.size()) {
            Object id = items.get(index).get("id");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    @Override
    public void actionPerformed(ActionEvent e) { }
}