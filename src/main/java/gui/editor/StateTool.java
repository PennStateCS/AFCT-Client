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

import automata.State;
import gui.environment.AutomatonEnvironment;
import gui.environment.EnvironmentFrame;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;

import static gui.editor.IconKeeper.getStateToolIcon;

/**
 * A tool that handles the creation of states.
 * 
 * @author Thomas Finley
 * @author Jesse Burdick-Pless
 */

public class StateTool extends Tool {
	/**
	 * Instantiates a new state tool.
	 */
	public StateTool(AutomatonPane view, AutomatonDrawer drawer) {
		super(view, drawer);
	}

	/**
	 * Gets the tool tip for this tool.
	 * 
	 * @return the tool tip for this tool
	 */
	public String getToolTip() {
		return "State Creator";
	}

	/**
	 * Returns the tool icon.
	 * 
	 * @return the state tool icon
	 */
	protected Icon getIcon() {
		return getStateToolIcon(this);
	}

	/**
	 * When the user clicks, one creates a state.
	 * 
	 * @param event
	 *            the mouse event
	 */
	public void mousePressed(MouseEvent event) {
		getView().getDrawer().showConnected = false;
		if (getDrawer().getAutomaton().getEnvironmentFrame() !=null)
    		((AutomatonEnvironment)getDrawer().getAutomaton().getEnvironmentFrame().getEnvironment()).saveStatus();
        getAutomaton().deselectStatesAndTransitions();
        getView().didBoundsSelection = false;
		state = getAutomaton().createState(event.getPoint());
        state.setSelect(true);

        initialPointClick.setLocation(event.getPoint());

		getView().repaint();
	}

	/**
	 * When the user drags, one moves the created state.
	 * 
	 * @param event
	 *            the mouse event
	 */
	public void mouseDragged(MouseEvent event) {
        ObjectSnappingHandler objectSnappingHandler = getObjectSnappingHandler();
        boolean doSnapping = objectSnappingHandler.whenMouseDragged(event, null, initialPointClick, getAutomaton(), state);
        Point p = event.getPoint();
        int x = state.getPoint().x + p.x - initialPointClick.x;
        int y = state.getPoint().y + p.y - initialPointClick.y;
        if (doSnapping) {
            Point temp = objectSnappingHandler.snapState(x, y);
            x = temp.x;
            y = temp.y;
        }
        state.getPoint().setLocation(x, y);
        state.setPoint(state.getPoint());
        objectSnappingHandler.showSnappingIndicators(getView());
        initialPointClick = p;

        //state.setPoint(event.getPoint());
		getView().repaint();
	}

    public void mouseReleased(MouseEvent event) {
        ObjectSnappingHandler objectSnappingHandler = getObjectSnappingHandler();
        objectSnappingHandler.clearSnappingIndicators(getView());
		// Prevent newly created states from overlapping with existing states
		StateOverlap.handleStateOverlap(getAutomaton());
		//state.setSelect(false);
        getView().repaint();
    }

	/**
	 * Returns the keystroke to switch to this tool, S.
	 * 
	 * @return the keystroke for this tool
	 */
	public KeyStroke getKey() {
		return KeyStroke.getKeyStroke(KeyEvent.VK_S, 0);
	}

	/** The state that was created. */
	automata.State state = null;

    /** The initial point of the click. */
    private Point initialPointClick = new Point();
}
