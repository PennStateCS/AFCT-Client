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

import automata.*;
import regular.RegularExpression;
import regular.RegularExpressionValidator;

import java.util.ArrayList;
import java.util.Iterator;

public class GNFAStepByStateSimulator extends AutomatonSimulator {
    /**
     * Creates a GNFA step by state simulator for the given automaton.
     *
     * @param automaton
     *            the machine to simulate
     */
    public GNFAStepByStateSimulator(Automaton automaton){
        super(automaton);
    }

    /**
     * Returns an GNFAConfiguration object that represents the initial
     * configuration of the GNFA, before any input has been processed. This
     * method returns an array of length one, since the closure of the initial
     * state is not taken.
     *
     * @param input
     *            the input string.
     */
    public Configuration[] getInitialConfigurations(String input){
        Configuration[] configs = new Configuration[1];
        configs[0] = new GNFAConfiguration(myAutomaton.getInitialState(), null,
                input, input);
        return configs;
    }

    /**
     * Simulates one step for a particular configuration, adding all possible
     * configurations reachable in one step to set of possible configurations.
     *
     * @param config
     *            the configuration to simulate the one step on.
     */
    public ArrayList<Configuration> stepConfiguration(Configuration config){
        ArrayList<Configuration> list = new ArrayList<>();
        GNFAConfiguration configuration = (GNFAConfiguration) config;
        /** get all information from configuration. */
        String unprocessedInput = configuration.getUnprocessedInput();
        String totalInput = configuration.getInput();
        State currentState = configuration.getCurrentState();
        Transition[] transitions = myAutomaton
                .getTransitionsFromState(currentState);
        for (int k = 0; k < transitions.length; k++) {
            GNFATransition transition = (GNFATransition) transitions[k];
            /** get all information from transition. */
            String transLabel = transition.getLabel();
            State toState = transition.getToState();
            /** identify unprocessed input string prefixes that match the transition's regular expression */
            ArrayList<String> matchingPrefixes = getMatchingPrefixes(transLabel, unprocessedInput);
            // Enumerate each regular expression-matching prefix into a new configuration
            for (String matchingPrefix : matchingPrefixes){
                String nextUnprocessedInput = "";
                if (matchingPrefix.length() < unprocessedInput.length()){
                    nextUnprocessedInput = unprocessedInput.substring(matchingPrefix.length());
                }
                list.add(
                    new GNFAConfiguration(toState, configuration, totalInput, nextUnprocessedInput)
                );
            }
        }
        return list;
    }

    /**
     * Finds prefixes of an input string that match a regular expression passed as a string (i.e. a+b*).
     * If no such prefixes exist, an empty ArrayList is returned.
     * @param regex the regular expression in string form
     * @param input the input string
     * @return an arraylist of prefixes of the input string that match a given regular expression
     */
    public ArrayList<String> getMatchingPrefixes(String regex, String input){
        ArrayList<String> matchingPrefixes = new ArrayList<>();
        RegularExpression regularExpression = new RegularExpression(regex);
        for (int i=1; i<input.length()+1; i++){
            String prefix = input.substring(0,i);
            if (RegularExpressionValidator.testInputString(regularExpression, prefix)){
                matchingPrefixes.add(prefix);
            }
        }
        return matchingPrefixes;
    }

    /**
     * Returns true if the simulation of the input string on the automaton left
     * the machine in a final state. If the entire input string is processed and
     * the machine is in a final state, return true.
     *
     * @return true if the simulation of the input string on the automaton left
     *         the machine in a final state.
     */
    public boolean isAccepted() {
        Iterator<Configuration> it = myConfigurations.iterator();
        while (it.hasNext()) {
            GNFAConfiguration configuration = (GNFAConfiguration) it.next();
            State currentState = configuration.getCurrentState();
            if (configuration.getUnprocessedInput().isEmpty()
                    && myAutomaton.isFinalState(currentState)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs the automaton on the input string.
     *
     * @param input
     *            the input string to be run on the automaton
     * @return true if the automaton accepts the input
     */
    public boolean simulateInput(String input) {
        /** clear the configurations to begin new simulation. */
        myConfigurations.clear();
        Configuration[] initialConfigs = getInitialConfigurations(input);
        for (int k = 0; k < initialConfigs.length; k++) {
            GNFAConfiguration initialConfiguration = (GNFAConfiguration) initialConfigs[k];
            myConfigurations.add(initialConfiguration);
        }
        while (!myConfigurations.isEmpty()) {
            if (isAccepted())
                return true;
            ArrayList<Configuration> configurationsToAdd = new ArrayList<>();
            Iterator<Configuration> it = myConfigurations.iterator();
            while (it.hasNext()) {
                GNFAConfiguration configuration = (GNFAConfiguration) it.next();
                ArrayList<Configuration> configsToAdd = stepConfiguration(configuration);
                configurationsToAdd.addAll(configsToAdd);
                /**
                 * Remove configuration since just stepped from that
                 * configuration to all reachable configurations.
                 */
                it.remove();
            }
            myConfigurations.addAll(configurationsToAdd);
        }
        return false;
    }

}

