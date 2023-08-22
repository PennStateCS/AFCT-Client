/*
 * Code written for Master's Project by Daphne A. Norton
 * December 25, 2008
 * Version 1.1 (renamed class)
 */
 
package equivalence;

import automata.State;

/**
 * Tracks two states:  the state from one automaton 
 * has been found to be distinguishable from the state
 * from the other automaton, or two states have
 * been merged under the assumption they are equivalent.
 * Use depends on the algorithm.
 * 
 * @author Daphne A. Norton
 */
 public class StatePair {
	 
	 /** 
	  * The states from the finite automata.
	  */
	 State state1 = null;
	 State state2 = null;
	 
	 /**
	  * Constructor.  Be consistent about the order of the
	  * two automata so that lookups will work on this object.
	  * 
	  * @param firstState  A state from the first automaton
	  * @param secondState A state from the second automaton
	  */
	 StatePair(State firstState, State secondState) {
		 
		 state1 = firstState;
		 state2 = secondState;
	 }
	 

	/**
	 * Returns a string representation of the two states.
	 * 
	 * @return a string representation of this object
	 */
	public String toString() {
		return ("First State: " + state1.getName() + " (" + 
				      state1.getID() + "), Second state: " + 
				      state2.getName() + " (" + state2.getID() + ")");
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
			StatePair pair = (StatePair) object;
			return (state1 == pair.state1 && state2 == pair.state2);
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
		
		// Don't want same hash code if states are reversed.
		return (31 * state1.hashCode()) + (17 * state2.hashCode());
	}	
	
	// Getters and setters
	
	public State getState1() {
		return state1;
	}



	public void setState1(State state1) {
		this.state1 = state1;
	}



	public State getState2() {
		return state2;
	}



	public void setState2(State state2) {
		this.state2 = state2;
	}
 }
