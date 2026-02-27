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


package automata.turing;

import automata.*;

import java.util.*;

/**
 * The Turing alphabet retriever object can be used to find the alphabet for a
 * given Turing machine. The method of determining the alphabet for
 * automaton involves examining all transitions in the automaton and adding each
 * new character on a transition label to the alphabet.
 * 
 * @author Lucas Famous
 */

public class TuringAlphabetRetriever extends AlphabetRetriever {

	private boolean getReadAlphabet;
	private boolean getWriteAlphabet;

	/**
	 * Creates an instance of <CODE>FSAAlphabetRetriever</CODE>.
	 * Default constructor gets the reading alphabet but not the writing alphabet
	 */
	public TuringAlphabetRetriever() {
		this.getReadAlphabet = true;
		this.getWriteAlphabet = false;
	}

	/**
	 * Creates an instance of <CODE>FSAAlphabetRetriever</CODE>.
	 * @param getRead whether to read the read alphabet
	 * @param getWrite whether to read the write alphabet
	 */
	public TuringAlphabetRetriever(boolean getRead, boolean getWrite) {
		this.getReadAlphabet = getRead;
		this.getWriteAlphabet = getWrite;
	}

	/**
	 * Returns the alphabet of <CODE>automaton</CODE> by analyzing all
	 * transitions and their labels.
	 * 
	 * @param automaton
	 *            the Turing machine automaton
	 * @return the alphabet, in a string[].
	 */
	@Override
	public String[] getAlphabet(Automaton automaton) {
		if (automaton instanceof TuringMachine tm) {
			int tapes = tm.tapes();
			ArrayList<String> list = new ArrayList<>();
			Transition[] transitions = tm.getTransitions();
			for (Transition transition1 : transitions) {
				TMTransition transition = (TMTransition) transition1;
				for (int tapenum = 0; tapenum < tapes; tapenum++) {
					if (this.getWriteAlphabet) {
						String writeLabel = transition.getWrite(tapenum);
						if (!writeLabel.equals("") && !writeLabel.equals(Character.toString(Tape.BLANK)) && !list.contains(writeLabel)) {
							list.add(writeLabel);
						}
					}
					
					if (this.getReadAlphabet) {
						String readLabel = transition.getRead(tapenum);
						if (!readLabel.equals("") && !readLabel.equals(Character.toString(Tape.BLANK)) && !list.contains(readLabel)) {
							list.add(readLabel);
						}
					}
				}
			}
			return (String[]) list.toArray(new String[0]);
		}
		return null;
	}

	public void setGetRead(boolean getReadAlphabet) {
		this.getReadAlphabet = getReadAlphabet;
	}

	public void setGetWrite(boolean getWriteAlphabet) {
		this.getWriteAlphabet = getWriteAlphabet;
	}
}
