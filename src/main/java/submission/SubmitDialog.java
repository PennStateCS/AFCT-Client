package submission;

import javax.swing.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import gui.environment.Environment;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.awt.Color;

public class SubmitDialog extends JDialog implements ActionListener {
    private Environment env;
    private JTextField email;
    private JPasswordField password;
    private JButton signInButton;
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
        path.setText("No file selected");
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
                                publish("Loading courses…");
                                // Load courses on worker thread
                                List<Map<String, Object>> fetched = client.getCourses(userEmail);
                                courses = fetched;
                                List<String> names = new ArrayList<>();
                                names.add(PLACEHOLDER);
                                for (Map<String, Object> course : fetched) {
                                    names.add((String) course.get("name"));
                                }
                                SwingUtilities.invokeLater(() -> {
                                    setModel(courseBox, names, true);
                                    setModel(assignmentBox, List.of(PLACEHOLDER), false);
                                    setModel(problemBox, List.of(PLACEHOLDER), false);
                                });
                            } else {
                                publish("Authentication failed.");
                            }
                        } catch (IOException ex) {
                            publish("Authentication error: " + ex.getMessage());
                        }
                        return null;
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (String s : chunks) appendResult(s);
                    }

                    @Override
                    protected void done() {
                        signInButton.setEnabled(true);
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
                    updateSubmitEnabled();
                    return;
                }
                appendResult("Selected course: " + courseBox.getSelectedItem());
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
                    updateSubmitEnabled();
                    return;
                }
                appendResult("Selected assignment: " + assignmentBox.getSelectedItem());
                appendResult("Loading problems for selected assignment…");
                loadProblemsAsync();
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
                    updateSubmitEnabled();
                }
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
                    for (Map<String, Object> assignment : assignments) {
                        titles.add((String) assignment.get("title"));
                    }
                } catch (IOException ex) {
                    publish("Failed to load assignments: " + ex.getMessage());
                    titles = List.of(PLACEHOLDER);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendResult(s);
            }

            @Override
            protected void done() {
                setModel(assignmentBox, titles, true);
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
                    for (Map<String, Object> problem : problems) {
                        titles.add((String) problem.get("title"));
                    }
                } catch (IOException ex) {
                    publish("Failed to load problems: " + ex.getMessage());
                    titles = List.of(PLACEHOLDER);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) appendResult(s);
            }

            @Override
            protected void done() {
                setModel(problemBox, titles, true);
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

    private void updateSubmitEnabled() {
        boolean ready =
                client != null && client.isAuthenticated() &&
                        assignmentBox.isEnabled() && assignmentBox.getSelectedIndex() > 0 &&
                        problemBox.isEnabled() && problemBox.getSelectedIndex() > 0 &&
                        selectedFile != null;
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