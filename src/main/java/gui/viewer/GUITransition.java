package gui.viewer;

import automata.State;
import automata.Transition;

import java.util.ArrayList;

/**
 * A <CODE>GUITransition</CODE> is a <CODE>Transition</CODE> object that represents
 * a collection of transitions that should visually be rendered together. It has
 * a special variable that stores an ArrayList of transition labels. These labels
 * represent every transition possible from the <CODE>GUITransition</CODE>'s from
 * and to states.
 *
 * @see automata.fsa.FiniteStateAutomaton
 *
 * @author Thomas Finley
 */
public class GUITransition extends Transition {

    /**
     * Instantiates a <CODE>GUITransition</CODE> object.
     * @param from the state that every transition this object holds starts from.
     * @param to the state that every transition this object holds ends at.
     * @param labels a list of strings representing all transitions that occur
     *               between the from and to states. Each individual string should
     *               be a transition label for a transition that starts at the <i>from</i>
     *               state and ends at the <i>to</i> state.
     */
    public GUITransition(State from, State to, ArrayList<String> labels) {
        super(from, to);
        transitionLabels = labels;
    }

    /**
     * Produces a copy of this transition with new from and to states.
     *
     * @param from
     *            the new from state
     * @param to
     *            the new to state
     * @return a copy of this transition with the new states
     */
    public Transition copy(State from, State to) {
        return new GUITransition(from, to, transitionLabels) {
        };
    }

    /**
     * {@inheritDoc}
     * {@link GUITransition} is a special transition meant for
     * rendering use. Because of this, it doesn't have a proper
     * getDescriptionWithSpacesHandled function. This method should
     * not be used.
     */
    @Override
    public String getDescriptionWithSpacesHandled() {
        return "";
    }

    /**
     * Gets a list of ready to render transition labels for all the transitions
     * between the from and to states of this <CODE>GUITransition</CODE>. Each
     * label represents one transition.
     * @return a list of strings that can be rendered on screen
     */
    public ArrayList<String> getTransitionLabels() {
        ArrayList<String> processedList = new ArrayList<>();
        for (String s : transitionLabels) {
            processedList.add(getEmptyOrReplaceSpaces(s));
        }
        return processedList;
    }

    private ArrayList<String> transitionLabels;
}
