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

import automata.Automaton;
import automata.fsa.FSAStepWithClosureSimulator;
import gui.environment.RegularEnvironment;
import gui.environment.Universe;
import gui.regular.ConvertToAutomatonPane;
import gui.regular.REToFSAController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * This class initiates the validation/testing of an input string
 * on a regular expression, responding with accept or reject.
 *
 * @author Teddy FitzPatrick
 */

public class TestREInput extends RegularAction {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a <CODE>TestREInput</CODE>.
	 *
	 * @param environment the regular expression's environment
	 *
	 */
	public TestREInput(RegularEnvironment environment) {
		super("Test Input", null, environment);
	}

	/**
	 * This begins the process of testing an input string on the regular expression
	 *
	 * @param event
	 *            the event to process
	 */
	public void actionPerformed(ActionEvent event) {
		// prompt the input string to test against the regular expression
		SimulateAction action = new SimulateAction((Automaton) null, (RegularEnvironment) getEnvironment());
		Object input = action.initialInput((Component) getEnvironment().getActive(), "Input String");
		// "cancel" was selected in the input menu
		if (input == null) return;
		// Convert the regular expression to an NFA
		ConvertToAutomatonPane pane = new ConvertToAutomatonPane(
				(RegularEnvironment) getEnvironment());
		REToFSAController controller = pane.controller;
		// Complete the RegEx -> FSA (NFA) conversion
		controller.completeAll();
		Automaton automaton = controller.automaton;
		// Identify an accepting configuration for validation
		// If there are none, reject
		FSAStepWithClosureSimulator simulator = new FSAStepWithClosureSimulator(automaton);
		boolean isAccepted = simulator.simulateInput(input.toString());
		// Notify the user if the inputted string was accepted/rejected
		JFrame frame = Universe.frameForEnvironment(getEnvironment());
		JOptionPane.showMessageDialog(frame, "The input was " + (isAccepted ? "accepted" : "rejected") + ".");
	}
}
