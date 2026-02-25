package gui.action;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;
import gui.environment.Environment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashSet;

public class GenerateOCRDataAction extends RestrictedAction{
    final int MIN_NUMBER_STATES = 3;
    final int MAX_NUMBER_STATES = 11;
    final int MAX_NUMBER_TRANSITIONS = 15;

    private FiniteStateAutomaton automaton = null;
    private Environment environment = null;

    /**
     * Constructor
     * @param automaton the empty FSA that is first generated upon initialization
     * @param environment the enviornment
     */
    public GenerateOCRDataAction(FiniteStateAutomaton automaton,
                                 Environment environment) {
        super("Generate data", null);
        this.automaton = automaton;
        this.environment = environment;
    }

    /**
     * Begin the data generation process where a specified number
     * of automata are generated, screenshotted, and paired up with their XML descriptions.
     *
     * @param e the action event
     */
    public void actionPerformed(ActionEvent e) {
        int numExamples = 0;
        try {
            numExamples = Integer.parseInt(
                    JOptionPane.showInputDialog("How many examples would you like to generate?", "enter a number")
            );
        } catch (Error err) {
            JOptionPane.showMessageDialog(null, "Error: please enter a number");
        }

        for (int i = 0; i < numExamples; i++) {
            // create from 3 to 11 states
            int numStatesForThisExample = (int) (Math.random()*(MAX_NUMBER_STATES-MIN_NUMBER_STATES+1))+MIN_NUMBER_STATES;
            for (int k = 0; k < numStatesForThisExample; k++) {
                // we will use the layout algorithms later to move the states around
                // to be in a more logical position
                this.automaton.createState(new Point(100, 50*k));
            }

            // randomly assign the start and ending states
            int startStateID = (int) (Math.random()*numStatesForThisExample);
            State startState = this.automaton.getStateWithID(startStateID);
            this.automaton.setInitialState(startState);

            // pick which states will be final states
            int numFinalStates = (int) (Math.random() * (numStatesForThisExample/3)) + 1;
            HashSet<Integer> finalStateIDs = new HashSet<>();
            for (int k = 0; k < numFinalStates; k++) {
                int id = (int) (Math.random() * numStatesForThisExample);
                while(finalStateIDs.contains(id)) {
                    id = (int) (Math.random() * numStatesForThisExample);
                };
                finalStateIDs.add(id);
            }
            State[] states = this.automaton.getStates();
            finalStateIDs.forEach((id -> this.automaton.addFinalState(states[id])));

            // randomly add some transitions
            // for every state, give it a chance to have a transition to the
            // other states.
            int numTransitions = 0;
            for (int k = 0; k < numStatesForThisExample; k++) {
                for (int j = 0; j < numStatesForThisExample; j ++) {
                    if (j == k) {
                        continue;
                    }

                    double chance = Math.random();
                    // we will make a transition roughly 33% of the time
                    if (chance < 0.33 && numTransitions < MAX_NUMBER_TRANSITIONS) {
                        Transition t = new FSATransition(states[k],states[j], "a");
                        this.automaton.addTransition(t);
                        numTransitions++;
                    }
                }
            }
        }
    }
}
