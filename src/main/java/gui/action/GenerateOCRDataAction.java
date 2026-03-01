package gui.action;

import automata.State;
import automata.Transition;
import automata.fsa.FSATransition;
import automata.fsa.FiniteStateAutomaton;
import gui.environment.Environment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.StringJoiner;

public class GenerateOCRDataAction extends RestrictedAction{
    final int MIN_NUMBER_STATES = 3;
    final int MAX_NUMBER_STATES = 9;
    final int MAX_NUMBER_TRANSITIONS = 11;

    enum layoutProcess {
        GEM,
        THE_RANDOM_ALGORITHM,
        THE_RANDOM_ALGORITHM_THEN_GEM,
        RANDOMLY_PICK_OTHER,
    }

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
            // add some random number of states
            int numStatesForThisExample = initializeStates();

            State[] states = this.automaton.getStates();
            createStartAndEndStates(numStatesForThisExample, states);

            // add transitions
            int numTransitions = createTransitions(numStatesForThisExample, states);

            // choose a layout algorithm
            layoutProcess algorithmCode = chooseLayoutProcess(numStatesForThisExample, numTransitions);
        }
    }

    private int initializeStates() {
        // create from 3 to 11 states
        int numStatesForThisExample = (int) (Math.random()*(MAX_NUMBER_STATES-MIN_NUMBER_STATES))+MIN_NUMBER_STATES;
        for (int k = 0; k < numStatesForThisExample; k++) {
            // we will use the layout algorithms later to move the states around
            // to be in a more logical position
            this.automaton.createState(new Point(100, 50*k));
        }
        return numStatesForThisExample;
    }

    private void createStartAndEndStates(int numStatesForThisExample, State[] states) {
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
        finalStateIDs.forEach((id -> this.automaton.addFinalState(states[id])));
    }

    private int createTransitions(int numStatesForThisExample, State[] states) {
        // randomly add some transitions
        // for every state, give it a chance to have a transition to the
        // other states.
        int numTransitions = 0;
        for (int k = 0; k < numStatesForThisExample; k++) {
            for (int j = 0; j < numStatesForThisExample; j ++) {

                double chance = Math.random();
                // we will make a transition roughly 33% of the time
                if (chance < 0.33 && numTransitions < MAX_NUMBER_TRANSITIONS) {
                    String label = getRandomAlphaString();
                    Transition t = new FSATransition(states[k],states[j], label);
                    this.automaton.addTransition(t);
                    numTransitions++;
                }
            }
        }
        return numTransitions;
    }

    private String getRandomAlphaString() {
        Random random = new Random();
        String alphaPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

        int numSymbols = random.nextInt(3) + 1;

        // Use StringJoiner for easy comma delineation
        StringJoiner joiner = new StringJoiner(",");

        for (int i = 0; i < numSymbols; i++) {
            int index = random.nextInt(alphaPool.length());
            joiner.add(String.valueOf(alphaPool.charAt(index)));
        }

        return joiner.toString();
    }

    private layoutProcess chooseLayoutProcess(int numStatesForThisExample, int numTransitions) {
        // first see if we should just use GEM if we have a high number of states/transitions
        if (numStatesForThisExample > 5 || numTransitions > 8) {
            return layoutProcess.GEM;
        }

        // otherwise pick between doing the random algorithm, the random algorithm then
        // doing GEM, or randomly picking from the other algorithms
        double chance = Math.random();

        if (chance < 0.4) {
            return layoutProcess.THE_RANDOM_ALGORITHM;
        } else if (chance < 0.8) {
            return layoutProcess.THE_RANDOM_ALGORITHM_THEN_GEM;
        } else {
            return layoutProcess.RANDOMLY_PICK_OTHER;
        }
    }
}
