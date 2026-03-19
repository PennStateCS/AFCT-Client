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

import javax.swing.*;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import gui.environment.AutomatonEnvironment;
import gui.viewer.AutomatonDrawer;

import java.net.URL;
import java.util.*;

/**
 * A tool bar for editing and manipulating an automaton.
 * 
 * @author Thomas Finley
 * @author Jesse Burdick-Pless
 */

public class ToolBar extends JToolBar implements ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new tool bar.
	 * 
	 * @param view
	 *            the view the automaton is displayed in
	 * @param drawer
	 *            the automaton drawer
	 * @param box
	 *            the toolbox to get the initial tools to put in the bar
	 */
	public ToolBar(EditCanvas view, AutomatonDrawer drawer, ToolBox box) {
		super();
		adapter = new ToolAdapter(view);
		this.view = view;
		this.drawer = drawer;
		tools = box.tools(view, drawer);
		initBar();
		view.addMouseListener(adapter);
		view.addMouseMotionListener(adapter);
	}

	/**
	 * Returns the view that the automaton is drawn in.
	 * 
	 * @return the view that the automaton is drawn in
	 */
	protected Component getView() {
		return view;
	}

	/**
	 * Returns the automaton drawer for the automaton.
	 * 
	 * @return the automaton drawer for the automaton
	 */
	protected AutomatonDrawer getDrawer() {
		return drawer;
	}

	/**
	 * Initializes the tool bar.
	 */
	private void initBar() {
		ButtonGroup group = new ButtonGroup();
		JToggleButton button = null;
		Iterator<Tool> it = tools.iterator();
		KeyStroke key;
		while (it.hasNext()) {
			Tool tool = (Tool) it.next();
			button = new JToggleButton(tool.getIcon());
			buttonsToTools.put(button, tool);
			button.setToolTipText(tool.getShortcutToolTip());
			group.add(button);
			this.add(button);
			button.addActionListener(this);

            if (tool instanceof UndoTool) {
                this.undoButton = button;
                button.setEnabled(false);
            } else if (tool instanceof RedoTool) {
                this.redoButton = button;
                button.setEnabled(false);
            }

			key = tool.getKey();
			if (key == null)
				continue;
			InputMap imap = button
					.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
			ActionMap amap = button.getActionMap();
			Object o = new Object();
			imap.put(key, o);
			amap.put(o, new ButtonClicker(button));

			// Start environment with the first tool in the toolbar selected
			if (currentTool == null) {
				currentTool = tool;
				currentButton = button;
				button.setSelected(true);
				adapter.setAdapter(tool);
			}
		}
	}

    private void handleUndoRedoButtonSelection(JToggleButton button) {
        if (currentButton != null) {
            currentButton.setSelected(true);
        }
        button.setSelected(false);
    }

    private boolean handleUndoRedoHoldDelay(ActionEvent e) {
        // TODO: find a way to check if key is still held instead, or rate limit the action events somehow
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - e.getWhen() > maxUndoOrRedoOffsetTimeMillis) {
            return false;
        }
        boolean result = (currentTimeMillis - lastUndoOrRedoTimeMillis) > UndoRedoDelayMillis;
        if (result) {
            lastUndoOrRedoTimeMillis = currentTimeMillis;
        }
        return result;
    }

	/**
	 * If a tool is clicked, sets the new current tool.
	 */
	public void actionPerformed(ActionEvent e) {
		Tool tool = (Tool) buttonsToTools.get(e.getSource());
		if (tool != null) {
            if (tool instanceof UndoTool) {
                if (handleUndoRedoHoldDelay(e)) {
                    ((AutomatonEnvironment) getDrawer().getAutomaton().getEnvironmentFrame().getEnvironment()).restoreStatus();
                }
                handleUndoRedoButtonSelection((JToggleButton) e.getSource());
            } else if (tool instanceof RedoTool) {
                if (handleUndoRedoHoldDelay(e)) {
                    ((AutomatonEnvironment)getDrawer().getAutomaton().getEnvironmentFrame().getEnvironment()).redo();
                }
                handleUndoRedoButtonSelection((JToggleButton) e.getSource());
            } else {
                adapter.setAdapter(tool);
                currentTool = tool;
                currentButton = (JToggleButton) e.getSource();
                view.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
		}
		if(tool instanceof DeleteTool){
			   Toolkit toolkit = Toolkit.getDefaultToolkit();  
			   //Image image = toolkit.getImage("/JFLAP09CVS/ICON/deletecursor.gif");   
			   URL url = getClass().getResource("/ICON/deletecursor.gif");
			   Image image = getToolkit().getImage(url);
			   Point hotSpot = new Point(5,5);  
			   Cursor cursor = toolkit.createCustomCursor(image, hotSpot, "Delete");  
			   view.setCursor(cursor);
			   //Cursor hourglassCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);
			   //view.setCursor(hourglassCursor);
		}
	} 

	/**
	 * Draws the tool view.
	 * 
	 * @param g
	 *            the graphics object to draw upon
	 */
	public void drawTool(Graphics g) {
		if (currentTool == null)
			return;
		currentTool.draw(g);
	}
	
	public Tool getCurrentTool(){
		return currentTool;
	}

	/**
	 * The action that clicks a button.
	 */
	private class ButtonClicker extends AbstractAction {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public ButtonClicker(AbstractButton button) {
			this.button = button;
		}

		public void actionPerformed(ActionEvent e) {
			// only click the button when the toolbar's enabled
			if (toolbarEnabled) button.doClick();
		}

		AbstractButton button;
	}

    public void enableOrDisableUndo(boolean enable) {
        if (undoButton != null) {
            undoButton.setEnabled(enable);
        }
    }

    public void enableOrDisableRedo(boolean enable) {
        if (redoButton != null) {
            redoButton.setEnabled(enable);
        }
    }

	public void setEnabled(boolean enabled){
		toolbarEnabled = enabled;
	}

	private boolean toolbarEnabled = true;

    private JToggleButton undoButton = null;
    private JToggleButton redoButton = null;


	private Component view;

	private AutomatonDrawer drawer;

	private List<Tool> tools;

	private HashMap<JToggleButton, Tool> buttonsToTools = new HashMap<>();

	private ToolAdapter adapter;

	private Tool currentTool = null;

	private JToggleButton currentButton = null;

    private long lastUndoOrRedoTimeMillis = 0;

    // TODO allow the user to change these timings in a GUI menu
    private long UndoRedoDelayMillis = 100;

    private long maxUndoOrRedoOffsetTimeMillis = 300;
}
