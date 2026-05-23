package gui.viewer;

import automata.State;
import automata.Transition;

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

    public ArrayList<String> getTransitionLabels() {
        ArrayList<String> processedList = new ArrayList<>();
        for (String s : transitionLabels) {
            processedList.add(getEmptyOrReplaceSpaces(s));
        }
        return processedList;
    }

    private ArrayList<String> transitionLabels;
}
