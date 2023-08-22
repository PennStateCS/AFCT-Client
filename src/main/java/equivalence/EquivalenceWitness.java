/*
 * Code written for Master's Project by Daphne A. Norton at RIT
 * December 27, 2008
 * Version 1
 */
 
package equivalence;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import automata.fsa.*;
import file.XMLCodec;
import file.ParseException;

/**
 * This class contains methods and parameters
 * useful for determining equivalence
 * of two JFLAP finite automata.  Subclass it with the actual
 * implementation.
 * 
 * @author Daphne A. Norton
 */

public abstract class EquivalenceWitness {
		
	/**
	 * Is there a format problem with the automata?
	 */
	protected boolean hasInputError = false;
	
	/**
	 * The message describing the format problem.
	 */
	protected String inputErrorMsg = new String();
	
	/**
	 * Are the automata equivalent?
	 */
	protected boolean areEquivalent = false;
	
	/**
	 * If the automata are not equivalent,
	 * this string is an example regular expression 
	 * which one automaton accepts and the other
	 * rejects.
	 */
	protected StringBuffer witness = new StringBuffer();
	
	/**
	 * Does the user want a witness string?
	 */
	protected boolean produceWitness = true;
	
	/**
	 * Used to turn on/off the detailed output to standard out.
	 */
	protected boolean verbose = false;
	

	/**
	 * Constructor.  
	 *  
	 * @param  includeWitness Does the user want an example of a string
	 *               which one automaton accepts and the other rejects?
	 */
	public EquivalenceWitness(boolean includeWitness) {
		produceWitness = includeWitness;
	}
	
	
	/**
	 * This method needs to be implemented.
	 * 
	 * @return  whether or not the automata recognize the same language.
	 */
	public abstract boolean areAutomataEquivalent();
	
	/**
	 * Reveals the existence of a format issue, such as
	 * different alphabets, which may be fixed by addition
	 * of trap state(s).
	 * 
	 * @return   true if the automata can't be processed as is.
	 */
	public boolean getHasInputError() {
		return hasInputError;
	}
	
	/**
	 * Problem description.
	 * 
	 * @return a string which describes the format issue.
	 */
	public String getInputErrorMessage() {
		return inputErrorMsg;
	}	 
	 	 
	 /**
	  * Prints out the example string which one automaton
	  * accepts but the other rejects.
	  */
	 public void printWitness() {
		 System.out.println();
		 
		 if (areEquivalent) {
			 System.out.println("There is no witness, as the models are equivalent.");
		 } else if (witness != null && witness.length() > 0) {
			 System.out.println("The witness is: " + witness.toString());
		 } else if (witness != null && witness.length() == 0) {
			 System.out.println("The witness is the empty string.");
		 } else {
			 System.err.println("Problem with witness!");
		 }
	 }
	 
	 
	 /**
	  * Returns the example string which one automaton
	  * accepts but the other rejects.
	  * 
	  * @return   the witness string
	  */
	 public String getWitness() {
		 
		 String retVal = null;  // the value to return
		 
		 if (!areEquivalent) {
		     if (witness != null) {
			 retVal = witness.toString();
		     }
		 }
		 return retVal;
	 }
	 
	 /**
	  * To print or not print detailed output to standard out.
	  * 
	  * @param  wantVerbose   true if want details
	  */
	 public void setVerbose(boolean wantVerbose) {
		 verbose = wantVerbose;
	 }
	 
	 
	/**
	 * Read in a file, which must be in the 
	 * standard JFLAP .jff file format (XML).
	 * The file must contain a "fa" (finite automaton).
	 *
	 * @param  filename  the file to read, including path.
	 * @return           the automaton
	 * @throws IOException 
	 * @throws ParseException 
	 */
	 public static FiniteStateAutomaton readFAFile(String filename) 
	                            throws IOException, ParseException {
		 
		 FiniteStateAutomaton fsa = null; // the automaton	
		 File file = new File(filename);  // the file being read
		 XMLCodec x = new XMLCodec();     // to decode the file
		 
		 Serializable object = x.decode(file, null);
		 if(object instanceof FiniteStateAutomaton) {
			 fsa = (FiniteStateAutomaton) object;
		 } else {
			 System.err.println("Wrong type of model in the file " + filename);
		 }
		 return fsa;
	 } 
}
