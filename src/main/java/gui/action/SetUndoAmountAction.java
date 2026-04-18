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

import gui.environment.Environment;
import gui.environment.AutomatonEnvironment;
import gui.environment.Universe;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.Serializable;

import javax.swing.*;

import debug.EDebug;

/**
 * The <CODE>SetUndoAmount</CODE> is an action to set the amount of Undos that are stored for automaton construction.
 * 
 * @author Henry Qin
 * @author Jesse Burdick-Pless
 */

public class SetUndoAmountAction extends RestrictedAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new <CODE>SetUndoAmountAction</CODE>.
	 */
	public SetUndoAmountAction () {
		super(getActionText(), null);
	}

	/**
	 * If an Undo amount change was requested, then show a dialog and ask about it. 
	 * @param event
	 *            the action event
	 */
	public void actionPerformed(ActionEvent event) {
		String str; 
        int n;
        while (true){
//            str = JOptionPane.showInputDialog(null, "Please type the number of Undos:", "How many undo?", ""+Universe.curProfile.undo_num,  JOptionPane.PLAIN_MESSAGE);
            str = JOptionPane.showInputDialog("Please type a number for the Undo limit (or -1 for no limit)", ""+Universe.curProfile.undo_num);
            try {
                n = Integer.parseInt(str);
            }
            catch (NumberFormatException e){
                if (str != null)
                    continue;
                else 
                    return;
            }
                break;
        }

        //we better make sure this option is disabled for places where Undo does not apply.
        //((AutomatonEnvironment) environment).getUndoKeeper().setNumUndo(n);
        Universe.curProfile.setNumUndo(n);
        Universe.curProfile.savePreferences();

		updateActionText();
	}

	private static String getActionText() {
		String base = "Set Undo Limit";
		Integer undoNum = null;
		if (Universe.curProfile != null) {
			undoNum = Universe.curProfile.getNumUndo();
		}

		if (undoNum != null) {
			if (undoNum < 0) {
				base += " (currently unlimited)";
			} else {
				base += " (currently " + undoNum + ")";
			}
		}

		return base;
	}

	public void updateActionText() {
		this.putValue(Action.NAME, getActionText());
	}

	/**
	 * This action is restricted to those objects that are serializable.
	 * 
	 * @param object
	 *            the object to check for serializable-ness
	 * @return <CODE>true</CODE> if the object is an instance of a
	 *         serializable object, <CODE>false</CODE> otherwise
	 */
	public static boolean isApplicable(Object object) {
		return true;
	}

}
