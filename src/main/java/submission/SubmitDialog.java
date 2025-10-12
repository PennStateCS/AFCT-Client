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
                                List<Map<String, Object>> fetched = client.getCourses();
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

//package submission;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.File;
//import java.io.UnsupportedEncodingException;
//
//import java.util.ArrayList;
//import java.util.Map;
//import java.util.prefs.Preferences;
//
//import javax.swing.ComboBoxModel;
//import javax.swing.JButton;
//import javax.swing.JComboBox;
//import javax.swing.JDialog;
//import javax.swing.JLabel;
//import javax.swing.JOptionPane;
//import javax.swing.JPanel;
//import javax.swing.JPasswordField;
//import javax.swing.JTextArea;
//import javax.swing.JTextField;
//import javax.swing.SwingConstants;
//
//import java.awt.BorderLayout;
//import java.awt.CardLayout;
//import java.awt.Container;
//import java.awt.Dimension;
//import java.awt.GridBagConstraints;
//import java.awt.GridBagLayout;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.awt.event.ItemEvent;
//import java.awt.event.ItemListener;
//import java.awt.event.KeyAdapter;
//import java.awt.event.KeyEvent;
//
//import java.net.URL;
//import java.net.MalformedURLException;
//
//import org.apache.commons.io.IOUtils;
//import org.apache.commons.lang3.StringUtils;
//
//import org.apache.http.HttpResponse;
//import org.apache.http.auth.AuthScope;
//import org.apache.http.auth.UsernamePasswordCredentials;
//import org.apache.http.client.HttpClient;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.entity.mime.MultipartEntityBuilder;
//import org.apache.http.impl.client.BasicCredentialsProvider;
//import org.apache.http.impl.client.HttpClientBuilder;
//
//import org.json.simple.JSONArray;
//import org.json.simple.parser.JSONParser;
//import org.json.simple.parser.ParseException;
//
//import gui.environment.Environment;
//import file.XMLCodec;
//
//public class SubmitDialog extends JDialog implements ActionListener {
//    private Environment env;
//    private JPanel submissionCards;
//    private JTextField serverTF;
//    private JTextField usernameTF;
//    private JPasswordField passwordTF;
//    private JComboBox homeworkCB;
//    private JComboBox probCB;
//    private JLabel descLabel;
//    private JTextArea resultText;
//    private JButton submit;
//
//    // Preferences
//    private String PREF_SERVER = "server";
//    private String PREF_USERNAME = "username";
//    private String PREF_PASSWORD = "password";
//    private String PREF_HOMEWORK = "homework";
//    private String PREF_PROBLEM = "problem";
//
//    public SubmitDialog(Environment env) {
//        Container c = this.getContentPane();
//        JPanel submissionFrame = new JPanel();
//        JPanel submittedFrame = new JPanel();
//        GridBagConstraints leftCons = new GridBagConstraints();
//        GridBagConstraints rightCons = new GridBagConstraints();
//
//        this.env = env;
//        this.submissionCards = new JPanel();
//        this.serverTF = new JTextField();
//        this.usernameTF = new JTextField();
//        this.passwordTF = new JPasswordField();
//        this.homeworkCB = new JComboBox();
//        this.probCB = new JComboBox();
//        this.descLabel = new JLabel();
//        this.resultText = new JTextArea();
//        this.submit = new JButton("Submit");
//
//        this.submit.addActionListener(this);
//
//        this.resultText.setEnabled(false);
//        this.resultText.setLineWrap(true);
//        this.resultText.setWrapStyleWord(true);
//
//        this.setMinimumSize(new Dimension(300, 0));
//
//        Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);
//
//        this.serverTF.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyReleased(KeyEvent e) {
//                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
//                    fetchHomework(serverTF.getText());
//                } else {
//                    prefs.put(PREF_SERVER, serverTF.getText());
//                }
//            }
//        });
//
//        this.usernameTF.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyReleased(KeyEvent e) {
//                prefs.put(PREF_USERNAME, usernameTF.getText());
//            }
//        });
//
//        this.passwordTF.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyReleased(KeyEvent e) {
//                prefs.put(PREF_PASSWORD, passwordTF.getText());
//            }
//        });
//
//
//        probCB.addItemListener(new ItemListener() {
//            @Override
//            public void itemStateChanged(ItemEvent e) {
//                if (e.getStateChange() == ItemEvent.SELECTED) {
//                    Problem prob = (Problem)e.getItem();
//
//                    descLabel.setText(prob.getDescription());
//                }
//                else {
//                    descLabel.setText("");
//                }
//            }
//        });
//
//        homeworkCB.addItemListener(new ItemListener() {
//            @Override
//            public void itemStateChanged(ItemEvent e) {
//                if (e.getStateChange() == ItemEvent.SELECTED) {
//                    Homework homework = (Homework)e.getItem();
//
//                    probCB.removeAllItems();
//
//                    for (Problem p : homework.getProblems()) {
//                        probCB.addItem(p);
//                    }
//                }
//            }
//        });
//
//        leftCons.gridx = 0;
//        leftCons.fill = GridBagConstraints.HORIZONTAL;
//        leftCons.weightx = 0;
//        rightCons.gridx = 1;
//        rightCons.fill = GridBagConstraints.HORIZONTAL;
//        rightCons.weightx = 1;
//        submissionFrame.setLayout(new GridBagLayout());
//        leftCons.gridy = 0;
//        submissionFrame.add(new JLabel("Server:", SwingConstants.RIGHT), leftCons);
//        rightCons.gridy = 0;
//        submissionFrame.add(this.serverTF, rightCons);
//        leftCons.gridy = 1;
//        submissionFrame.add(new JLabel("Username:", SwingConstants.RIGHT), leftCons);
//        rightCons.gridy = 1;
//        submissionFrame.add(this.usernameTF, rightCons);
//        leftCons.gridy = 2;
//        submissionFrame.add(new JLabel("Password:", SwingConstants.RIGHT), leftCons);
//        rightCons.gridy = 2;
//        submissionFrame.add(this.passwordTF, rightCons);
//        leftCons.gridy = 3;
//        submissionFrame.add(new JLabel("Homework:", SwingConstants.RIGHT), leftCons);
//        rightCons.gridy = 3;
//        submissionFrame.add(this.homeworkCB, rightCons);
//        leftCons.gridy = 4;
//        submissionFrame.add(new JLabel("Problem:", SwingConstants.RIGHT), leftCons);
//        rightCons.gridy = 4;
//        submissionFrame.add(this.probCB, rightCons);
//        leftCons.gridy = 5;
//        submissionFrame.add(new JLabel("Description:", SwingConstants.RIGHT), leftCons);
//        rightCons.gridy = 5;
//        submissionFrame.add(this.descLabel, rightCons);
//
//        submittedFrame.setLayout(new BorderLayout(5, 5));
//        submittedFrame.add(this.resultText, BorderLayout.CENTER);
//
//        this.submissionCards.setLayout(new CardLayout());
//        this.submissionCards.add(submissionFrame, "submitting");
//        this.submissionCards.add(submittedFrame, "submitted");
//
//        c.add(submissionCards, BorderLayout.CENTER);
//        c.add(submit, BorderLayout.SOUTH);
//
//        this.pack();
//        this.setInitialUIValues();
//    }
//
//    public void actionPerformed(ActionEvent e) {
//        this.submit.setEnabled(false);
//
//        try {
//            File f = File.createTempFile("jflap", ".jff");
//            XMLCodec x = new XMLCodec();
//            long hwid = ((Homework)this.homeworkCB.getSelectedItem()).getId();
//            long pid = ((Problem)this.probCB.getSelectedItem()).getId();
//            MultipartEntityBuilder contentBuilder = MultipartEntityBuilder.create();
//            HttpPost httppost = new HttpPost(StringUtils.stripEnd(this.serverTF.getText(), "/") + "/api/homework/submit");
//            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(this.usernameTF.getText(), this.passwordTF.getText());
//            BasicCredentialsProvider credprov = new BasicCredentialsProvider();
//            HttpClientBuilder builder = HttpClientBuilder.create();
//            Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);
//
//            prefs.put(PREF_SERVER, this.serverTF.getText());
//            prefs.put(PREF_USERNAME, this.usernameTF.getText());
//            prefs.put(PREF_PASSWORD, this.passwordTF.getText());
//            prefs.putLong(this.serverTF.getText() + "_" + PREF_HOMEWORK, hwid);
//            prefs.putLong(this.serverTF.getText() + "_" + hwid + "_" + PREF_PROBLEM, pid);
//            x.encode(this.env.getObject(), f, null);
//            contentBuilder.addTextBody("hwid", Long.toString(hwid));
//            contentBuilder.addTextBody("pid", Long.toString(pid));
//            contentBuilder.addBinaryBody("submission", f);
//            httppost.setEntity(contentBuilder.build());
//            credprov.setCredentials(AuthScope.ANY, creds);
//            builder.setDefaultCredentialsProvider(credprov);
//
//            HttpClient client = builder.build();
//            HttpResponse res = client.execute(httppost);
//
//            if (res.getStatusLine().getStatusCode() == 200) {
//                CardLayout cl = (CardLayout)this.submissionCards.getLayout();
//                JSONParser parser = new JSONParser();
//                String jsonText = IOUtils.toString(res.getEntity().getContent());
//
//                try {
//                    Map submission = (Map)parser.parse(jsonText);
//
//                    if ((boolean)submission.get("correct")) {
//                        this.resultText.setText("Correct!");
//                    }
//                    else {
//                        this.resultText.setText("Incorrect: " + (String)submission.get("feedback"));
//                    }
//
//                    this.resultText.setEnabled(true);
//                } catch (ParseException pe) {
//                    JOptionPane.showMessageDialog(null, "Could not parse json");
//                }
//                cl.next(this.submissionCards);
//            } else if (res.getStatusLine().getStatusCode() == 401) {
//                JOptionPane.showMessageDialog(null, "Login error. Check your username and password.");
//            } else if (res.getStatusLine().getStatusCode() == 404) {
//                JOptionPane.showMessageDialog(null, "The homework was not found. Perhaps you are past the deadline?");
//            } else {
//                JOptionPane.showMessageDialog(null, "Unknown error: " + IOUtils.toString(res.getEntity().getContent()));
//            }
//
//            /*if (res.getStatusLine().getStatusCode() != 200) {
//                this.submit.setEnabled(false);
//            }*/
//
//            f.delete();
//        } catch (UnsupportedEncodingException ue) {
//            JOptionPane.showMessageDialog(null, "Unsupported encoding");
//        } catch (IOException ie) {
//            JOptionPane.showMessageDialog(null, "IO Exception");
//        } finally {
//            this.submit.setEnabled(true);
//        }
//    }
//
//    private void internalFetchHomework(InputStream is) {
//        try {
//            JSONParser parser = new JSONParser();
//            String jsonText = IOUtils.toString(is);
//
//            try {
//                JSONArray homework = (JSONArray)parser.parse(jsonText);
//
//                this.homeworkCB.removeAllItems();
//
//                for (Object h : homework) {
//                    Map hmap = (Map)h;
//                    ArrayList<Problem> problems = new ArrayList<Problem>();
//                    long hid  = (Long)hmap.get("id");
//                    String name = hmap.get("name").toString();
//                    Homework hw = new Homework(hid, name);
//
//                    for (Object p : (JSONArray)hmap.get("problems")) {
//                        Map pmap = (Map)p;
//                        long pid = (Long)pmap.get("id");
//                        String pname = (String)pmap.get("name");
//                        String pdesc = (String)pmap.get("description");
//                        Problem prob = new Problem(pid, pname, pdesc);
//
//                        problems.add(prob);
//                    }
//
//                    hw.setProblems(problems);
//                    this.homeworkCB.addItem(hw);
//                }
//            } catch (ParseException pe) {
//                JOptionPane.showMessageDialog(null, "Could not parse json");
//            } finally {
//                is.close();
//            }
//        } catch (MalformedURLException mue) {
//            JOptionPane.showMessageDialog(null, "Malformed URL");
//        } catch (IOException ie) {
//            JOptionPane.showMessageDialog(null, "IO Exception");
//        }
//    }
//
//    private void fetchHomework(String server) {
//        Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);
//        String newUrl = server;
//        while (true) {
//            try {
//                // If newUrl does not begin with an http prefix
//                if (!(newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
//                    newUrl = "http://" + newUrl;
//                }
//                prefs.put(PREF_SERVER, newUrl);
//                this.serverTF.setText(newUrl);
//                String url = StringUtils.stripEnd(newUrl, "/") + "/api/homework";
//                InputStream is = new URL(url).openStream();
//                internalFetchHomework(is);
//                return;
//            } catch (IOException e) {
//                newUrl = JOptionPane.showInputDialog("Unable to connect to server!\nPlease enter a valid AFCT Server URL:", prefs.get(PREF_SERVER, ""));
//                if (newUrl == null || newUrl.isBlank()) {
//                    return;
//                }
//            }
//        }
//    }
//
//    private void setInitialUIValues() {
//        Preferences prefs = Preferences.userNodeForPackage(submission.SubmitDialog.class);
//        String defaultServer = "";
//
//        if (this.getClass().getResource("/SETTINGS/SERVER") != null) {
//            try  {
//                defaultServer = IOUtils.toString(this.getClass().getResourceAsStream("/SETTINGS/SERVER"));
//            } catch (IOException e) {
//            }
//        }
//
//        if (!prefs.get(PREF_SERVER, defaultServer).equals("")) {
//            this.serverTF.setText(prefs.get(PREF_SERVER, defaultServer));
//            this.serverTF.requestFocusInWindow();
//            this.fetchHomework(prefs.get(PREF_SERVER, defaultServer));
//
//            if (!prefs.get(prefs.get(PREF_SERVER, "") + "_" + PREF_HOMEWORK,"").equals("")) {
//                long hwid = prefs.getInt(prefs.get(PREF_SERVER, "") + "_" + PREF_HOMEWORK, 0);
//                ComboBoxModel hwModel = this.homeworkCB.getModel();
//
//                for (int cidx = 0; cidx < hwModel.getSize(); cidx++) {
//                    if (hwid == ((Homework)hwModel.getElementAt(cidx)).getId()) {
//                        hwModel.setSelectedItem(hwModel.getElementAt(cidx));
//                    }
//                }
//
//                if (!prefs.get(prefs.get(PREF_SERVER, "") + "_" + hwid + "_" + PREF_PROBLEM,"").equals("")) {
//                    int probID = prefs.getInt(prefs.get(PREF_SERVER, "") + "_" + hwid + "_" + PREF_PROBLEM, 0);
//                    ComboBoxModel probModel = this.probCB.getModel();
//
//                    for (int pidx = 0; pidx < probModel.getSize(); pidx++) {
//                        if (probID == ((Problem)probModel.getElementAt(pidx)).getId()) {
//                            probModel.setSelectedItem(probModel.getElementAt(pidx));
//                        }
//                    }
//                }
//            }
//        }
//
//        this.usernameTF.setText(prefs.get(PREF_USERNAME, ""));
//        this.passwordTF.setText(prefs.get(PREF_PASSWORD, ""));
//    }
//}
