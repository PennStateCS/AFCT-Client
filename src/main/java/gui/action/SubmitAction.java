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

import java.io.Serializable;

import gui.deterministic.ConversionPane;
import gui.environment.Environment;
import gui.environment.Universe;
import gui.environment.tag.CriticalTag;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import submission.LegacySubmitDialog;
import submission.SubmitDialog;

/**
 * This is a simple action to submit a JFLAP file
 *
 * @author Thomas Finley
 */

public class SubmitAction extends RestrictedAction {
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new submission
	 *
	 * @param automaton
	 *            the automaton that input will be simulated on
	 * @param environment
	 *            the environment object that we shall add our simulator pane to
	 */
	public SubmitAction(Serializable obj, Environment environment) {
		super("Submit", null);
		this.obj = obj;
		this.environment = environment;
		/*
		 * putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke (KeyEvent.VK_R,
		 * MAIN_MENU_MASK+InputEvent.SHIFT_MASK));
		 */
	}

	/**
	 * Starts submission window
	 *
	 * @param e
	 *            the action event
	 */
	public void actionPerformed(ActionEvent e) {
        if (Universe.curProfile.getUseLegacySubmissionGui()) {
            LegacySubmitDialog d = new LegacySubmitDialog(this.environment);
            d.setVisible(true);
        } else {
            SubmitDialog d = Universe.submitDialogForEnvironment(this.environment);
            if (d == null) {
                d = new SubmitDialog(this.environment);
                d.setContentPane(d.getMainPanel());
                d.pack();
                d.setLocationRelativeTo(null);
                d.setResizable(false);
                d.refreshDialog();
                d.setVisible(true);
                Universe.registerSubmitDialog(this.environment, d);
            } else {
                d.refreshDialog();
                d.setVisible(true);
                d.toFront();
            }
        }
	}

	/** The automaton. */
	private Serializable obj;

	/** The environment. */
	private Environment environment;
}
