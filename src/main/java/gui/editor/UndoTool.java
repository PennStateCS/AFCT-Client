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





package gui.editor;

import gui.environment.AutomatonEnvironment;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;

import javax.swing.*;

import static gui.editor.IconKeeper.getUndoToolIcon;

/**
 * First, let's make it work, then we'll make the interface so you don't have to click undo and then click randomly.
 * TODO: make it so that the tool just works without needing to click in the canvas after selecting the tool,
 * 	just active when tool is clicked  (and rest tool to whatever it was before.
 *
 * 	TODO: add using CTRL+Z to undo, and CTRL+SHIFT+Z to redo, instead of requiring the user to click the undo and redo buttons
 * 		(but leave buttons for those who prefer to use them.
 * @author Henry Qin
 * @author Jesse Burdick-Pless
 */

public class UndoTool extends Tool {
	/**
	 * Instantiates a new delete tool.
	 */
	public UndoTool(AutomatonPane view, AutomatonDrawer drawer) {
		super(view, drawer);
	}

	/**
	 * Gets the tool tip for this tool.
	 * 
	 * @return the tool tip for this tool
	 */
	public String getToolTip() {
		return "Undo";
	}

	/**
	 * Returns the tool icon.
	 * 
	 * @return the delete tool icon
	 */
	protected Icon getIcon() {
        return getUndoToolIcon(this);
	}

	/**
	 * Returns the key stroke to switch to this tool, the D key.
	 * 
	 * @return the key stroke to switch to this tool
	 */
	public KeyStroke getKey() {
        return KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
		//return KeyStroke.getKeyStroke('u');
	}

	/**
	 * When the user clicks, we delete either the state or, if no state, the
	 * transition found at this point. If there's nothing at this point, nothing
	 * happens.
	 * 
	 * @param event
	 *            the mouse event
	 */
	public void mouseClicked(MouseEvent event) {
		//do nothing
		((AutomatonEnvironment)getDrawer().getAutomaton().getEnvironmentFrame().getEnvironment()).restoreStatus();
//		getView().repaint();
	}
}
