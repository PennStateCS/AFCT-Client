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

import gui.Globals;
import gui.deterministic.ConversionPane;
import gui.environment.Environment;
import gui.environment.Universe;
import gui.environment.tag.CriticalTag;

import java.awt.event.ActionEvent;

import javax.swing.*;

import submission.LegacySubmitDialog;
import submission.SubmitDialog;
import submission.SubmitWindow;

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
        boolean useLessModernSubmissionGui = false;

        if (Universe.curProfile.getUseLegacySubmissionGui()) {
            LegacySubmitDialog d = (LegacySubmitDialog) Universe.submitDialogForEnvironment(this.environment);
            if (d == null) {
                d = new LegacySubmitDialog(this.environment);
                Universe.registerSubmitDialog(this.environment, d);
                d.setVisible(true);
            } else {
                d.setVisible(true);
                d.toFront();
            }
        } else {
            if (useLessModernSubmissionGui) {
                SubmitDialog d = (SubmitDialog) Universe.submitDialogForEnvironment(this.environment);
                if (d == null) {
                    d = new SubmitDialog(this.environment);
                    Universe.registerSubmitDialog(this.environment, d);
                    d.setContentPane(d.getMainPanel());
                    d.pack();
                    d.setLocationRelativeTo(null);
                    d.setResizable(false);
                    d.refreshDialog();
                    d.setVisible(true);
                } else {
                    d.refreshDialog();
                    d.setVisible(true);
                    d.toFront();
                }
            } else {
                SubmitWindow d = (SubmitWindow) Universe.submitDialogForEnvironment(this.environment);
                if (d == null) {
                    d = Globals.sessionHandler.createNewSubmitWindow(environment);
                    Universe.registerSubmitDialog(this.environment, d);
                    d.pack();
                    d.setLocationRelativeTo(null);
                    //d.setResizable(false);
                    d.displaySubmitWindow();
//                    d.refreshDialog();
//                    d.setVisible(true);
                } else {
                    d.displaySubmitWindow();
//                    d.refreshDialog();
//                    d.setVisible(true);
//                    d.toFront();
                }
            }
        }
	}

	/** The automaton. */
	private Serializable obj;

	/** The environment. */
	private Environment environment;
}
