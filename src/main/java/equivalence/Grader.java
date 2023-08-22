/*
 * Code written for Master's Project by Daphne A. Norton at RIT
 * December 27, 2008
 * 
 * Version 1.1 - offering choice between partitioning vs. merging
 * algorithm, using EquivalenceWitness superclass.
 */

package equivalence;

import file.ParseException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import automata.State;
import automata.Transition;
import automata.fsa.FSAAlphabetRetriever;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;

/**
 * This class facilitates grading of JFLAP files
 * representing finite automata (DFAs and NFAs).
 * 
 * @author Daphne A. Norton
 */
public class Grader {
	
	/**
	 * An instance of the class that determines
	 * equivalence.  If the student's model is equivalent
	 * to the instructor's, then it is correct.
	 */
	private EquivalenceWitness equivWitness = null;
	
	/**
	 * If the answer is wrong, should a witness string
	 * be provided to show an example of what one
	 * automaton accepts and the other does not?
	 */
	private boolean provideWitness = true;
	
	/**
	 * Do the two automata represent the same
	 * language?
	 */
	private boolean areEquivalent = false;
		
	/**
	 * If the automata have different alphabets,
	 * or some other error occurs during testing,
	 * the program will assume they do not match.
	 */
	private boolean hasInputErr = false;
	
	/**
	 * If the submitted automata is an NFA and
	 * a DFA was required, or the submitted automata
	 * is not an NFA according to Sipser.
	 */
	private boolean wrongMachine = false;
	
	/**
	 * Error string when it is the wrong machine
	 */
	private String wrongMachineError = null;

	/**
	 * Constructor.  Tests the equivalence of two finite
	 * automata.  One of these should be the instructor's
	 * "answer key" model (a .jff file from JFLAP), and the other 
	 * should be the student's file.  Note that the instructor
	 * should tell students how to handle transitions that go to
	 * a dead state (whether the student can leave transitions
	 * out or not), if it may cause symbols in the alphabet to
	 * be left completely out of the automaton.  The alphabet
	 * used in the student's automaton must match that of the
	 * instructor's automaton. 
	 * 
	 * @param  firstFile       The first automaton to compare.
	 * @param  secondFile      The second automaton.
	 * @param  includeWitness  Provide feedback by outputting a string
	 *               which one automaton accepts and the other rejects?
	 * @param  useMerge        Use near-linear merging
	 * @param  forceDFA        Check that both files are DFAs if the first one is
	 * @throws IOException 
	 * @throws ParseException 
	 */
	public Grader(String firstFile, String secondFile, 
		      boolean includeWitness, boolean useMerge,
		      boolean forceDFA, boolean forceSipserNFA)
	                                       throws ParseException, IOException {
		
		FiniteStateAutomaton fa1 = EquivalenceWitness.readFAFile(firstFile);
		FiniteStateAutomaton fa2 = EquivalenceWitness.readFAFile(secondFile);

		if (forceDFA) {
		    if (Grader.isSipserDFA(fa1) && !Grader.isSipserDFA(fa2)) {
			wrongMachine = true;
			wrongMachineError = "The answer must be a DFA.";
			areEquivalent = false;
			return;
		    }
		}

		if (forceSipserNFA) {
		    if (!Grader.isSipserNFA(fa2)) {
			wrongMachine = true;
			wrongMachineError = "Not an NFA.";
			areEquivalent = false;
			return;
		    }
		}
		
		if (useMerge) {
			equivWitness = new EquivalenceMergeWitness(fa1, fa2, includeWitness);
		} else {
			equivWitness = new EquivalenceNlgNWitness(fa1, fa2, includeWitness);
		}
		provideWitness = includeWitness;
		areEquivalent = equivWitness.areAutomataEquivalent();
		hasInputErr = equivWitness.getHasInputError();
	}
	
	
	/**
	 * Check if the student's answer matches the key.
	 * The automata do not need to be identical, just
	 * equivalent.
	 * 
	 * @return whether or not the answer is right
	 */
	public boolean isCorrect() {
		return areEquivalent;
	}
	
	
	/**
	 * This method states whether or not the problem was
	 * solved correctly.  If the instructor has specified that
	 * the student should receive extra feedback on incorrect
	 * results, then a sample witness string is provided.
	 * 
	 * @return  whether the answer is right or not, in String
	 *          format (including a witness, if enabled). 
	 */
	public String getResult() {
		String result = null;

		if (hasInputErr) {
			result = equivWitness.getInputErrorMessage();
		} else if (wrongMachine) {
			result = this.wrongMachineError;
		} else if (isCorrect()) {
			result = "The answer is correct!";
		} else if (provideWitness) {
		    String witness = equivWitness.getWitness();

		    if (!"".equals(witness)) {
			result = "Incorrect:  the string \"" + witness +
			     "\" is an example.";
		    }
		    else {
			result = "Incorrect: the empty string is an example.";
		    }
		} else {
			result = "The answer is incorrect.";
		}
		return result;
	}

	public static boolean isSipserDFA(FiniteStateAutomaton fsa) {
		FSAAlphabetRetriever fsaar = new FSAAlphabetRetriever();
		State[] states = fsa.getStates();
		HashSet<String> alpha = new HashSet<String>(Arrays.asList(fsaar.getAlphabet(fsa)));

		// It has to be an NFA for starters
		if (!Grader.isSipserNFA(fsa)) {
		    return false;
		}

		for (State s : states) {
			Transition[] transitions = fsa.getTransitionsFromState(s);
			HashSet<String> thisAlpha = new HashSet<String>();

			for (Transition t : transitions) {
				String label = ((FSATransition)t).getLabel();

				if (thisAlpha.contains(label)) {
					// Repeated label, not a DFA
					return false;
				}

				thisAlpha.add(label);
			}

			if (!thisAlpha.equals(alpha)) {
				return false;
			}
		}

		return true;
	}

	public static boolean isSipserNFA(FiniteStateAutomaton fsa) {
		State[] states = fsa.getStates();

		for (State s : states) {
			Transition[] transitions = fsa.getTransitionsFromState(s);

			for (Transition t : transitions) {
				String label = ((FSATransition)t).getLabel();

				if (label.length() > 1) {
					return false;
				}
			}
		}

		return true;
	}
	
	/**
	 * The main method to compare the student's file with
	 * the answer key file.
	 * 
	 * @param args  command line arguments should be:
	 *              1. first file, 
	 *              2. second file, 
	 *              3. whether or not to provide a witness for incorrect
	 *              answers so that students can learn from their mistakes.
	 *              4. (optional) whether to use the merging algorithm.
	 */
	public static void main(String[] args) {
		
		if (3 <= args.length && args.length <= 6) {
			 
			// Determine user preferences
			 boolean wantWitness = true;
			 boolean wantMerge = true;
			 boolean forceDFA = false;
			 boolean forceSipserNFA = false;
			 if (args[2].equalsIgnoreCase("false")) {
				 wantWitness = false;
			 }
			 if (args.length >= 4 && args[3].equalsIgnoreCase("false")) {
				 wantMerge = false;
			 }
			 if (args.length >= 5 && args[4].equalsIgnoreCase("true")) {
			     forceDFA = true;
			 }
			 if (args.length >= 6 && args[5].equalsIgnoreCase("true")) {
			     forceSipserNFA = true;
			 }

			 try {
				 Grader grader = new Grader(args[0], args[1], wantWitness,
							    wantMerge, forceDFA, forceSipserNFA);
				 System.out.println(grader.getResult());
				 System.exit(grader.isCorrect() ? 0 : 1);
			 } catch (ParseException pe) {
				 System.err.println("Problem parsing the file: " + pe);
			 } catch (IOException ioe) {
				 System.err.println("Problem reading file: " + ioe);
 			 } catch (Exception e) {
				 System.err.println("Problem with Grader program: " + e);
				 e.printStackTrace();
			 } finally {
			     System.exit(2);
			 }
		} else {
		    System.out.println("Usage:  java equivalence.Grader " +
				"filename1 filename2 {true|false} [{true|false} [{true|false} [{true|false}]]]");
		    System.out.println("...where the first boolean indicates " +
		    		"whether the output should include an example " +
		    		"when a solution is wrong,");
		    System.out.println("the second (optional) boolean " +
		    		"specifies if the near-linear merging algorithm" +
		    		"should be used rather than the slower " +
		    		"Hopcroft partitioning " +
		    		"algorithm -- it uses merging (true) by default,");
		    System.out.println("the third boolean forces both inputs to be " +
					"DFAs (in Sipser's sense) if the first one is,");
		    System.out.println("and the fourth boolean forces the second input to be " +
					"an NFA (in Sipser's sense).");
		}
	}
}
