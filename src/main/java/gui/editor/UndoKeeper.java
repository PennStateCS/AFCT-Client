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

import gui.environment.Universe;
import gui.environment.Profile;

import java.awt.*;
import java.util.Deque;
import java.util.LinkedList;

import automata.Automaton;
import automata.turing.TuringMachine;
import debug.EDebug;

/**
 * This class will store the states between actions, that we may undo them.
 * Since there should be one set of states per active window, this class should be instantiated
 * within the AutomatonFrame class.
 * 
 * Let's use it statically for now (testing / prototype), so we don't have to change environment frame to instantiate it.
 * @author Henry Qin
 * @author Jesse Burdick-Pless
 */
public class UndoKeeper { 
    	
    private Automaton myMaster;	
    
    private Deque<Automaton> myDeck;
    private Deque<Automaton> myBackDeck;

    //private final int DEFAULT_NUM = 50;

    private int numUndo;

    private Automaton initialState;
    private int initialUndos = 0;

    public UndoKeeper(Automaton master){
    	myMaster = master;
    	myDeck = new LinkedList<Automaton>();
    	myBackDeck = new LinkedList<Automaton>();
        numUndo = Universe.curProfile.undo_num;
        //initialState = (Automaton) myMaster.clone();
    }

    public void setNumUndo(int nn){
        numUndo = nn;
    }
	
	public boolean sensitive = false;
	private boolean wait = false;
	
    public void setWait(){
    	wait = true;
    }
	
    public void saveStatus(){
//        EDebug.print("I have been called upon");
        if (wait){
        	wait = false;
        	return;
        }
    	
//        EDebug.print("\nFirst place");
//        for (int i = 0; i < myDeck.size(); i++)
//            EDebug.print(((LinkedList)myDeck).get(i).hashCode());

//        if (myDeck.size() > 0)
//            EDebug.print("The top of deck hash is " + myDeck.peek().hashCode());

        myDeck.push((Automaton)myMaster.clone()); //push on head
    	    
//        EDebug.print("The master that is getting pushed on has hascode = " + myMaster.hashCode());
        //System.out.println();

//        EDebug.print("Second place");
//        for (int i = 0; i < myDeck.size(); i++)
//            EDebug.print(((LinkedList)myDeck).get(i).hashCode());
//
//        EDebug.print("\n");
        

        if (myDeck.size() >= 2)
        {
        	Automaton first = myDeck.pop();
        	Automaton second = myDeck.pop();
//            EDebug.print("The first is " + first.hashCode() + "While the second is " + second.hashCode());
        	if (first.hashCode() == second.hashCode()){
        	    myDeck.push(first);	
        	}
        	else{
        	    myDeck.push(second);	
        	    myDeck.push(first);	
                myBackDeck.clear();
        	}
        }

//        EDebug.print("Third place");
//        for (int i = 0; i < myDeck.size(); i++)
//            EDebug.print(((LinkedList)myDeck).get(i).hashCode());
//        EDebug.print(myDeck.size());

        // Disable undo limit if numUndo=-1
        if (numUndo >= 0) {
            while (myDeck.size() > numUndo) myDeck.removeLast();
        }

        updateUndoRedoButtonState();
    }

    /*Undo*/
    public void restoreStatus(){
//        EDebug.print("I am mucking with that data structure.");
    	if (myDeck.size() == 0) return;
    	
    	Automaton p = null;
        while (myDeck.size() > 0 && (p = myDeck.pop()).hashCode() == myMaster.hashCode());
        
        
        
//        EDebug.print("Master's hash is " + myMaster.hashCode());
//        EDebug.print("Top hash is " + p.hashCode());
        
        if (myDeck.size() == 0 && p.hashCode() == myMaster.hashCode()) {
            checkIfInInitialState();
            return;
        }
    	
		sensitive = true;
        myBackDeck.push((Automaton) myMaster.clone());

        if (myMaster instanceof TuringMachine)
            TuringMachine.become((TuringMachine) myMaster,(TuringMachine) p);
        else
            Automaton.become(myMaster, p); //pop off head

		sensitive = false;
		myMaster.getEnvironmentFrame().repaint();

        checkIfInInitialState();
        updateUndoRedoButtonState();
    }

    public void redo(){
//        EDebug.print("I am mucking with that data structure.");
        if (myBackDeck.size() == 0) return;
//        EDebug.print("Back deck is not empty.");
//
        myDeck.push((Automaton)myMaster.clone()); //push on head

        if (myMaster instanceof TuringMachine)
            TuringMachine.become((TuringMachine)myMaster, (TuringMachine)myBackDeck.pop()); //casting to the high heavens
        else
            Automaton.become(myMaster, myBackDeck.pop());

		myMaster.getEnvironmentFrame().repaint();
        checkIfInInitialState();
        updateUndoRedoButtonState();
    }

    public void updateInitialState(){
        //initialState = (Automaton) myMaster.clone();
        initialUndos = myDeck.size();
    }

    private boolean isInInitialState(){
//        int initHash = initialState.hashCode();
//        int currHash = myMaster.hashCode();
//
//        return initHash == currHash;
        //return initialState.equals(myMaster);
        return myDeck.size() == initialUndos;
    }

    private void checkIfInInitialState(){
        if (isInInitialState()) {
            myMaster.getEnvironmentFrame().getEnvironment().clearDirty();
        }
    }

    private void updateUndoRedoButtonState(){
        Component pane = myMaster.getEnvironmentFrame().getEnvironment().getActive();
        if (pane instanceof EditorPane){
            EditorPane ep = (EditorPane)myMaster.getEnvironmentFrame().getEnvironment().getActive();
            ep.toolbar.enableOrDisableUndo(!myDeck.isEmpty());
            ep.toolbar.enableOrDisableRedo(!myBackDeck.isEmpty());
        }
    }
}
