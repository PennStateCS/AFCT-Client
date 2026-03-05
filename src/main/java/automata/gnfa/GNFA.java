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
    /**
     * This seems to be in every Automaton subclass, so I added it here as well
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a GNFA with no states and no transitions.
     */
    public GNFA() {
        super();

        // In accordance with the definition of a GNFA:
        // start by initializing one start state and one accept state
        Rectangle bounds = getEnvironmentFrame().getBounds();
        System.out.println("bounds::"+bounds);
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        State initialState = createState(new Point((int)width/3, (int)height/2));
        setInitialState(initialState);
        State finalState = createState(new Point((int)(2 * width/3), (int)height/2));
        addFinalState(finalState);
    }

    /**
     * Returns the class of <CODE>Transition</CODE> this automaton must
     * accept.
     *
     * @return the <CODE>Class</CODE> object for <CODE>automata.gnfa.GNFATransition</CODE>
     */
    protected Class getTransitionClass() {
        return automata.fsa.FSATransition.class;
    }

}
