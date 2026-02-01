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





package gui.environment;

import automata.State;
import automata.Transition;
import file.Codec;
import file.ParseException;
import file.XMLCodec;
import file.xml.Transducer;
import file.xml.TransducerFactory;
import gui.Globals;
import gui.editor.ObjectSnappingHandler;
import gui.editor.UndoKeeper;
import automata.Automaton;
import automata.event.AutomataStateEvent;
import automata.event.AutomataStateListener;
import automata.event.AutomataTransitionEvent;
import automata.event.AutomataTransitionListener;
import automata.event.AutomataNoteEvent;
import automata.event.AutomataNoteListener;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.Objects;

/**
 * @author Unknown
 * @author Jesse Burdick-Pless
 */
public class AutomatonEnvironment extends Environment {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

    private Automaton automaton;

	/**
	 * Instantiates an <CODE>AutomatonEnvironment</CODE> for the given
	 * automaton. By default this method will set up an environment with an
	 * editor pane for this automaton.
	 * 
	 * @param automaton
	 *            the automaton to set up an environment for
	 * @see gui.editor.EditorPane
	 */
	public AutomatonEnvironment(Automaton automaton) {
		super(automaton);
        this.automaton = automaton;
		Listener listener = new Listener();
		automaton.addStateListener(listener);
		automaton.addTransitionListener(listener);
		automaton.addNoteListener(listener);
		initUndoKeeper();
        objectSnappingHandler = new ObjectSnappingHandler();
	}

	/**
	 * Returns the automaton that this environment manages.
	 * 
	 * @return the automaton that this environment manages
	 */
	public Automaton getAutomaton() {
		return (Automaton) super.getObject();
	}
	
	/*Start undo methods*/
    public UndoKeeper getUndoKeeper(){
        return myKeeper;	
    }
    public void initUndoKeeper(){
        myKeeper = new UndoKeeper(getAutomaton());
    }
    public void saveStatus(){
        myKeeper.saveStatus();	
    }
    public void restoreStatus(){
        myKeeper.restoreStatus();	
    }
    
    public boolean shouldPaint(){
        return myKeeper == null ? true: !myKeeper.sensitive;	
    }
    
    public void setWait(){
    	myKeeper.setWait();
    }

    public void redo(){
        myKeeper.redo();
    }
	
	private UndoKeeper myKeeper;
    /*End undo methods*/

    private ObjectSnappingHandler objectSnappingHandler;

    public ObjectSnappingHandler getObjectSnappingHandler() {
        return objectSnappingHandler;
    }

    @Override
    public void handleDelete() {
        myKeeper.saveStatus();
        State[] states = automaton.getStates();

        // Delete selected states (if any)
        for (State state : states) {
            if (state.isSelected()) {
                automaton.removeState(state);
            }
        }

        // Delete selected transitions (if any)
        Transition[] transitions = automaton.getTransitions();
        for (Transition transition : transitions) {
            if (transition.isSelected) {
                automaton.removeTransition(transition);
            }
        }
    }

    @Override
    public void handleDuplicate(boolean shiftHeld) {
        myKeeper.saveStatus();
        automaton.duplicateSelected(shiftHeld);
    }

    @Override
    public void handleCopy() {
        // Get new Automaton from selection
        Automaton tempAutomaton = automaton.newAutomatonFromSelected();
        // Serialize the Automaton
        String selected = XMLCodec.serialize(tempAutomaton);
        // Record what was copied
        Globals.lastCopiedString = selected;
        Globals.lastCopiedAutomaton = tempAutomaton;
        // Put the serialized Automaton onto the clipboard
        StringSelection stringSelection = new StringSelection(selected);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    @Override
    public void handleCut() {
        handleCopy();
        handleDelete();
    }

    @Override
    public void handlePaste(boolean shiftHeld) {
        myKeeper.saveStatus();

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents;
        try {
            contents = clipboard.getContents(null);
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(this, "The clipboard is currently unavailable.",
                    "AFCT", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (contents == null) {
            JOptionPane.showMessageDialog(this, "The clipboard is empty.",
                    "AFCT", JOptionPane.ERROR_MESSAGE);
        } else if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            JOptionPane.showMessageDialog(this, "The clipboard doesn't contain an AFCT structure.",
                    "AFCT", JOptionPane.ERROR_MESSAGE);
        } else {
            try {
                String pastedText = (String) contents.getTransferData(DataFlavor.stringFlavor);

                if (!Objects.equals(pastedText, Globals.lastCopiedString)) {
                    Automaton tempAutomaton = (Automaton) XMLCodec.deserialize(pastedText);
                    Globals.lastCopiedString = pastedText;
                    Globals.lastCopiedAutomaton = tempAutomaton;
                }
                Automaton.copyBetweenAutomaton(Globals.lastCopiedAutomaton, automaton, false, shiftHeld);
            } catch (UnsupportedFlavorException | IOException e) {
                JOptionPane.showMessageDialog(this, "The clipboard doesn't contain an AFCT structure.",
                        "AFCT", JOptionPane.ERROR_MESSAGE);
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(this, "The clipboard contains an invalid AFCT structure.",
                        "AFCT", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void handleUndo() {
        myKeeper.restoreStatus();
    }

    @Override
    public void handleRedo() {
        myKeeper.redo();
    }

    @Override
    public void handleSelectAll() {
        State[] states = automaton.getStates();
        for (State state : states) {
            state.setSelect(true);
        }
    }

    /**
	 * The transition and state listener for an automaton detects if there are
	 * changes in the environment, and if so, sets the dirty bit.
	 */
	private class Listener implements AutomataStateListener,
			AutomataTransitionListener, AutomataNoteListener {
		public void automataTransitionChange(AutomataTransitionEvent e) {
			setDirty();
		}

		public void automataStateChange(AutomataStateEvent e) {
			setDirty();
		}

        public void automataNoteChange(AutomataNoteEvent e){
            setDirty();
        }
	}
}
