/*
 * Code written for Master's Project by Daphne A. Norton at RIT
 * December 27, 2008
 * 
 * Version 2.0 - incorporating conversion from NFA to DFA using
 * existing code, and handling missing trap state scenario so there are no
 * missing transitions when forming witness via delta.
 * 
 * Version 2.1 - adding boolean to turn verbose mode on/off.
 * 
 * Version 2.2 - reordering loop; replacing some Vectors with HashSets
 * for constant time removes; using EquivalenceWitness superclass.
 */
 
package equivalence;

import java.util.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import java.awt.Point;
import java.io.IOException;

import automata.Automaton;
import automata.Transition;
import automata.State;
import automata.fsa.*;
import file.ParseException;

/**
 * This class implements the n lg n Hopcroft algorithm to
 * compare two finite automata to determine if they recognize
 * the same language.  If desired, when the two automata are
 * inequivalent, it also outputs a 'witness' string
 * which one automaton accepts and the other does not.
 * 
 * @author Daphne A. Norton
 */

public class EquivalenceNlgNWitness extends EquivalenceWitness {
	
	/**
	 * The two automata being checked for equivalence.
	 */
	 private FiniteStateAutomaton fsa[] = new FiniteStateAutomaton[2];
	 
	/**
	 * The current partitioning of the states.  Contains HashSets
	 * because remove operations will then only take constant time.
	 * The outer vector number corresponds to the block (set) number.
	 */ 
	 private Vector<HashSet<State>> partitionSets = null;
	 
	/**
	 * The two start states and which partition sets they are in.
	 */
	private State startState[] = new State[2];
	private int startStatePartitionSets[] = {-1, -1};
	
	/**
	 * The quantity of partition sets so far.
	 * This is one less than the counter in the original
	 * pseudocode because the index starts with 0, not 1.
	 */
	private int partitionSetCounter = 2;
	
	/**
	 * The inverse transition function.
	 */
	private HashMap<StateAlphaPair, Vector<State>> deltaInverse = null;
	
	/**
	 * The transition function.  Only used if witness is needed.
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
	 * The sets a(i) with predecessors.
	 * It contains one entry for each input symbol in
	 * the alphabet.  Each entry consists of a vector
	 * (numbered by partition set number) containing a
	 * HashSet of states, if any.
	 */
	private Vector<HashSet<State>> ai[] = null;
	
	/**
	 * Lists L(a) of sets to process for each symbol a.
	 */
	private Vector<Integer> La[] = null;
	
	/**
	 * To check if initial values were put into La yet.
	 */
	private boolean isInitialized = true;
	
	/**
	 * A "queue" to conveniently track which alphabet symbols have
	 * values in La.
	 */
	private Vector<Integer> unprocessed = null;
	
	/**
	 * A boolean that can be used to stop the algorithm
	 * as soon as inequivalence has been demonstrated.
	 */
	private boolean done = false;
	
	/**
	 * Tracks which alphabet symbols were used to
	 * distinguish which states.  Then, used as
	 * a lookup table to create the witness.
	 */
	private HashMap<StatePair, String> witnessLookup = null;
		
	/**
	 * Track the current block number where each state 
	 * within the partition resides.
	 */
	private HashMap<State, Integer> currBlock = null;

	/**
	 * Constructor.  Converts NFAs to DFAs before starting algorithm.
	 * 
	 * @param  fsaFirst       The first automaton to compare.
	 * @param  fsaSecond      The second automaton.
	 * @param  includeWitness Does the user want an example of a string
	 *               which one automaton accepts and the other rejects?
	 */
	public EquivalenceNlgNWitness(FiniteStateAutomaton fsaFirst, 
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
		
		currBlock = new HashMap<State, Integer>();
		
		if (produceWitness) {
			delta = new HashMap<StateAlphaPair, Vector<State>>();
			witnessLookup = new HashMap<StatePair, String>();
		}
	}
	
	
	/**
	 * This method runs the n lg n Hopcroft algorithm to
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
		    initializePartition();  // do this first as it checks initial states
		
		    if (!done) {
			constructDeltaInverse();	
			if (!done) {
			    initializePredecessorSetsAndLists();
			    createPredecessorSets(0);
			    createPredecessorSets(1);
			    updateProcessingLists(0, 1);
			    isInitialized = true;  // the processing lists were updated once
			    distinguish();
			}
		    }
		}
		return areEquivalent;
	}
	
 
	/**
	 * This method creates two initial partition sets, one containing
	 * the final (accepting) states of both automata, and one containing
	 * the rest of the states.  The initial (start) states are tracked.
	 */
	 private void initializePartition() {
		 partitionSets = new Vector<HashSet<State>>(); // Contains all partitions
		 HashSet<State> partition0 = new HashSet<State>(); // Final states
		 HashSet<State> partition1 = new HashSet<State>(); // Non-final states

		 // Loop through both automata
		 for (int fsaNum = 0; fsaNum < fsa.length; fsaNum++) {
	 		 State[] states = fsa[fsaNum].getStates();// Begin with first FSA
			 for (int i = 0; i < states.length; i++) {

				 if (fsa[fsaNum].isFinalState(states[i])) {
					 partition0.add(states[i]);
					 currBlock.put(states[i], 0);
					 if (fsa[fsaNum].isInitialState(states[i])) {
						 startState[fsaNum] = states[i];
						 startStatePartitionSets[fsaNum] = 0;
					 }
						 
				 } else {
					 partition1.add(states[i]);
					 currBlock.put(states[i], 1);
					 if (fsa[fsaNum].isInitialState(states[i])) {
						 startState[fsaNum] = states[i];
						 startStatePartitionSets[fsaNum] = 1;
					 }
				 }
			 }
		 }

		 partitionSets.add(partition0);
		 partitionSets.add(partition1);
		 
		 if (startStatePartitionSets[0] != startStatePartitionSets[1]) {
			 if (verbose) {
				 System.out.println("One of the start states is accepting, " +
				   "and the other is not.  The automata are not equivalent because " +
				   "one accepts the empty string, and the other does not.");
			 }
			 done = true;
		 }
	 }
	 
	 
	/**
	 * This method constructs a table for the inverse transition function:
	 * deltaInverse(s, a) = {t | delta(t, a) = s}.
	 * It also creates a vector of the symbols in the alphabet as it runs.
	 * If the witness string is being requested, delta is also created here
	 * to save time, and a check for missing trap states is performed.
	 */
	 private void constructDeltaInverse() {
		 deltaInverse = new HashMap<StateAlphaPair, Vector<State>>();
		 alphabet = new Vector<String>();
		 alphabet2 = new Vector<String>();
		 
		 // The application already has various functions to get transitions.
		 // Don't really want to call the FSAAlphabetRetriever, though, because
		 // that would go through all of the same transitions all over again.
		 
 		 // Loop through both automata
		 for (int fsaNum = 0; fsaNum < fsa.length; fsaNum++) {
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
				StateAlphaPair pair = new StateAlphaPair(toState, label);
				if (deltaInverse.get(pair) == null) {
					Vector<State> fromVector = new Vector<State>();
					fromVector.add(fromState);
					deltaInverse.put(pair, fromVector);
				} else {
					Vector<State> fromVector = deltaInverse.get(pair);
					fromVector.add(fromState);
				}
				
				// Also store the regular transition function while
				// we're at it, if we're going to need it for quick
				// lookup to get the witness.
				if (produceWitness) {
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
		 }
		 
		 if (!hasInputError && produceWitness) {
			 setUpTrapStates();
		 }

	 }
	 
	 
	 /**
	  * Make sure we are not missing any transitions
	  * if we create the delta lookup for the witness,
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
	 			 
	 			 // Get the updated list of states.
	 			 states = fsa[fsaNum].getStates();
	 			 
	 			 // Add dead state to non-accepting block.
	 			 partitionSets.get(1).add(deadState);
	 			 currBlock.put(deadState, 1);
	 			 
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
								
								// fix delta inverse
								StateAlphaPair pair = new StateAlphaPair(deadState, a);
								if (deltaInverse.get(pair) == null) {
									Vector<State> fromVector = new Vector<State>();
									fromVector.add(states[i]);
									deltaInverse.put(pair, fromVector);
								} else {
									Vector<State> fromVector = deltaInverse.get(pair);
									fromVector.add(states[i]);
								}
								
							}
					}
				 }
	 		 }
		 }
	 }
	 
	 
	 /**
	  * Initialize the array ai to hold vectors of predecessor sets
	  * for each symbol in the alphabet and partition set.  Initialize
	  * processing lists L(a) for each symbol in the alphabet.
	  * Initialize "queue" unprocessed to track symbols with data
	  * in L(a) to process.
	  */
	 @SuppressWarnings("unchecked")
	private void initializePredecessorSetsAndLists() {
		 ai = new Vector[alphabet.size()];
		 La = new Vector[alphabet.size()];
		 unprocessed = new Vector<Integer>();
		 
		 for (int j = 0; j < alphabet.size(); j++) {
			 Vector<HashSet<State>> temp = new Vector<HashSet<State>>();
			 ai[j] = temp;
			 Vector<Integer> temp2 = new Vector<Integer>();
			 La[j] = temp2;
		 }
	 }
	 
	 
	 /**
	  * Create the set of states a(i) from partition set Bi (where 
	  * partitionSetNum is i) which has predecessors via input
	  * a, for each input a in the alphabet.  Specifically, find
	  * a(i) = {s | s is in Bi and deltaInverse(s, a) is not
	  * the empty set}.
	  * 
	  * This function also updates the block numbers
	  * to save an extra iteration through the states.
	  * 
	  * @param   partitionSetNum  the block to find sets from
	  */
	 private void createPredecessorSets(int partitionSetNum) {
		 for (int k = 0; k < alphabet.size(); k++) {
			 String a = alphabet.get(k);
			 HashSet<State> statesWithPredecessors = new HashSet<State>();
			 HashSet<State> partitionSet = partitionSets.get(partitionSetNum);
			 for (State currState: partitionSet) {
				 currBlock.put(currState, partitionSetNum); // update block #
				 if (deltaInverse.get(new StateAlphaPair(currState, a)) != null) {
					 statesWithPredecessors.add(currState);				 
				 }
			 }
			 // Save the set of states - need to grow the vector first
			 while (ai[k].size() <= partitionSetNum) {
				 ai[k].add(new HashSet<State>());
			 }
			 ai[k].set(partitionSetNum, statesWithPredecessors);
		 }
	 }
	 
	 
	 /**
	  * For each symbol a in the alphabet, pick the smaller
	  * set from the two partition sets and put it on appropriate list to
	  * be processed.
	  * L(a) = L(a) union {partitionNum1} if partitionNum1 is not in L(A) and 
	  *                      0 < |a(partitionNum1)| <= |a(partitionNum2)|.
	  *                      (Don't check for size zero if uninitialized.)
	  * L(a) = L(a) union {partitionNum2} otherwise.
	  * 
	  * Also put the index of the alphabet symbol into an "unprocessed"
	  * queue (Vector) to pick which symbol to process next until the queue
	  * is empty.  The algorithm does not specify how to implement this,
	  * and this approach should be efficient (no searching).
	  * 
	  * @param partitionNum1    the first partition set (block)
	  * @param partitionNum2    the other block to compare
	  */
	 private void updateProcessingLists(int partitionNum1, int partitionNum2) {
		 for (int j = 0; j < alphabet.size(); j++) {
			 HashSet<State> set1 = ai[j].get(partitionNum1);
			 HashSet<State> set2 = ai[j].get(partitionNum2);
			 if (set1.size() <= set2.size() && 
					 (!set1.isEmpty() || !isInitialized) &&
					 !La[j].contains(partitionNum1)) {
				 La[j].add(partitionNum1);
			 } else {
				 La[j].add(partitionNum2);
			 }
			 unprocessed.add(j);
		 }		 
	 }
	 
	 
	 /**
	  * The main processing loop to break states into
	  * sets which are distinguishable from each other.
	  */
	 private void distinguish() {
		 
		 // Detailed output to see current status
		 if (verbose) {
			 System.out.println("Values to start distinguishing with: ");
			 printPartition();
			 printPredecessorSets();
			 printProcessingLists();
		 }
		 
		 // Keep going until L(a) is the empty set for all a
		 // (nothing more to process) or until the start
		 // states are placed in different partition sets
		 // and we are done.
		 while (unprocessed.size() > 0 && !done) {
			 
			 // Get the next alphabet symbol with something to process.
			 Integer symbolNumInteger = unprocessed.remove(0);
			 int symbolNum = symbolNumInteger.intValue();
			 String symbol = alphabet.get(symbolNum);
			 
			 // Get the first item to process from that alphabet symbol's list.
			 Integer processNumInteger = La[symbolNum].remove(0);
			 int processNum = processNumInteger.intValue();
			 
			 // Identify the blocks which can be split.
			 Vector<Integer> blocksToSplit = new Vector<Integer>();
			 HashMap<Integer, HashSet<State>> blockSplitStates = 
				               new  HashMap<Integer, HashSet<State>>();
			 HashMap<Integer, HashSet<State>> blockUnsplitStates = 
	               new  HashMap<Integer, HashSet<State>>();
			 
			 // Check each state s in a(i):
			 HashSet<State> currAi = ai[symbolNum].get(processNum);
			 Iterator<State> aiIterator = currAi.iterator();
			 
			 while (aiIterator.hasNext()) {
				 State aiState = aiIterator.next();
				 
				 // Use inverse lookup table for delta to get the predecessor
				 // state t.
				 StateAlphaPair pair = new StateAlphaPair(aiState, symbol);
				 Vector<State> inverseStates = deltaInverse.get(pair);
				 
				 if (verbose) {
					 System.out.println("Going through predecessor " +
							 "states for symbol " + symbol
							 + " for state " + aiState.getName());
				 }
				 
				 // Find the block where t resides so we know this
				 // block can be split.
				 for (int stateNum = 0; stateNum < inverseStates.size(); stateNum++) {
					 State currStateT = inverseStates.get(stateNum);
					 Integer blockNum = currBlock.get(currStateT);
					 
					 if (verbose) {
						 System.out.println("Found state " + currStateT.getName() +
								 " in partition set " + blockNum);
					 }
				
					 HashSet<State> splitSet = new HashSet<State>();
					 HashSet<State> unsplitSet;
					 
					 if (!blocksToSplit.contains(blockNum)) {
						 blocksToSplit.add(blockNum);			 
						 splitSet.add(currStateT);						 
						 blockSplitStates.put(blockNum, splitSet);
						 unsplitSet = new HashSet<>(partitionSets.get(blockNum));
						 unsplitSet.remove(currStateT);
						 blockUnsplitStates.put(blockNum, unsplitSet);
					 } else {
						 splitSet = blockSplitStates.get(blockNum);
						 splitSet.add(currStateT);
						 blockSplitStates.put(blockNum, splitSet);
						 unsplitSet = blockUnsplitStates.get(blockNum);
						 unsplitSet.remove(currStateT);
						 blockUnsplitStates.put(blockNum, unsplitSet);
					 }
				 }
			 }
			 
			 // Loop through the blocks to split (unless done)
			 for (int blockIter = 0; blockIter < blocksToSplit.size() && !done; blockIter++) {
				 
				 int j = blocksToSplit.get(blockIter);
				 HashSet<State> splitStates = blockSplitStates.get(j);
				 HashSet<State> unsplitStates = blockUnsplitStates.get(j);
				 
				 // Detailed output
				 if (verbose) {
					 System.out.println("Looping thru symbol " + symbol +
					      " processing number " + processNum + 
					      " partiton set " + j );
				 }
				 
				 // The new partitioning is only used if something changed.
				 HashSet<State> existingPartitionSet = partitionSets.get(j);
				 
				 if (splitStates.size() < existingPartitionSet.size()) {		
					 partitionSets.set(j, splitStates);                    // replace Bj
					 partitionSets.add(partitionSetCounter, unsplitStates);// add Bk
					 
					 // track the changes
					 if (produceWitness) {
						 updateWitnessData(symbol, splitStates, unsplitStates);
					 }
					 
					 // Check if we have split the start states into 
					 // separate, distinguishable partition sets.  If
					 // so, halt algorithm to save time.
					 if (unsplitStates.contains(startState[0])) {
						 startStatePartitionSets[0] = partitionSetCounter;
					 }
					 if (unsplitStates.contains(startState[1])) {
						 startStatePartitionSets[1] = partitionSetCounter;
					 }
					 if (startStatePartitionSets[0] != startStatePartitionSets[1]) {
						 if (verbose) {
							 for (int k = 0; k < startState.length; k++) {
								 System.out.println("Start state " + startState[k].getName() +
								      " is in partition set " + startStatePartitionSets[k]);
							 }
							 System.out.println();
							 System.out.println("Halting the algorithm:  ");
							 System.out.print("The start states are not equivalent.  ");
							 System.out.print("Therefore, the automata do not ");
							 System.out.println("accept the same language.");
						 }
						 done = true;
						 
						 if (produceWitness) {
							 createWitness();
						 }
					 }
					 if (!done) {
						 // For each a in the alphabet, construct a(j) and a(k).
						 // This function also updates the block numbers
						 // to save an extra iteration through the states.
						 createPredecessorSets(j);
						 createPredecessorSets(partitionSetCounter);
						 
						 // Update the lists L(a) of items to process.
						 updateProcessingLists(j, partitionSetCounter);
						 
						 // Increment count of partition sets.
						 partitionSetCounter++;
						 
						 // Output for testing
						 if (verbose) {
							 printPartition();
				 			 printPredecessorSets();
				 			 printProcessingLists();
						 }
					 }
				 } 
			 }
		 } // end main while loop
		 
		 // Verify if distinguished the start states from each other, or not.
		 if (!done && verbose) {
			 for (int k = 0; k < startState.length; k++) {
				 System.out.println("Start state " + startState[k].getName() +
				      " is in partition set " + startStatePartitionSets[k]);
			}
		 }
		 if (startStatePartitionSets[0] == startStatePartitionSets[1]) {
			 areEquivalent = true;
			 if (verbose) {
				 System.out.println("The start states are equivalent, so " +
				                    "the automata accept the same language.");
			 }
		 }
	 }
	 
	 
	 /**
	  * Track the latest changes to the partitioning.
	  * This way, it will be feasible to reconstruct a
	  * string that distinguishes the two automata.
	  * 
	  * @param symbol        The alphabet symbol used to distinguish 
	  *                      the two separate partition sets.
	  * @param partitionSet1 A new partition set (block).
	  * @param partitionSet2 The other new partition set (block).
	  */
	 private void updateWitnessData(String symbol, HashSet<State> partitionSet1, 
			                                   HashSet<State> partitionSet2) {
		 // Find states newly distinguished from each other.
		 // Don't bother tracking pairs of states from the same
		 // automaton.  This will save storage space.
		 // Keep order of automata consistent.
		 for (State stateOne : partitionSet1) {
 			 Automaton aOne = stateOne.getAutomaton();
			 
			 for (State stateTwo : partitionSet2) {
				 Automaton aTwo = stateTwo.getAutomaton();

				 if (aOne.equals(fsa[0]) && aTwo.equals(fsa[1])) {
					 witnessLookup.put(
							 new StatePair(stateOne, stateTwo), 
							 symbol);

				 } else if (aTwo.equals(fsa[0]) && aOne.equals(fsa[1])) {
					 witnessLookup.put(
							 new StatePair(stateTwo, stateOne), 
							 symbol);
				 } 
			 }
		 }
	 }
	 
	 
	 /**
	  * Called at the end of the algorithm (if the automata accept
	  * different languages) to process the saved runtime data
	  * and form a witness string.  Essentially, this just
	  * follows the algorithm in reverse by running the transition
	  * function delta on the data (instead of delta inverse,
	  * which was used as the algorithm ran and the data was 
	  * created).  See the author's project paper for discussion.
	  */
	 private void createWitness() {
		 if (areEquivalent) {
			 if (verbose) {
				 System.out.println("Cannot produce a witness since the " +
						 "two automata accept the same language!");
			 }
		 } else {
			 if (verbose) {
				 System.out.println("Creating witness.");
			 }
			 
			 StatePair pair = null;
			 
			 // Begin with the start states, and continue from there
			 // until an accepting/non-accepting state pair is found.
			 // When that happens, we know we found a suitable string.
			 State state1 = startState[0];
			 State state2 = startState[1];
			 pair = new StatePair(state1, state2);
			 String currSymbol = witnessLookup.get(pair); 
			 
			 boolean continuing = ( !fsa[0].isFinalState(state1) &&
					 !fsa[1].isFinalState(state2) ) ||
					 ( fsa[0].isFinalState(state1) && fsa[1].isFinalState(state2) );
			 		 
			 while (continuing) {
				 
				 if (verbose) {
					 System.out.println("Processing states: " + state1 + " and " + state2);
					 System.out.println("Processing symbol: " + currSymbol);
				 }
				 
				 if (currSymbol != null) {
					witness.append(currSymbol);
				 } else {
					 System.err.print("Missing input symbol ");
					 if (state1 != null && state2 != null) {
						 System.err.println("for states " + state1 + " and " + state2);
					 } else {
						 System.err.println();
					 }
				 }
				 				 
				 // Apply the transition function, delta, to 
				 // move to the next states.
				 Vector<State> temp1 = delta.get(new StateAlphaPair(state1, currSymbol));
				 state1 = temp1.get(0);
				 Vector<State> temp2 = delta.get(new StateAlphaPair(state2, currSymbol));
				 state2 = temp2.get(0);

				 continuing =
					     (( !fsa[0].isFinalState(state1) && !fsa[1].isFinalState(state2) ) ||
						  ( fsa[0].isFinalState(state1) && fsa[1].isFinalState(state2) ));
				 
				 if (continuing) {
					 pair = new StatePair(state1, state2);
					 currSymbol = witnessLookup.get(pair);
				 }
			 }
			 
		 }
	 }
	 
	 
	 /**
	  * Prints out the processing lists L(a) plus the unprocessed "queue".
	  */
	 public void printProcessingLists() {
		 System.out.println("The processing lists L(a): ");
		 
		 if (alphabet == null || La == null) {
			 System.out.println("Processing lists were not constructed.");
			 
		 } else {
		 
			 for (int j = 0; j < alphabet.size(); j++) {
				 System.out.println(" Symbol " + alphabet.get(j) + ": ");
				 for (int i = 0; i < La[j].size(); i++) {
					 if (i > 0) {
						 System.out.print(",");
					 }
					 System.out.print(" " + La[j].get(i));
				 }
				 System.out.println();
	
			 }
			 System.out.println();
			 System.out.println("The unprocessed queue: ");
			 for (int i = 0; i < unprocessed.size(); i++) {
				 if (i > 0) {
					 System.out.print(", ");
				 }
				 Integer symbolNum = unprocessed.get(i);
				 System.out.print("num = " + symbolNum + 
						 " symbol = " + alphabet.get(symbolNum));
			 }
		 }
		 System.out.println();
		 System.out.println();
	 }
	 
	 
	 /**
	  * Prints out the current sets a(i) of states in
	  * partition set i with predecessors on input a.
	  */
	 public void printPredecessorSets() {
		 System.out.println();
		 System.out.println("The predecessor sets a(i): ");
		 
		 if (ai == null || ai.length == 0) {
			 System.out.println("Predecessor sets were not constructed.");
			 
		 } else { 
			 for (int j = 0; j < ai.length; j++) {
				 System.out.println("Input " + alphabet.get(j) + ":");
				 Vector<HashSet<State>> partitionSetVec = ai[j];
				 for (int k = 0; k < partitionSetVec.size(); k++) {
					 System.out.println("	Partion set " + k + ":");
					 HashSet<State> stateSet = partitionSetVec.get(k);
					 if (stateSet != null) {
						 // iterate over set.
						 Iterator<State> iterator = stateSet.iterator();
						  while (iterator.hasNext()) {
							  State currState = iterator.next();
							  System.out.println( "	 " + currState.getName()
							         + " (" + currState.getID() + ")");
						  }
					  } else {
						  System.out.println("	 none");
					  }
				}	 
			 }
		 }
		 System.out.println();
	 }
	 
	  
	 /**
	  * Print out the current partitioning and
	  * which partition sets contain the start states.
	  */
	 public void printPartition() {
		 System.out.println();
		 if (!areEquivalent) {
			 System.out.println("Partition: ");
			 
			 for (int i = 0; i < partitionSets.size(); i++) {
				 HashSet<State> currPartitionSet = partitionSets.get(i);
				 System.out.print("Partition set " + i + ": ");
				 	 
				 int j = 0;
				 for (State currState : currPartitionSet) {
					 if (j > 0) {
						 System.out.print(", ");
					 }
					 System.out.print(currState.getName() + " (" + 
					                  currState.getID() + ")");
					 j++;
				 }
				 System.out.println();
			 }
				 if (!done) {
				 System.out.println();
				 System.out.println("Set numbers are stored as follows:");
				 
				 for (State nextState : currBlock.keySet()) {
					System.out.println(nextState.getName() + ":  block " 
							+ currBlock.get(nextState)); 
				 }
			 }
		 }
		 System.out.println();
		 for (int k = 0; k < startState.length; k++) {
			 System.out.println("Start state " + startState[k].getName() +
			      " is in partition set " + startStatePartitionSets[k]);
		}
	 }
	 
	 
	 /**
	  * Print out the inverse transition function.
	  */
	 public void printDeltaInverse() {
		 System.out.println();
		 System.out.println("The inverse transition function: ");
		 
		 if (deltaInverse == null) {
			 System.out.println("Inverse transition function was not constructed.");
		 } else {
			 // iterate over map.
			 Iterator<StateAlphaPair> iterator = deltaInverse.keySet().iterator();
			 while (iterator.hasNext()) {
				 StateAlphaPair pair = iterator.next();
				 Vector<State> fromVector = deltaInverse.get(pair);
				 System.out.print(pair + ":  ");
				 for (int i = 0; i < fromVector.size(); i++) {
					 if (i > 0) {
						 System.out.print(", ");
					 }
					 State currState = fromVector.get(i);
					 System.out.print( currState.getName() + " (" + 
					                  currState.getID() + ")");
				 }				
				 System.out.println();
			}
		}
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
				 FiniteStateAutomaton fa1 = EquivalenceNlgNWitness.readFAFile(args[0]);
				 FiniteStateAutomaton fa2 = EquivalenceNlgNWitness.readFAFile(args[1]);
				 
				 boolean wantWitness = true;
				 boolean wantVerbose = false;
				 
				 if (args.length >= 3 && args[2].equalsIgnoreCase("false")) {
					 wantWitness = false;
				 }

				 // test for equivalence
				 EquivalenceNlgNWitness equiv = 
					          new EquivalenceNlgNWitness(fa1, fa2, wantWitness);
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
					 if (wantWitness) {
						 equiv.printDelta();
					 }	
					 equiv.printDeltaInverse();
		 			 equiv.printPartition();
		 			 equiv.printPredecessorSets();
		 			 equiv.printProcessingLists();
				 }
			 } catch (ParseException pe) {
				 System.err.println("Problem parsing the file: " + pe);
			 } catch (IOException ioe) {
				 System.err.println("Problem reading file: " + ioe);
 			 } catch (Exception e) {
				 System.err.println("Program failed with general exception: " + e);
			 }

		} else {
		    System.out.println("Usage:  java equivalence.EquivalenceNlgNWitness " +
		    		"filename1 filename2 [true|false] [true|false]");
		    System.out.println("...where the two booleans are: ");
		    System.out.println("1. false means no witness is produced.");
		    System.out.println("   The default is to include a witness (true).");
		    System.out.println("2. true means verbose mode.");
		    System.out.println("   The default is to minimize output to standard out (false).");
		}
	 }
}
