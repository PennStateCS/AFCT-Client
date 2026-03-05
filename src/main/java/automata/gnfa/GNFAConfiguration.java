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


package automata.gnfa;

import automata.Automaton;
import automata.Configuration;
import automata.State;

public class GNFAConfiguration extends Configuration {
    public GNFAConfiguration(State state, automata.gnfa.GNFAConfiguration parent, String input,
                            String unprocessed) {
        super(state, parent);
        myInput = input;
        myUnprocessedInput = unprocessed;
    }
    public String getInput(){return myInput;}
    public String getUnprocessedInput(){return myUnprocessedInput;}
    public void setMyUnprocessedInput(String input){myUnprocessedInput = input;}
    public String toString(){
        return super.toString() + ": " + getUnprocessedInput();
    }
    public boolean isAccept() {
        if (!getUnprocessedInput().isEmpty())
            return false;
        State s = getCurrentState();
        Automaton a = s.getAutomaton();
        return a.isFinalState(s);
    }
    public boolean equals(Object configuration) {
        if (configuration == this)
            return true;
        try {
            return super.equals(configuration)
                    && myUnprocessedInput
                    .equals(((automata.gnfa.GNFAConfiguration ) configuration).getUnprocessedInput());
        } catch (ClassCastException e) {
            return false;
        }
    }
    public int hashCode() {
        return super.hashCode() ^ myUnprocessedInput.hashCode();
    }
    /** The total input. */
    private String myInput;

    /** The unprocessed input. */
    private String myUnprocessedInput;
}

