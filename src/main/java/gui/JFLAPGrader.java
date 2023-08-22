package gui;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import automata.Automaton;
import automata.fsa.FiniteStateAutomaton;
import conversions.REToNFA;
import equivalence.Grader;
import file.ParseException;
import file.XMLCodec;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;
import regular.RegularExpression;

public class JFLAPGrader extends JFrame implements ActionListener {
    private JLabel usernameLabel;

    private JLabel graderFeedbackLabel;

    private JPanel submissionPanels;

    private JLabel progressLabel;

    private JButton leftButton;

    private JButton rightButton;

    private File[] submissions;

    private int submissionIndex;

    private File answerKey;

    public JFLAPGrader() {
	super("HANNAH");

	JPanel feedbackPanel = new JPanel();
	JPanel navigationPanel = new JPanel();

	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	this.usernameLabel = new JLabel();
	this.usernameLabel.setFont(this.usernameLabel.getFont().deriveFont(Font.BOLD));
	this.graderFeedbackLabel = new JLabel();
	this.graderFeedbackLabel.setFont(this.graderFeedbackLabel.getFont().deriveFont(Font.PLAIN));

	feedbackPanel.setLayout(new BoxLayout(feedbackPanel, BoxLayout.Y_AXIS));
	feedbackPanel.add(usernameLabel);
	feedbackPanel.add(graderFeedbackLabel);
	this.add(feedbackPanel, BorderLayout.NORTH);

	this.submissionPanels = new JPanel(new CardLayout());
	this.submissionPanels.setPreferredSize(new Dimension(800, 600));
	this.add(submissionPanels, BorderLayout.CENTER);

	this.leftButton = new JButton("<");
	this.leftButton.setActionCommand("previous");
	this.leftButton.addActionListener(this);
	this.progressLabel = new JLabel();
	this.rightButton = new JButton(">");
	this.rightButton.setActionCommand("next");
	this.rightButton.addActionListener(this);

	navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.X_AXIS));
	navigationPanel.add(Box.createHorizontalGlue());
	navigationPanel.add(leftButton);
	navigationPanel.add(progressLabel);
	navigationPanel.add(rightButton);
	this.add(navigationPanel, BorderLayout.SOUTH);

	this.pack();
    }

    public void grade(File gradingDir, File answerKey) {
	XMLCodec codec = new XMLCodec();
	Serializable correct = codec.decode(answerKey, null);

	this.submissions = gradingDir.listFiles(new FilenameFilter() {
		public boolean accept(File dir, String name) {
		    return name.endsWith(".jff");
		}
	    });

	for (File submission : this.submissions) {
	    try {
		Serializable s = codec.decode(submission, null);

		if (!s.getClass().equals(correct.getClass())) {
		    this.submissionPanels.add(new JPanel(), submission.getName());
		    continue;
		}

		if (correct instanceof FiniteStateAutomaton) {
		    AutomatonDrawer d = new AutomatonDrawer((Automaton)codec.decode(submission, null));

		    this.submissionPanels.add(new AutomatonPane(d), submission.getName());
		}
		else if (correct instanceof RegularExpression) {
		    JLabel relabel = new JLabel(((RegularExpression)s).asString());
		    Font labelFont = relabel.getFont();

		    relabel.setFont(labelFont.deriveFont(36.f));
		    this.submissionPanels.add(relabel, submission.getName());
		}
	    }
	    catch (ParseException e) {
		this.submissionPanels.add(new JPanel(), submission.getName());
		continue;
	    }
	}

	this.answerKey = answerKey;
	this.setSubmissionIndex(0);
	this.setVisible(true);
    }

    private void setSubmissionIndex(int i) {
	assert(i < this.submissions.length);
	this.submissionIndex = i;
	File submission = this.submissions[this.submissionIndex];
	String fname = submission.getName();
	String username = fname.substring(0, fname.indexOf("."));
	XMLCodec codec = new XMLCodec();

	this.usernameLabel.setText(username);
	this.progressLabel.setText((this.submissionIndex + 1) + "/" + submissions.length);
	((CardLayout)this.submissionPanels.getLayout()).show(this.submissionPanels, fname);
	this.leftButton.setEnabled(this.submissionIndex > 0);
	this.rightButton.setEnabled(this.submissionIndex < (this.submissions.length - 1));

	try {
	    Serializable correct = codec.decode(this.answerKey, null);
	    Serializable s = codec.decode(submission, null);

	    if (s.getClass().equals(correct.getClass())) {
		if (correct instanceof FiniteStateAutomaton) {
		    Grader g = new Grader(this.answerKey.getPath(), submission.getPath(), true, true, true, true);

		    this.graderFeedbackLabel.setText(g.getResult());
		}
		else if (correct instanceof RegularExpression) {
		    File correctConv = File.createTempFile("jflap", "jff");
		    File submissionConv = File.createTempFile("jflap", "jff");

		    REToNFA.convert(this.answerKey, correctConv);
		    REToNFA.convert(this.submissions[this.submissionIndex], submissionConv);

		    Grader g = new Grader(correctConv.getPath(), submissionConv.getPath(), true, true, true, true);

		    this.graderFeedbackLabel.setText(g.getResult());
		    correctConv.delete();
		    submissionConv.delete();
		}
	    }
	    else {
		this.graderFeedbackLabel.setText("Submission is of the wrong type");
	    }
	}
	catch (ParseException p) {
	    this.graderFeedbackLabel.setText("Parse exception");
	}
	catch (IOException e) {
	    this.graderFeedbackLabel.setText("Grader error: " + e);
	}
    }

    public void actionPerformed(ActionEvent e) {
	if ("previous".equals(e.getActionCommand())) {
	    if (this.submissionIndex > 0) {
		this.setSubmissionIndex(this.submissionIndex - 1);
	    }
	}
	else if ("next".equals(e.getActionCommand())) {
	    if (this.submissionIndex < (this.submissions.length - 1)) {
		this.setSubmissionIndex(this.submissionIndex + 1);
	    }
	}
    }

    public static void main(String[] args) {
	File gradingDir = new File(args[0]);
	File answerKey = new File(args[1]);
	JFLAPGrader grader = new JFLAPGrader();

	grader.grade(gradingDir, answerKey);

	return;
    }
}
