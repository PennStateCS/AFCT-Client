/*
 *  JFLAP - Formal Languages and Automata Package
 * 
 * 
 *  Susan H. Rodger
 *  Computer Science Department
 *  Duke University
 *  August 27, 2009

 *  Copyright (c) 2002-2009
 *  All rights reserved.

 *  JFLAP is open source software. Please see the LICENSE for terms.
 *
 */





package gui.action;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.Cursor;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import automata.turing.IncompatibleTMsException;
import automata.turing.TuringEq;
import automata.turing.TuringMachine;
import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.Universe;

/**
 * This tests to see if two Turing machines accept the same language.
 * 
 * @author Lucas Famous
 */

public class TMEqualityAction extends AutomatonAction{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new <CODE>TMEqualityAction</CODE>.
	 * 
	 * @param automaton
	 *            the Turing machine that input will be simulated on
	 * @param environment
	 *            the environment object that we shall add our simulator pane to
	 */
	public TMEqualityAction(TuringMachine automaton,
			Environment environment) {
		super("Compare Equivalence", null);
		this.environment = environment;
		/*
		 * putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke (KeyEvent.VK_R,
		 * MAIN_MENU_MASK+InputEvent.SHIFT_MASK));
		 */
	}

	/**
	 * Runs a comparison with another Turing machine.
	 * 
	 * @param e
	 *            the action event
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		JComboBox<EnvironmentFrame> combo = new JComboBox<>();
		// Figure out what existing environments in the program have
		// the type of structure that we need.
		EnvironmentFrame[] frames = Universe.frames();
		for (EnvironmentFrame frame : frames) {
			// get every other turing machine environment
			if (!isApplicable(frame.getEnvironment().getObject()) || frame.getEnvironment() == environment) {
				continue;
			}
			combo.addItem(frame);
		}
		// Set up our automaton.
		TuringMachine automaton = (TuringMachine) environment
				.getObject();

		if (combo.getItemCount() == 0) {
			showErrorDialog("No other TMs around!");
			return;
		}
		if (automaton.getInitialState() == null) {
			showErrorDialog("This automaton has no initial state!");
			return;
		}

		// Ask user if they want to compare outputs and/or tapes. For multitapes, ask which tape is input/output
        JCheckBox compareTape = new JCheckBox("Compare tape outputs");	
	
		JPanel panel = new JPanel();
		panel.add(combo);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(compareTape);

		boolean multiTape = automaton.tapes() > 1;
		Integer[] tapePossibilities = new Integer[automaton.tapes()];
		for (int i = 0; i < automaton.tapes(); i++) {
			tapePossibilities[i] = i + 1;
		}
		JComboBox<Integer> inputTapeChoice = new JComboBox<>(tapePossibilities);
		JComboBox<Integer> outputTapeChoice = new JComboBox<>(tapePossibilities);
		
		// add multitape options
		if (multiTape) {
			panel.add(new JLabel("Input tape number:"));
			panel.add(inputTapeChoice);
			panel.add(new JLabel("Output tape number:"));
			panel.add(outputTapeChoice);
		}

		int result = JOptionPane.showOptionDialog(Universe
				.frameForEnvironment(environment), panel, "Compare against TM",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
				null, null, null);
		if (result != JOptionPane.YES_OPTION && result != JOptionPane.OK_OPTION)
			return;
		TuringMachine other = (TuringMachine) ((EnvironmentFrame) combo
				.getSelectedItem()).getEnvironment().getObject();
		if (other.getInitialState() == null) {
			showErrorDialog("The other automaton has no initial state!");
			return;
		}
		
		try {
			checker = new TuringEq(automaton, other);
		} catch (IncompatibleTMsException exception) {
			showErrorDialog(exception.getMessage());
			return;
		}
		
		// dialog to correct the automatically detected alphabet
		correctAlphabet(checker);
		if (multiTape) {
			checker.setInputTapeNum((int) (inputTapeChoice.getSelectedItem()) - 1);
			checker.setOutputTapeNum((int) (outputTapeChoice.getSelectedItem()) - 1);
		}

		// Run eq in background worker thread
		SwingWorker<Void, Void> eqworker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() throws Exception {
				runComparison(compareTape.isSelected());
				return null;
			}

			@Override
			protected void done() {
				environment.setCursor(Cursor.getDefaultCursor());
			}
		};
		environment.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		eqworker.execute();

		
	}

	/**
	 * Runs the comparison between two TMs
	 */
	private void runComparison(boolean runTapeCompare) {
		// check equivalence and output
		String outputMessage = "Output: ";
		if (!runTapeCompare) {
			String checkedMessage = checker.checkEquivalence(false) ? "Outputs ARE equivalent!"
				.concat("\nNumber matched: " + checker.getMatching_count())
				.concat("\nNumber failed to halt: " + checker.getContinue_count())
					: "Outputs AREN'T equivalent! On input " + checker.getMismatchInput();
			outputMessage = outputMessage.concat("\n" + checkedMessage);
		}
		else {
			boolean eq = checker.checkEquivalence(true);
			String tapeMessage = eq ? "Tapes ARE equivalent!"
				.concat("\nNumber matched: " + checker.getMatching_count())
				.concat("\nNumber failed to halt: " + checker.getContinue_count())
					: "Tapes AREN'T equivalent! On input " + checker.getMismatchInput() + ":"
					.concat("\nThis tape: " + checker.getMismatchTape1().getOutputTapevals().toString() 
					+ " - " + (checker.getMismatchTape1().isAccepted() ? "Accepted" : "Rejected"))
					.concat("\nComparison Tape: " + checker.getMismatchTape2().getOutputTapevals().toString()
					+ " - " + (checker.getMismatchTape2().isAccepted() ? "Accepted" : "Rejected"));
			outputMessage = outputMessage.concat("\n" + tapeMessage);
		}
		
		JOptionPane.showMessageDialog(
				Universe.frameForEnvironment(environment), outputMessage);
	}

	public static boolean isApplicable(Object object) {
		return object instanceof TuringMachine;
	}

	/**
	 * Creates a dialog to correct the TuringEq's automatically detected alphabet
	 * @param checker the TuringEq checker to correct
	 */
	private void correctAlphabet(TuringEq checker) {
		// get and correct the alphabet
		String[] alphabet = checker.getAlphabet();
		JOptionPane dialog = new JOptionPane();
		dialog.setLayout(new BorderLayout());
		DefaultListModel<String> model = new DefaultListModel<>();
		for (String s : alphabet) {
			model.addElement(s);
		}
		JList<String> alphabetChecker = new JList<>(model);
		alphabetChecker.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent arg0) {
                if (!arg0.getValueIsAdjusting()) {
                  selectedString = alphabetChecker.getSelectedValue();
				  selectedIndex = alphabetChecker.getSelectedIndex();
                }
            }
        });
		JButton removeButton = new JButton("Remove selection");
		JPanel buttonPanel = new JPanel();
		buttonPanel.add(removeButton);
		removeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				checker.removeFromAlphabet(selectedString);
				((DefaultListModel<String>) (alphabetChecker.getModel())).remove(selectedIndex);
			}
		});
		dialog.add(new JScrollPane(alphabetChecker), BorderLayout.CENTER);
		dialog.add(buttonPanel, BorderLayout.SOUTH);
		JOptionPane.showMessageDialog(null, dialog, "Alphabet Checker", JOptionPane.PLAIN_MESSAGE);
	}

	private void showErrorDialog(String message) {
		JOptionPane.showMessageDialog(Universe.frameForEnvironment(environment), message);
	}

	/** The environment. */
	private Environment environment;

	/** The equality checker. */
	private static TuringEq checker;

	
	private String selectedString = "";
	private int selectedIndex = 0;
}
