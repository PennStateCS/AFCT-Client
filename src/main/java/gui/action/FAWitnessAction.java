/*
 * Code written for Master's Project by Daphne A. Norton at RIT.
 * December 28, 2008
 * 
 * Version 2 - moving NFA to DFA conversion to EquivalenceNlgNWitness
 * class where it belongs.
 * Version 2.1 - switching to use EquivalenceWitness superclass with
 * EquivalenceMergeWitness subclass as algorithm.  The name of the
 * subclass can easily be replaced with another algorithm, if so desired.
 */

package gui.action;

import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.Universe;

import java.awt.event.ActionEvent;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;

import automata.fsa.FiniteStateAutomaton;
import equivalence.EquivalenceWitness;
import equivalence.EquivalenceMergeWitness;

/**
 * This class checks finite automata for equivalence, and if they
 * are inequivalent, it outputs a 'witness' string
 * which one automaton accepts and the other does not.
 * 
 * The structure of this class is based on the design
 * of other FSAActions in the JFLAP 6.4 gui.action package,
 * such as the DFAEqualityAction by Thomas Finley.
 * 
 * @author Daphne A. Norton
 */


public class FAWitnessAction extends FSAAction {
	
	/** The environment. */
	private Environment environment;

	
	/**
	 * Instantiates a new <CODE>FAWitnessAction</CODE>.
	 * 
	 * @param environment
	 *            the environment object that we shall add our simulator pane to
	 */
	public FAWitnessAction(Environment environment) {
		// Specify the text for the menu option.
		super("Get Distinguishing String", null);
		this.environment = environment;

	}
	

	/**
	 * Compares the two automata the user selected.
	 * Checks for equivalence, and if they are not
	 * equivalent, produces a witness string.
	 * 
	 * @param e  the action event
	 */
	public void actionPerformed(ActionEvent e) {
		JComboBox combo = new JComboBox();
		// Figure out what existing environments in the program have
		// the type of structure that we need.  Add them to a list
		// for the user to choose from.
		EnvironmentFrame[] frames = Universe.frames();
		for (int i = 0; i < frames.length; i++) {
			if (!isApplicable(frames[i].getEnvironment().getObject())
					|| frames[i].getEnvironment() == environment)
				continue;
			combo.addItem(frames[i]);
		}
		// Set up our automaton.
		FiniteStateAutomaton automaton = (FiniteStateAutomaton) environment
				.getObject();

		if (combo.getItemCount() == 0) {
			JOptionPane.showMessageDialog(Universe
					.frameForEnvironment(environment), "No FAs available!  Create some, or open some files.");
			return;
		}
		if (automaton.getInitialState() == null) {
			JOptionPane.showMessageDialog(Universe
					.frameForEnvironment(environment),
					"This automaton has no initial state!");
			return;
		}
		// Prompt the user for the second automaton.
		int result = JOptionPane.showOptionDialog(Universe
				.frameForEnvironment(environment), combo, "Compare against FA - select:",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
				null, null, null);
		if (result != JOptionPane.YES_OPTION && result != JOptionPane.OK_OPTION)
			return;
		FiniteStateAutomaton other = (FiniteStateAutomaton) ((EnvironmentFrame) combo
				.getSelectedItem()).getEnvironment().getObject();
		if (other.getInitialState() == null) {
			JOptionPane.showMessageDialog(Universe
					.frameForEnvironment(environment),
					"The second automaton has no initial state!");
			return;
		}

		// Check for equivalence.
		EquivalenceWitness equivWitness = new EquivalenceMergeWitness(automaton, other, true);
		boolean equivalent = equivWitness.areAutomataEquivalent();
		boolean inputErr = equivWitness.getHasInputError();
		String equivMessage = "These automata are equivalent, so they accept the same strings.";
		if (inputErr) {
			equivMessage = equivWitness.getInputErrorMessage();
		}
		else if (!equivalent) {
			equivMessage = "One accepts " + equivWitness.getWitness() +
			               " and the other does not.";
		}
		JOptionPane.showMessageDialog(
				Universe.frameForEnvironment(environment), equivMessage);
	}
}
