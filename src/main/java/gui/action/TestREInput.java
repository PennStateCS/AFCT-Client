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

import gui.environment.RegularEnvironment;
import gui.environment.tag.CriticalTag;
import gui.regular.ConvertToAutomatonPane;
import java.awt.event.*;
import javax.swing.JOptionPane;

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
		super("Test Input (DEBUG)", null, environment);
	}

	/**
	 * This begins the process of testing an input string on the regular expression
	 *
	 * @param event
	 *            the event to process
	 */
	public void actionPerformed(ActionEvent event) {
		// JFrame frame = Universe.frameForEnvironment(environment);
//		try {
//			getExpression().asCheckedString();
//		} catch (UnsupportedOperationException e) {
//			JOptionPane.showMessageDialog(getEnvironment(), e.getMessage(),
//					"Illegal Expression", JOptionPane.ERROR_MESSAGE);
//			return;
//		}
//		ConvertToAutomatonPane pane = new ConvertToAutomatonPane(
//				getEnvironment());
//		getEnvironment().add(pane, "Convert RE to NFA", new CriticalTag() {
//		});
//		getEnvironment().setActive(pane);
	}
}
