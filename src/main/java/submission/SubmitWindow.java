package submission;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static gui.Globals.*;
import static submission.LoginWindow.createComboBoxPanel;
import static submission.LoginWindow.createInputPanel;

public class SubmitWindow {
    private JPanel contentPane;
    private JComboBox<CourseItem> courseBox;
    private JButton courseRefreshButton;
    private JComboBox<AssignmentItem> assignmentBox;
    private JButton assignmentRefreshButton;
    private DetailsPanel assignmentDetailsPanel;
    private JRadioButton allAssignments;
    private JRadioButton upcomingAssignments;
    private JComboBox<ProblemItem> problemBox;
    private JButton problemRefreshButton;
    private DetailsPanel problemDetailsPanel;
    private JRadioButton allProblems;
    private JRadioButton uncompletedProblems;
    private JLabel currentFileLabel;
    private JButton viewCurrentButton;
    private JButton submitButton;
    // TODO: replace this with better, more modern user feedback methods
    private JTextArea result;

    public SubmitWindow(JFrame parentFrame) {
        contentPane = new JPanel();
        courseBox = new JComboBox<>();
        courseRefreshButton = new JButton();
        assignmentBox = new JComboBox<>();
        assignmentRefreshButton = new JButton();
        assignmentDetailsPanel = new DetailsPanel(parentFrame, "Assignment Details");
        allAssignments = new JRadioButton("All Assignments");
        upcomingAssignments = new JRadioButton("Upcoming Assignments");
        problemBox = new JComboBox<>();
        problemRefreshButton = new JButton();
        problemDetailsPanel = new DetailsPanel(parentFrame, "Problem Details");
        allProblems = new JRadioButton("All Problems");
        uncompletedProblems = new JRadioButton("Uncompleted Problems");
        currentFileLabel = new JLabel("No File Selected");
        viewCurrentButton = new JButton("View");
        submitButton = new JButton("Submit");

        setupGui();

        //TODO: DELETE - just for testing
        problemDetailsPanel.setDetailsText("Create a Deterministic Finite State Automaton that accepts strings that contain any number of b's and at least one a, in any order.");
    }

    public JPanel getContentPane() {
        return contentPane;
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
        //submitButton.setPreferredSize(new Dimension(360, 36));
        //submitButton.setMargin(new Insets(6, 12, 6, 12));
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        //c.insets = new Insets(5, hozInset, vrtInset, hozInset);
        c.insets = new Insets(vrtInset + 5, hozInset, vrtInset, hozInset);
        contentPane.add(submitButton, c);
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
        c = setConstraints(0, 0, 1, 1);
        changeSize(viewCurrentButton, 16);
        filePanel.add(viewCurrentButton, c);

        return filePanel;
    }

    private JPanel createComboBoxWithRefreshPanel(JComboBox comboBox, JButton refreshButton, String headerText) {
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

    private JPanel createComboBoxWithButtonsPanel(JComboBox comboBox, JButton refreshButton, DetailsPanel detailsPanel, JRadioButton radioButton1, JRadioButton radioButton2, String headerText) {
        JPanel inputPanel = createComboBoxWithRefreshPanel(comboBox, refreshButton, headerText);
        GridBagConstraints c;
        int y = 2;

        // Add detailsPanel
        c = setConstraints(0, 0, 0, y++);
        c.gridwidth = GridBagConstraints.REMAINDER;
        changeSize(radioButton1, 14);
        inputPanel.add(detailsPanel, c);

        // Add radio buttons to a group
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(radioButton1);
        buttonGroup.add(radioButton2);

        // Create buttonPanel
        JPanel buttonPanel = new JPanel(new GridBagLayout());

        // Add radioButton1 to buttonPanel
        c = setConstraints(0.5, 0, 0, 0);
        changeSize(radioButton1, 14);
        unBoldFont(radioButton1);
        buttonPanel.add(radioButton1, c);

        // Add radioButton2 to buttonPanel
        c = setConstraints(0.5, 0, 1, 0);
        changeSize(radioButton2, 14);
        unBoldFont(radioButton2);
        buttonPanel.add(radioButton2, c);

        // Add buttonPanel to inputPanel
        c = setConstraints(0, 0, 0, y);
        c.insets = new Insets(5, 0, 0, 0);
        inputPanel.add(buttonPanel, c);

        return inputPanel;
    }
}
