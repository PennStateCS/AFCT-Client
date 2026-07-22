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


package regular;

import automata.Automaton;
import automata.fsa.FSAStepWithClosureSimulator;
import gui.environment.RegularEnvironment;
import gui.regular.ConvertToAutomatonPane;
import gui.regular.REToFSAController;

/**
 * This class is for validating/testing inputs on regular expressions.
 * This functionality is useful for GNFA transitions and user input regex testing.
 *
 * @author Teddy FitzPatrick
 */
public class RegularExpressionValidator {

    /**
     * Tests an input string on a regular expression contained within the expression
     * field of a RegularEnvironment.
     * @param env the RegularEnvironment containing the regular expression
     * @param input the input string to perform regex testing on
     * @return true on accept and false on reject
     */
    public static boolean testInputString(RegularEnvironment env, String input){
        // Convert the regular expression to an NFA
        ConvertToAutomatonPane pane = new ConvertToAutomatonPane(
                (RegularEnvironment) env);
        REToFSAController controller = pane.controller;
        // Complete the RegEx -> FSA (NFA) conversion
        controller.completeAll();
        Automaton automaton = controller.automaton;
        // Identify an accepting configuration for validation
        // If there are none, reject
        FSAStepWithClosureSimulator simulator = new FSAStepWithClosureSimulator(automaton);
        return simulator.simulateInput(input);
    }
}
