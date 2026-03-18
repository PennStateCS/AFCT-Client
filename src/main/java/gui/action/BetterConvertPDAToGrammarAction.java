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





package gui.action;

import conversions.PDAToCFG;
import grammar.cfg.ContextFreeGrammar;
import gui.environment.AutomatonEnvironment;
import gui.environment.EnvironmentFrame;
import gui.environment.FrameFactory;
import gui.environment.Universe;
import gui.grammar.automata.ConvertController;
import gui.grammar.automata.ConvertPane;
import gui.grammar.automata.PDAConvertController;
import gui.viewer.SelectionDrawer;
import gui.viewer.ZoomPane;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Iterator;

import javax.swing.*;
import javax.swing.border.BevelBorder;

import automata.Automaton;
import automata.State;
import automata.Transition;
import automata.pda.PDAToCFGConverter;
import automata.pda.PDATransition;
import automata.pda.PushdownAutomaton;

import static conversions.PDAToCFG.setupPDA;
import static conversions.PDAToCFG.transformPDA;
import static gui.environment.Profile.PDA_STACK_BOTTOM_MARKER;

/**
 * This action handles the conversion of an PDA to a context free grammar.
 *
 * @author Thomas Finley
 */

public class BetterConvertPDAToGrammarAction extends ConvertAutomatonToGrammarAction {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new <CODE>ConvertFSAToGrammarAction</CODE>.
     *
     * @param environment
     *            the environment
     */
    public BetterConvertPDAToGrammarAction(AutomatonEnvironment environment) {
        super(environment, "Better Convert to Grammar");
    }

    /**
     * Checks the PDA to make sure it's ready to be converted.
     */
    protected boolean checkAutomaton() {
        EnvironmentFrame frame = Universe.frameForEnvironment(getEnvironment());
        JPanel messagePanel = new JPanel(new BorderLayout());
        SelectionDrawer drawer = new SelectionDrawer(getAutomaton());
        JLabel messageLabel = new JLabel();
        ZoomPane zoom = new ZoomPane(drawer);
        JPanel tempPanel = new JPanel(new BorderLayout());
        tempPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
        zoom.setPreferredSize(new java.awt.Dimension(300, 200));
        tempPanel.add(zoom, BorderLayout.CENTER);
        messagePanel.add(tempPanel, BorderLayout.CENTER);
        messagePanel.add(messageLabel, BorderLayout.SOUTH);

        try {
            automaton = (PushdownAutomaton) getAutomaton().clone();
            if (automaton.getInitialState() == null) {
                JOptionPane.showMessageDialog(frame,
                        "There must be an initial state!",
                        "No Initial State", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            setupPDA(automaton);
            cfg = transformPDA(automaton);
        } catch (PDAToCFG.TransitionException te) {
            drawer.clearSelected();
            for (Transition transition : te.transitions) {
                drawer.addSelected(transition);
            }
            messageLabel.setText(te.getMessage());
            JOptionPane.showMessageDialog(frame, messagePanel,
                    "Transition Error", JOptionPane.ERROR_MESSAGE);
            return false;
        } catch (IllegalArgumentException ie) {
            if (ie.getMessage().equals("PDA does not have a unique final state")) {
                drawer.clearSelected();
                State[] finalStates = getAutomaton().getFinalStates();
                for (State state : finalStates) {
                    drawer.addSelected(state);
                }
                messageLabel.setText(ie.getMessage());
                JOptionPane.showMessageDialog(frame, messagePanel,
                        "Final State Error", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, ie.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        } catch (RuntimeException re) {
            if (re.getMessage().equals("Out of valid grammar variables")) {
                JOptionPane.showMessageDialog(frame, re.getMessage(),
                        "Grammar Variables Error", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, re.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
        return true;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SwingUtilities.invokeLater(() -> {
            if (checkAutomaton()) {
                FrameFactory.createFrame(cfg);
            }
        });
    }

    /**
     * This object is only applicable to pushdown automatons.
     *
     * @param object
     *            the object to test
     * @return <CODE>true</CODE> if the object is a pushdown automaton, <CODE>false</CODE>
     *         otherwise
     */
    public static boolean isApplicable(Object object) {
        return object instanceof PushdownAutomaton;
    }

    /**
     * Initializes the convert controller.
     *
     * @param pane
     *            the convert pane that holds the automaton pane and the grammar
     *            table
     * @param drawer
     *            the selection drawer of the new view
     * @param automaton
     *            the automaton that's being converted; note that this will not
     *            be the exact object returned by <CODE>getAutomaton</CODE>
     *            since a clone is made
     * @return the convert controller to handle the conversion of the automaton
     *         to a grammar
     */
    protected ConvertController initializeController(ConvertPane pane,
                                                     SelectionDrawer drawer, Automaton automaton) {
        return new PDAConvertController(pane, drawer, this.automaton);
    }

    /** The environment this action is part of. */
    private AutomatonEnvironment environment;

    /** The automaton to convert. */
    private PushdownAutomaton automaton;

    /** The resulting CFG. */
    private ContextFreeGrammar cfg = null;

    /** The grammar converter. */
    private PDAToCFGConverter converter = new PDAToCFGConverter();
}
