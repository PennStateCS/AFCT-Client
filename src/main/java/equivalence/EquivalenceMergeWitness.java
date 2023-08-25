/*
 * Code written for Master's Project by Daphne A. Norton at RIT
 * December 28, 2008
 * Version 1
 */
 
package equivalence;

import java.util.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import java.awt.Point;
import java.io.IOException;

import automata.Transition;
import automata.State;
import automata.fsa.*;
import file.ParseException;

/**
 * This class implements the nearly linear Hopcroft/Karp algorithm to
 * compare two finite automata to determine if they recognize
 * the same language.  If desired, when the two automata are
 * inequivalent, it also outputs a 'witness' string
 * which one automaton accepts and the other does not.
 * 
 * @author Daphne A. Norton
 */

public class EquivalenceMergeWitness extends EquivalenceWitness {
	
	/**
	 * The two automata being checked for equivalence.
	 */
	 private FiniteStateAutomaton fsa[] = new FiniteStateAutomaton[2];
	 
	/**
	 * The transition function.
	 */
	private HashMap<StateAlphaPair, Vector<State>> delta = null;
	
	/**
	 * The symbols in the alphabet.
	 */
	private Vector<String> alphabet = null;
	
	/**
	 * For purposes of comparison, the symbols in the
	 * alphabet for the second automaton.
	 */
	private Vector<String> alphabet2 = null;
	
	/**
	 * A "queue" to track pairs of states to process
	 */
	private Vector<StatePair> queue = new Vector<StatePair>();
	
	/**
	 * A boolean that can be used to stop the algorithm
	 * as soon as inequivalence has been demonstrated.
	 */
	private boolean done = false;
		
	/**
	 * Tracks which alphabet symbols were used to
	 * merge which states.  Then, lookups will
	 * allow us to create the witness.
	 */
	private HashMap<StatePair, StateStateSymbol> witnessLookup = null;
	
	/**
	 * Tracks which states are accepting (final).
	 */
	private HashSet<State> finalStates = new HashSet<State>();
	
	/**
	 * The two start states for the two automata.
	 */
	private State startState[] = new State[2];
	
	/**
	 * Data structure for the disjoint sets being merged.
	 */
	private StateDisjointSets stateSets = new StateDisjointSets();

	
	/**
	 * Constructor.  Converts NFAs to DFAs before starting algorithm.
	 * 
	 * @param  fsaFirst       The first automaton to compare.
	 * @param  fsaSecond      The second automaton.
	 * @param  includeWitness Does the user want an example of a string
	 *               which one automaton accepts and the other rejects?
	 */
	public EquivalenceMergeWitness(FiniteStateAutomaton fsaFirst, 
			           FiniteStateAutomaton fsaSecond, boolean includeWitness) {
		
		super(includeWitness);
		
		// Make sure format is a DFA.
		// Just use existing JFLAP functionality to convert NFA to DFA.
		NFAToDFA converter = new NFAToDFA();

		if ((fsaFirst.getInitialState() == null) || (fsaSecond.getInitialState() == null)) {
		    hasInputError = true;
		    inputErrorMsg = "Initial state missing";
		    return;
		}

		fsa[0] = converter.convertToDFA(fsaFirst);
		fsa[1] = converter.convertToDFA(fsaSecond);

		if (produceWitness) {
			witnessLookup = new HashMap<StatePair, StateStateSymbol>();
		}
	}
	
	
	/**
	 * This method runs the Hopcroft and Karp merging algorithm to
	 * check for equivalence (plus witness string
	 * creation, if desired).
	 * 
	 * @return  whether or not the automata recognize the same language.
	 */
	public boolean areAutomataEquivalent() {
		
		if (verbose) {
			System.out.println("IMPORTANT NOTE when reading output: ");
			System.out.println("Any NFAs have been converted to DFAs; " +
					" trap states have been added, if necessary.");
			System.out.println();
		}
		if (!hasInputError) {
		    initialize();
		}
		if (!hasInputError) {
			merge();	
		}

		return areEquivalent;
	}
	
	 
	/**
	 * This method sets up the n sets, one for each state.  At the
	 * same time, to reduce looping, it performs other functions: It
	 * also tracks which states are initial, and it
	 * tracks the final (accepting) states along the way
	 * so it will be easy to determine if we have reached a stopping point.
	 * Moreover, it creates the
	 * transition function delta (extra time is required for this, as
	 * the basic algorithm assumes delta is easy to look up, but JFLAP does
	 * not normally store transitions as a transition function).
	 * The automata are compared to make sure they use the same alphabet, and
	 * a check for missing trap states is performed.   If a trap state
	 * is needed, a method is called to add one.
	 */
	 private void initialize() {
		 delta = new HashMap<StateAlphaPair, Vector<State>>();

		 // The application already has various functions to get transitions.
		 // Don't really want to call the FSAAlphabetRetriever, though, because
		 // that would go through all of the same transitions all over again,
		 // and we already have to loop to get delta.
		 alphabet = new Vector<String>();
		 alphabet2 = new Vector<String>();
		 
		 // Loop through both automata
		 for (int fsaNum = 0; fsaNum < fsa.length; fsaNum++) {
			 
			 // Loop through the states for this automaton to create sets
	 		 State[] states = fsa[fsaNum].getStates();
			 for (int i = 0; i < states.length; i++) {
				 if (fsa[fsaNum].isFinalState(states[i])) {
					finalStates.add(states[i]);
				 } 
				 if (fsa[fsaNum].isInitialState(states[i])) {
					 startState[fsaNum] = states[i];
				 }
				 stateSets.makeSet(states[i]);
			 }
			 
			 // Loop through the transitions for this automaton
			 // to get the alphabet and delta.
	 		 Transition transitions[] = fsa[fsaNum].getTransitions();
			 for (int i = 0; i < transitions.length && !done; i++) {
				FSATransition transition = (FSATransition) transitions[i];
				State fromState = transition.getFromState();
				State toState = transition.getToState();
				String label = transition.getLabel();
				
				if (!label.equals("") && !alphabet.contains(label)) {
					if (fsaNum <= 0) {
						alphabet.add(label);  // found a new alphabet symbol
						if (verbose) {
							System.out.println("Found symbol: " + label);
						}
					} else if (verbose) {
						System.out.println("Found extra symbol: " + label);
		            }           
				}
				if (!label.equals("") && !alphabet2.contains(label) &&(fsaNum > 0)) {
						alphabet2.add(label);
				}				

				StateAlphaPair deltaPair = new StateAlphaPair(fromState, label);
				if (delta.get(deltaPair) == null) {
					Vector<State> toVector = new Vector<State>();
					toVector.add(toState);
					delta.put(deltaPair, toVector);
				} else {
					Vector<State> toVector = delta.get(deltaPair);
					toVector.add(fromState);
				}	
			}
		}

		// Need to compare alphabets fully so program doesn't encounter a null
		// pointer when expecting a transition on a symbol.
		// (To avoid this, user could create a dead trap state for all transitions
		// on unused symbol(s).  However, if this scenario does occur, it
		// is likely the user is looking at 2 different alphabets overall,
		// so make them aware of it rather than continuing with the 
		// program-built trap state.)
		 if (alphabet.size() != alphabet2.size()) {
			 done = true;
			 hasInputError = true;
		 } else {
             for (String a : alphabet) {
                 if (!alphabet2.contains(a)) {
                     done = true;
                     hasInputError = true;
                     break;
                 }
             }
		 }
		 if (hasInputError) {
			 inputErrorMsg = "Different alphabets; cannot be compared.";
			 if (verbose) {
				 System.out.println(inputErrorMsg);
			 }
		 } else {
			 setUpTrapStates();
		 }
	 }
	 
	 
	 /**
	  * Make sure we are not missing any transitions
	  * because JFLAP allows missing transitions in DFAs.
	  * There is new code to create a trap state in new JFLAP version 6.4.
	  * However, that's integrated into the GUI, so just do
	  * a quick check of the number of transitions here since
	  * we already know alphabet size. 
	  * This nested loop is just O(n) if we do have to
	  * add the trap state, because we check each state once for
	  * each alphabet symbol.
	  */
	 private void setUpTrapStates() {
		 
 		 // Loop through both automata
		 for (int fsaNum = 0; fsaNum < fsa.length; fsaNum++) {
	 		 Transition transitions[] = fsa[fsaNum].getTransitions();
	 		 State states[] = fsa[fsaNum].getStates();
	 		 
 			 // Look for missing transitions, assuming this is a DFA
 			 // so the total count is actually meaningful.
	 		 if (transitions.length != alphabet.size() * states.length){
	 			 if (verbose) {
	 				 System.out.println("Adding trap state to FSA " + fsaNum);
	 			 }
	 			 
	 			 // Location (Point) of this state doesn't matter.
	 			 State deadState = fsa[fsaNum].createState(new Point(0, 0));
	 			 
	 			 // Add state to disjoint sets
	 			stateSets.makeSet(deadState);	
	 			 
	 			 // Get the updated list of states.
	 			 states = fsa[fsaNum].getStates();
	 			 	 			 
	 			 // Put missing transitions in (including dead state to itself).		 			 
				 for (int j = 0; j < alphabet.size(); j++) {
					 String a = alphabet.get(j);

					 for (int i = 0; i < states.length; i++) {
							StateAlphaPair deltaPair = new StateAlphaPair(states[i], a);
							if (delta.get(deltaPair) == null) {
								if (verbose) {
									System.out.println("Adding transition for FSA " + fsaNum +
											", symbol " + a);
								}
								
								// fix the actual automaton
								Transition trans = new FSATransition(states[i], deadState, a);
								fsa[fsaNum].addTransition(trans);
								
								// fix delta
								Vector<State> toVector = new Vector<State>();
								toVector.add(deadState);
								delta.put(deltaPair, toVector);
								
							}
					}
				 }
	 		 }
		 }
	 }
	 
	 
	 /**
	  * The main processing loop to merge states into
	  * sets which must be indistinguishable 
	  * from each other if the automata are equivalent.
	  */
	 private void merge() {

		 if (verbose) {
			 printSets();
		 }
		 
		 // Check if distinguishable on empty string.
		 if ((finalStates.contains(startState[0]) && 
				        !finalStates.contains(startState[1])) ||
				(!finalStates.contains(startState[0]) && 
						finalStates.contains(startState[1]))) {
			
			if (verbose) {
				System.out.println("One start state is final (accepting), " +
						"and the other is not, so the automata cannot " +
						"be equivalent.");
			}
			done = true;
		}
		if (!done) {
			
			 // Merge the start states and put them on the queue.
			 stateSets.union(startState[0], startState[1]);
			 StatePair startPair = new StatePair(startState[0], startState[1]);
			 queue.add(startPair);
	
			 if (verbose) {
				 System.out.println("Merged start states: " + 
						 startState[0].getName() + " and " +
						 startState[1].getName() + ".   Placed them on the queue.");
				 printSets();
			 }
		}
		 // Use counter to avoid resizing/reindexing the queue when dequeuing.
		 int counter = 0;
		 
		 // The main execution loop
		 while (queue.size() > counter && !done) {
			 StatePair currPair = queue.get(counter);
			 counter++;
			 
			if (verbose) {
				System.out.println("Dequeued " + currPair.toString());
			}
			
			// Loop through each alphabet symbol unless stopping point found.
			for (int i = 0; i < alphabet.size() && !done; i++) {
				String symbol = alphabet.get(i);
				
				// Follow the transition function to determine what 
				// states to merge next.
				StateAlphaPair stateAlpha1 = new StateAlphaPair(
						                  currPair.getState1(), symbol);
				Vector<State> pVec = delta.get(stateAlpha1);
				State p = pVec.get(0);
				
				StateAlphaPair stateAlpha2 = new StateAlphaPair(
						                   currPair.getState2(), symbol);
				Vector<State> qVec = delta.get(stateAlpha2);
				State q = qVec.get(0);
				
				// Determine which sets these states are in.
				State r1 = stateSets.findSetState(p);
				State r2 = stateSets.findSetState(q);
				
				if (!r1.equals(r2)) {
					
					// Merge.
					stateSets.union(r1, r2);
					
					StatePair newPair = new StatePair(p, q);
					queue.add(newPair);
					
					if (verbose) {
						System.out.println("Processing input symbol " + symbol);
						System.out.println("Merged: " + r1.getName() +
								" and " + r2.getName());
						printSets();
						
						System.out.println("Queued: " + newPair.toString());
					}
					
					// Track what we just did, for the witness.
					if (produceWitness) {
						StateStateSymbol newValue = new StateStateSymbol(
								         currPair.getState1(), currPair.getState2(), symbol);
						witnessLookup.put(newPair, newValue);
					}
					if ((finalStates.contains(p) && !finalStates.contains(q)) ||
							(!finalStates.contains(p) && finalStates.contains(q))) {
						
						if (verbose) {
							System.out.println("One state is final (accepting), " +
									"and the other is not, so they cannot " +
									"be equivalent.");
						}
						done = true;
						if (produceWitness) {
							createWitness(p, q);
						}						         
					}
				}
			}
		 }

		 if (!done) {
			 areEquivalent = true;
			 if (verbose) {
				 System.out.println("No accepting and non-accepting states" +
			 		" were merged, so the automata accept the same language.");
			 }
		 }
	 }
	 
	 
	 /**
	  * Called at the end of the algorithm (if the automata accept
	  * different languages) to process the saved runtime data
	  * and form a witness string.  This will be (one of) the
	  * shortest possible witness(es) when this algorithm is used.
	  * See the author's project paper for discussion.
	  * 
	  * @param  stateOne  the last state encountered for the 1st automaton.
	  * @param  stateTwo  ditto for the second automaton.
	  */
	 private void createWitness(State stateOne, State stateTwo) {
		 if (areEquivalent) {
			 if (verbose) {
				 System.out.println("Cannot produce a witness since the " +
						 "two automata accept the same language!");
			 }
		 } else {
			 if (verbose) {
				 System.out.println("Creating witness.");
			 }
			 			 
			 // Begin with the states passed in as parameters, and
			 // continue from there, following the transition functions
			 // until the start state pair is found.
			 // When that happens, we know we found a suitable string
			 // for the witness.
			 State state1 = stateOne;
			 State state2 = stateTwo;
			 
			 StateStateSymbol currMapValue = new StateStateSymbol(
					                              state1, state2, "");

			 while (!state1.equals(startState[0]) || 
					        !state2.equals(startState[1])) {

				 if (verbose) {
					 System.out.println("Processing states: " +
							 state1.getName() + " and " + state2.getName());
				 }
				 // Move backwards to the previous states.				 
				 // Use the previous value as the next key.
				 currMapValue = witnessLookup.get(currMapValue);
				 String currSymbol = currMapValue.getSymbol();

				 state1 = currMapValue.getState1();
				 state2 = currMapValue.getState2();
				 
				 if(verbose) {
					 System.out.println("Found states " + state1.getName() + " " + state2.getName());
					 System.out.println("Processing symbol: " + currSymbol);
				 }
				 
				 
				 if (currSymbol != null) {
					 witness.insert(0, currSymbol); // add to beginning of witness
				 } else {
					 System.err.print("Missing input symbol ");
					 if (state1 != null && state2 != null) {
						 System.err.println("for states " +  state1 +
								 " and " + state2);
					 } else {
						 System.err.println();
					 }
				 }
			 }		 
		 }
	 }
	 
	 
	 /**
	  * Print out the merged sets that currently exist.
	  */ 
	 public void printSets() {
		 System.out.println();
		 System.out.print(stateSets.toString());		 
		 System.out.println();
	 }
	 
 
	 /**
	  * Print out the transition function.
	  */
	 public void printDelta() {
		 System.out.println();
		 System.out.println("The transition function, delta: ");
		 
		 if (delta == null || delta.size() <= 0) {
			 System.out.println("Transition function was not constructed.");
			 
		 } else {
		 
			 // iterate over map.
			 Iterator<StateAlphaPair> iterator = delta.keySet().iterator();
			 while (iterator.hasNext()) {
				 StateAlphaPair pair = iterator.next();
				 Vector<State> toVector = delta.get(pair);
				 System.out.print(pair + ":  ");
				 for (int i = 0; i < toVector.size(); i++) {
					 if (i > 0) {
						 System.out.print(", ");
					 }
					 State currState = toVector.get(i);
					 System.out.print( currState.getName() + " (" + 
					                  currState.getID() + ")");
				 }				
				 System.out.println();
			 }
		}
	}
	 
	 
	 /**
	  * Print the data stored to look up the witness.
	  */
	 public void printWitnessLookup() {
		 System.out.println();
		 System.out.println("Data available to construct the witness:");
		 
		 for(StatePair key: witnessLookup.keySet()) {
			 System.out.println(key.toString() + ":\n   " + 
					 witnessLookup.get(key).toString());
		 }
	 }
	 
	
	/**
	 * Main method - for testing equivalence of
	 * the automata in 2 files (pass in the filenames
	 * as the two parameters, plus two optional
	 * parameters -- first "true/false" if the witness
	 * is to be produced/not, then "true/false" if verbose mode
	 * is desired/not).
	 */
	 public static void main(String[] args) {
		 if (args.length >= 2 && args.length <= 4) {
		
			 try {
			 	 // get the automata from the files
				 FiniteStateAutomaton fa1 = EquivalenceMergeWitness.readFAFile(args[0]);
				 FiniteStateAutomaton fa2 = EquivalenceMergeWitness.readFAFile(args[1]);
				 
				 boolean wantWitness = true;
				 boolean wantVerbose = false;
				 
				 if (args.length >= 3 && args[2].equalsIgnoreCase("false")) {
					 wantWitness = false;
				 }

				 // test for equivalence
				 EquivalenceMergeWitness equiv = 
					          new EquivalenceMergeWitness(fa1, fa2, wantWitness);
				 if (args.length == 4 && args[3].equalsIgnoreCase("true")) {
					 wantVerbose = true;
					 equiv.setVerbose(true);
				 }
				 boolean result = equiv.areAutomataEquivalent();
				 System.out.println("Result is: " + result);

				 if (wantWitness) {
					 equiv.printWitness();
				 }				 
				 if (wantVerbose) {
					 equiv.printWitnessLookup();
					 equiv.printDelta();
		 			 equiv.printSets();
				 }
			 } catch (ParseException pe) {
				 System.err.println("Problem parsing the file: " + pe);
			 } catch (IOException ioe) {
				 System.err.println("Problem reading file: " + ioe);
 			 } catch (Exception e) {
				 System.err.println("Program failed with general exception: " + e);
			 }

		} else {
		    System.out.println("Usage:  java equivalence.EquivalenceMergeWitness " +
		    		"filename1 filename2 [true|false] [true|false]");
		    System.out.println("...where the two booleans are: ");
		    System.out.println("1. false means no witness is produced.");
		    System.out.println("   The default is to include a witness (true).");
		    System.out.println("2. true means verbose mode.");
		    System.out.println("   The default is to minimize output to standard out (false).");
		}
	 }
}
