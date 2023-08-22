/*
 * Code written for Master's Project by Daphne A. Norton
 * August 9, 2008
 * Version 1
 */
 
package equivalence;

import automata.State;

/**
 * Tracks a state paired with an alphabet symbol.
 * A simple, useful class for the inverse transition function.
 * 
 * @author Daphne A. Norton
 */
 public class StateAlphaPair {
	 State state = null;
	 String symbol = "";
	 
	 /**
	  * Constructor.
	  * @param state  the state the transition is going TO
	  *               (This is for the INVERSE of delta!)
	  * @param sumbol the alphabet symbol for the transition
	  */
	 StateAlphaPair(State state, String symbol) {
		 this.state = state;
		 this.symbol = symbol;
	 }
	 
	/**
	 * Returns a string representation of the state and the symbol.
	 * 
	 * @return a string representation of this object
	 */
	public String toString() {
		return ("(" + state.getName() + " (" + 
				      state.getID() + "), " + symbol + ")");
	}

	/**
	 * Returns true if this pair equals another pair.
	 * 
	 * @param object
	 *            the object to test against
	 * @return <CODE>true</CODE> if the two are equal, <CODE>false</CODE>
	 *         otherwise
	 */
	public boolean equals(Object object) {
		try {
			StateAlphaPair pair = (StateAlphaPair) object;
			return (state == pair.state
			        && symbol.equals(pair.symbol));
		} catch (ClassCastException e) {
			return false;
		}
	}

	/**
	 * Returns the hash code for this object.
	 * 
	 * @return the hash code for this pair
	 */
	public int hashCode() {
		return state.hashCode() + (31 * symbol.hashCode());
	}		 
 }
