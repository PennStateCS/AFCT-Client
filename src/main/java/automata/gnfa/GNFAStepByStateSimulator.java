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
import debug.EDebug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

public class GNFAStepByStateSimulator extends AutomatonSimulator {
    public GNFAStepByStateSimulator(Automaton automaton){
        super(automaton);
    }
    public Configuration[] getInitialConfigurations(String input){
        Configuration[] configs = new Configuration[1];
        configs[0] = new GNFAConfiguration(myAutomaton.getInitialState(), null,
                input, input);
        return configs;
    }
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
            HashSet<String> trange = new HashSet<String>();
            if (transLabel.contains("[")){
                for(int i=transLabel.charAt(transLabel.indexOf("[")+1); i<=transLabel.charAt(transLabel.indexOf("[")+3); i++){
                    trange.add(Character.toString((char)i));
                    EDebug.print(Character.toString((char)i));
                }
                for(String element : trange){
                    if (unprocessedInput.startsWith(element)) {
                        String input = "";
                        if (element.length() < unprocessedInput.length()) {
                            input = unprocessedInput.substring(element.length());
                        }
                        State toState = transition.getToState();
                        GNFAConfiguration configurationToAdd = new GNFAConfiguration(
                                toState, configuration, totalInput, input);
                        list.add(configurationToAdd);
                    }
                }
            }
            else if (unprocessedInput.startsWith(transLabel)) {
                String input = "";
                if (transLabel.length() < unprocessedInput.length()) {
                    input = unprocessedInput.substring(transLabel.length());
                }
                State toState = transition.getToState();
                GNFAConfiguration configurationToAdd = new GNFAConfiguration(
                        toState, configuration, totalInput, input);
                list.add(configurationToAdd);
            }
        }
        return list;
    }
    public boolean isAccepted() {
        Iterator<Configuration> it = myConfigurations.iterator();
        while (it.hasNext()) {
            GNFAConfiguration configuration = (GNFAConfiguration) it.next();
            State currentState = configuration.getCurrentState();
            if (configuration.getUnprocessedInput().equals("")
                    && myAutomaton.isFinalState(currentState)) {
                return true;
            }
        }
        return false;
    }
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

