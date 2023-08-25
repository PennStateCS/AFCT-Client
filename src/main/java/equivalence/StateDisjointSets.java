/*
 * Code written for Master's Project by Daphne A. Norton
 * December 27, 2008
 * Version 1
 */
 
package equivalence;

import automata.State;
import java.util.HashMap;
import java.util.Vector;

/**
 * Disjoint sets consisting of the provided states.  
 * See Cormen, Leiserson, Rivest, and Stein,
 * Introduction to Algorithms, 2nd ed., p. 508 for the original pseudocode.
 * Their state elements are just ints, so this is a bit more complex.
 * 
 * @author Daphne A. Norton
 */
 public class StateDisjointSets {
	 
	 /** 
	  * A mapping of the states from the finite automata
	  * to their arbitrary index numbers.  HashMap gets
	  * and puts are constant-time operations, per the Sun JavaDoc.
	  */
	 private HashMap<State, Integer> stateToIndex = new HashMap<State, Integer>();
	 
	 /**
	  * The reverse:  A way to look up a state by its index number.
	  */
	 private Vector<State> indexToState = new Vector<State>();
	 
	 /**
	  * An "array" (here, a Vector, so it can grow as we add states)
	  * containing the parent of each state.
	  */
	 private Vector<Integer> parent = new Vector<Integer>();
	 
	 /**
	  * A Vector with the rank (max possible node height) of each state.
	  */
	 private Vector<Integer> rank = new Vector<Integer>();
	 
	 /**
	  * The next available index (same as the number of states
	  * added so far).
	  */
	 private int counter = 0;
	 
	 
	 /**
	  * Constructor.  
	  */
	 public StateDisjointSets() {
		 // do nothing
	 }

	 
	 /**
	  * Add a new state to the disjoint sets.  
	  * Creates a set of size one with the element as its own parent
	  * and the rank set to zero.  All states 
	  * in the automata under consideration should
	  * be added via this method before running the algorithm
	  * contained in the other methods.
	  * 
	  * @param  state  The state to add.
	  */
	 public void makeSet(State state) {
		 
		 // Make sure we can track the actual state.
		 stateToIndex.put(state, counter);
		 indexToState.add(state); // automatically adds to the next available slot
		 
		 // Create the parent and rank for the tree node
		 parent.add(counter);
		 rank.add(0);
		 
		 counter++;
	 }
	

	 /**
	  * Combine the sets containing two specific states, by rank.
	  * 
	  * @param  state1  The first state
	  * @param  state2  The second state
	  */
	 public void union(State state1, State state2) {
		 link(findSetIndex(state1), findSetIndex(state2));
	 }

	 
	 /**
	  * Find the state at the root of the tree (set) containing a given
	  * state, and do path compression along the way.
	  * 
	  * @param  state  the state whose set we're looking for
	  * @return        the root state
	  */
	 public State findSetState(State state) {
		 int index = stateToIndex.get(state);
		 return indexToState.get(findSet(index));
	 }

	 
	 /**
	  * Find the index of the root of the tree (set) containing a given
	  * state, and do path compression along the way.
	  * 
	  * @param  state  the state whose set we're looking for
	  * @return        the root index
	  */
	 private int findSetIndex(State state) {
		 int index = stateToIndex.get(state);
		 return findSet(index);
	 }
	 
	 
	 /**
	  * Find the index of the root of the tree (set) containing a given
	  * state, and do path compression along the way.
	  * 
	  * @param  currIndex  the current state index we're looking for
	  * @return        the root index
	  */
	 private int findSet(int currIndex) {
		 Integer currParent = parent.get(currIndex);
		 int parentVal = currParent.intValue();
		 if (currIndex != parentVal) {
			 parentVal = findSet(parentVal);
			 parent.set(currIndex, parentVal);
		 }
		 return parentVal;
	 }
	 
	 
	 /**
	  * Do the actual work of combining sets.  Do
	  * union by rank.
	  * 
	  * @param  root1  index of root of first set
	  * @param  root2  index of root of second set  
	  */
	 private void link(int root1, int root2) {
		 Integer rank1 = rank.get(root1);
		 Integer rank2 = rank.get(root2);
		 
		 if (rank1 > rank2) {
			 parent.set(root2, root1);
		 } else {
			 parent.set(root1, root2);
			 
			 if (rank1.equals(rank2)) {
				 rank2++;
				 rank.set(root2, rank2);
			 }	 
		 }
	 }
	 
	 
	 /**
	  * Get a string displaying the disjoint sets.
	  * 
	  * @return  a String representation of this object.
	  */
	 public String toString() {
		 StringBuilder buff = new StringBuilder("The disjoint sets:\n\n");
		 
		 for (int i = 0; i < indexToState.size(); i++) {
			buff.append("State " + i + " (" + indexToState.get(i).getName() +
					"): Parent " + parent.get(i) + "\n");
			
		 }
		 return buff.toString();
	 }
 }