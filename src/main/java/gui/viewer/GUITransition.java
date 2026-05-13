package gui.viewer;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;

import java.util.ArrayList;

public class GUITransition extends Transition {

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
     * {@link GUITransition} is a special UI implementation meant
     * for UI rendering only.
     */
    @Override
    public String getDescriptionWithSpacesHandled() {
        return "";
    }

    private ArrayList<String> transitionLabels;
}
