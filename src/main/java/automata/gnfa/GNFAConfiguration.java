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

import java.util.Objects;

public class GNFAConfiguration extends Configuration{
    public GNFAConfiguration(State state, automata.gnfa.GNFAConfiguration parent, String input,
                            String unprocessed) {
        super(state, parent);
        myCurrentState = state;
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

    @Override
    public boolean equals(Object otherConfig) {
        if (this == otherConfig) return true;
        if (!(otherConfig instanceof GNFAConfiguration otherGNFAConfig)) return false;
        return Objects.equals(myCurrentState, otherGNFAConfig.myCurrentState)
                && Objects.equals(myUnprocessedInput, otherGNFAConfig.myUnprocessedInput);
    }

    /** GNFAConfig hash is composed of the current state's label and the unprocessed input*/
    public int hashCode() {
        return Objects.hash(myCurrentState.getLabel(), myUnprocessedInput);
    }

    /** The current state of the configuration */
    private State myCurrentState;
    /** The total input. */
    private String myInput;

    /** The unprocessed input. */
    private String myUnprocessedInput;
}

