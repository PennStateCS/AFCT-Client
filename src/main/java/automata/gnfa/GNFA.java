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
import automata.State;

import java.awt.*;

/**
 * This subclass of <code>Automaton</code> is specifically for
 * a definition of a Generalized Nondeterministic Finite Automaton (GNFA).
 * A GNFA must adhere to the following restrictions:
 * -one start state
 * -one accept state
 * -start state has no incoming transitions
 * -accept state has no outgoing transitions
 *
 * @author Teddy FitzPatrick
 *
 */
public class GNFA extends Automaton {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a GNFA with no states and no transitions.
     */
    public GNFA() {
        super();
    }

    /**
     * Returns the class of <CODE>Transition</CODE> this automaton must
     * accept.
     *
     * @return the <CODE>Class</CODE> object for <CODE>automata.gnfa.GNFATransition</CODE>
     */
    protected Class getTransitionClass() {
        return automata.gnfa.GNFATransition.class;
    }

}
