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

import automata.Automaton;
import automata.State;
import automata.Transition;
import gui.SuperMouseAdapter;
import gui.environment.AutomatonEnvironment;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.KeyStroke;

import static gui.editor.EditorKeyBindings.CTRL_CMD_SHORTCUT_MASK;
import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;

/**
 * The <CODE>Tool</CODE> abstract class is a type of input adapter for the
 * pane used to edit the view, and the automaton. The tool also has the ability
 * to draw on the view.
 *
 * @author Unknown, Jesse Burdick-Pless
 */

public abstract class Tool extends SuperMouseAdapter {
	/**
	 * Constructs a new tool.
	 * 
	 * @param view
	 *            the view the tool is in, useful for calling <CODE>repaint</CODE>
	 * @param drawer
	 *            the drawer of the automaton
	 */
	public Tool(AutomatonPane view, AutomatonDrawer drawer) {
		this.view = view;
		this.drawer = drawer;
		this.automaton = drawer.getAutomaton();
		this.creator = TransitionCreator.creatorForAutomaton(getAutomaton(), getView());
	}

	/**
	 * Constructs a new tool.
	 *
	 * @param view
	 *            the view the tool is in, useful for calling <CODE>repaint</CODE>
	 * @param drawer
	 *            the drawer of the automaton
	 * @param creator
	 * 	 *            the transition creator for the type of automata we are editing
	 */
	public Tool(AutomatonPane view, AutomatonDrawer drawer, TransitionCreator creator) {
		this.view = view;
		this.drawer = drawer;
		this.automaton = drawer.getAutomaton();
		this.creator = creator;
	}

	/**
	 * Returns the tool tip for this tool, modified to have the tool tip
	 * shortcut highlighted.
	 * 
	 * @return the string from <CODE>getToolTip</CODE> slightly modified
	 */
	public String getShortcutToolTip() {
		String tip = getToolTip();
		KeyStroke stroke = getKey();
		if (stroke == null)
			return tip;
		int index = findDominant(tip, (char) stroke.getKeyCode());
		if (index == -1) {
            if (stroke.getModifiers() == 0) {
                return tip + "(" + Character.toUpperCase(stroke.getKeyChar()) + ")";
            } else {
                return tip;
            }
        }
		return tip.substring(0, index) + "(" + tip.charAt(index) + ")" + tip.substring(index + 1);
	}

	/**
	 * Returns the tool tip for this tool.
	 * 
	 * @return a string containing the tool tip
	 */
	public String getToolTip() {
		return "Tool";
	}

	/**
	 * Retrieves the view.
	 * 
	 * @return the view the tool is in
	 */
	protected AutomatonPane getView() {
		return view;
	}

	/**
	 * Returns the automaton drawer.
	 * 
	 * @return the automaton drawer
	 */
	protected AutomatonDrawer getDrawer() {
		return drawer;
	}

	/**
	 * Returns the tool icon.
	 * 
	 * @return the default tool icon
	 */
	protected Icon getIcon() {
		java.net.URL url = getClass().getResource("/ICON/default.gif");
		return new javax.swing.ImageIcon(url);
	}

	/**
	 * The tool drawer, given a graphics context, draws for the tool. Most tools
	 * will have no cause to use this, though some will have certain states that
	 * they will express through some graphics.
	 * 
	 * @param g
	 *            the graphics object to draw upon
	 */
	public void draw(Graphics g) {

	}

	/**
	 * Returns the automaton.
	 * 
	 * @return the automaton
	 */
	protected Automaton getAutomaton() {
		return automaton;
	}

	/**
	 * Returns the key stroke that will activate this tool.
	 * 
	 * @return the key stroke that will activate this tool, or <CODE>null</CODE>
	 *         if there is no shortcut keystroke for this tool
	 */
	public KeyStroke getKey() {
		return false ? KeyStroke.getKeyStroke('a') : null;
	}

	/**
	 * This automatically finds the index of a character in the string for which
	 * then given character is at its most prominant. The intended use is to
	 * automatically, given a tooltip and a key shortcut, find the key in the
	 * string that should be highlighted as the shortcut for that particular
	 * tool.
	 * 
	 * @param string
	 *            the string to search for a character
	 * @param c
	 *            the character to search for in the string
	 * @return the index of the character c "at its best", or -1 if the
	 *         indicated character is not in the string
	 */
	protected static int findDominant(String string, char c) {
		int index = string.indexOf(Character.toUpperCase(c));
		if (index != -1)
			return index;
		return string.indexOf(Character.toLowerCase(c));
	}

    protected ObjectSnappingHandler getObjectSnappingHandler() {
        //Automaton test = view.getCreator().getAutomaton();
        return ((AutomatonEnvironment) getAutomaton().getEnvironmentFrame().getEnvironment()).getObjectSnappingHandler();
        //return ((AutomatonEnvironment) view.getCreator().getAutomaton().getEnvironmentFrame().getEnvironment()).getObjectSnappingHandler();
    }

	protected void showPopup(MouseEvent event) {
		if (skipNextPopup) {
			skipNextPopup = false;
			return;
		}

		System.out.println("showPopup");

		// Should we show a popup menu?
		if (event.isPopupTrigger() || event.getButton() == MouseEvent.BUTTON3) {
			Point p = getView().transformFromAutomatonToView(event.getPoint());

			State clickedState = getDrawer().stateAtPoint(event.getPoint());
			if (clickedState != null) {
				getAutomaton().deselectAllTransitions();
				if (!clickedState.isSelected() && ctrlAndShiftUp(event)) {
					getAutomaton().deselectAllStates();
					clickedState.setSelect(true);
					getView().repaint();
				}
				getDrawer().contextActions.showPopupMenu(this, p, getView(), false);
			} else {
				Transition transition = getDrawer().transitionAtPoint(event.getPoint());
				if (transition != null) {
					getAutomaton().deselectStatesAndTransitions();
					transition.isSelected = true;
					creator.editTransition(transition, event.getPoint());
				} else {
					// Open the default context-menu
					getDrawer().contextActions.showPopupMenu(this, p, getView(), true);
				}
			}
		}

		// If the event is NOT a popup trigger, but the user right-clicked (so the popup was shown anyway)
		if (!event.isPopupTrigger() && event.getButton() == MouseEvent.BUTTON3) {
			skipNextPopup = true;
		}
	}

	protected boolean ctrlAndShiftUp(InputEvent event) {
		// Check currently pressed keys
		int modifiersEx = event.getModifiersEx();
		// Check if Ctrl is NOT pressed
		boolean isCtrlUp = (modifiersEx & CTRL_CMD_SHORTCUT_MASK) == 0;
		// Check if Shift is NOT pressed
		boolean isShiftUp = (modifiersEx & SHIFT_DOWN_MASK) == 0;
		// Ctrl and Shift are both up
		return isCtrlUp && isShiftUp;
	}

	protected boolean shiftUp(InputEvent event) {
		// Check currently pressed keys
		int modifiersEx = event.getModifiersEx();
		// Check if Shift is NOT pressed
		boolean isShiftUp = (modifiersEx & SHIFT_DOWN_MASK) == 0;
		return isShiftUp;
	}

	protected boolean altKeyDown(InputEvent event) {
		// Check currently pressed keys
		int modifiersEx = event.getModifiersEx();
		// Check if ALT IS pressed
		boolean isAltDown = (modifiersEx & ALT_DOWN_MASK) != 0;
		return isAltDown;
	}


	/** The transition creator for editing transitions. */
	protected TransitionCreator creator;

	/** The view we receive events from. */
	private AutomatonPane view;

	/** The drawer of the automaton */
	private AutomatonDrawer drawer;

	/** The automaton. */
	private Automaton automaton;

	protected boolean skipNextPopup = false;
}
