/*
 * Code written for Master's Project by Daphne A. Norton
 * December 25, 2008
 * Version 1
 */
 
package equivalence;

import automata.State;

/**
 * Tracks two DFA states with an alphabet symbol.
 * Useful for lookups.  Subclass of StatePair,
 * with an added symbol.  Uses the same
 * equals method as its parent.
 * 
 * @author Daphne A. Norton
 */
 public class StateStateSymbol extends StatePair {
	 
	 /** 
	  * The input symbol from the alphabet
	  */
	 String symbol = "";
	 
	 /**
	  * Constructor.  Be consistent about the order of the
	  * two automata so that lookups will work on this object.
	  * 
	  * @param firstState  A state from the first automaton
	  * @param secondState A state from the second automaton
	  * @param symbol      The alphabet symbol to track
	  */
	 StateStateSymbol(State firstState, State secondState, String symbol) {
		 super(firstState, secondState);
		 this.symbol = symbol;
	 }
	 

	/**
	 * Returns a string with the names of the states and the symbol.
	 * 
	 * @return a string representation of this object
	 */
	public String toString() {
		return ("First State: " + state1.getName() + " (" + 
				      state1.getID() + "), Second state: " + 
				      state2.getName() + " (" + state2.getID() + 
				      "), Symbol: " + symbol);
	}

	
	// Getters and setters
	public String getSymbol() {
		return symbol;
	}
	

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}
 }
