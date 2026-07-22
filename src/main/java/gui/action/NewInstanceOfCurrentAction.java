package gui.action;

import automata.fsa.FiniteStateAutomaton;
import automata.gnfa.GNFA;
import automata.mealy.MealyMachine;
import automata.mealy.MooreMachine;
import automata.pda.PushdownAutomaton;
import automata.turing.TuringMachine;
import automata.turing.TuringMachineBuildingBlocks;
import grammar.Grammar;
import grammar.cfg.ContextFreeGrammar;
import grammar.lsystem.LSystem;
import gui.environment.EnvironmentFrame;
import gui.environment.FrameFactory;
import gui.pumping.CFPumpingLemmaChooser;
import gui.pumping.RegPumpingLemmaChooser;
import regular.RegularExpression;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serializable;

import static gui.Globals.guaranteedPositionFrameOnWindow;
import static gui.action.NewAction.showNew;
import static gui.editor.EditorKeyBindings.CTRL_CMD_SHORTCUT_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;

public class NewInstanceOfCurrentAction extends RestrictedAction {
    EnvironmentFrame frame;
    private NewInstanceOfCurrentAction(String string, EnvironmentFrame frame) {
        super(string, null);
        this.frame = frame;
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(
                KeyEvent.VK_N, CTRL_CMD_SHORTCUT_MASK | SHIFT_DOWN_MASK
        ));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Serializable obj = frame.getEnvironment().getObject();
        switch (obj) {
            case FiniteStateAutomaton fsa -> {
                createAndPositionWindow(new FiniteStateAutomaton());
            }
            case MooreMachine moore -> {
                createAndPositionWindow(new MooreMachine());
            }
            case MealyMachine mealy -> {
                createAndPositionWindow(new MealyMachine());
            }
            case PushdownAutomaton pda -> {
                if (pda.singleInputPDA) {
                    createAndPositionWindow(new PushdownAutomaton(true));
                } else {
                    createAndPositionWindow(new PushdownAutomaton());
                }
            }
            case TuringMachineBuildingBlocks turingBB -> {
                createAndPositionWindow(new TuringMachineBuildingBlocks(turingBB.tapes));
            }
            case TuringMachine turing -> {
                createAndPositionWindow(new TuringMachine(turing.tapes));
            }
            case Grammar cfg -> {
                createAndPositionWindow(new ContextFreeGrammar());
            }
            case LSystem lSys -> {
                createAndPositionWindow(new LSystem());
            }
            case RegularExpression re -> {
                createAndPositionWindow(new RegularExpression());
            }
            case RegPumpingLemmaChooser rePump -> {
                createAndPositionWindow(new RegPumpingLemmaChooser());
            }
            case CFPumpingLemmaChooser cfPump -> {
                createAndPositionWindow(new CFPumpingLemmaChooser());
            }
            default -> {
                showNew();
            }
        }
    }

    public static NewInstanceOfCurrentAction getActionInstance(EnvironmentFrame frame) {
        Serializable obj = frame.getEnvironment().getObject();
        String base = "New ";
        return switch (obj) {
            case FiniteStateAutomaton fsa -> new NewInstanceOfCurrentAction(base + "Finite Automaton", frame);
            case GNFA gnfa -> new NewInstanceOfCurrentAction(base + "Generalized Nondeterministic Finite Automaton", frame);
            case MooreMachine moore -> new NewInstanceOfCurrentAction(base + "Moore Machine", frame);
            case MealyMachine mealy -> new NewInstanceOfCurrentAction(base + "Mealy Machine", frame);
            case PushdownAutomaton pda -> new NewInstanceOfCurrentAction(base + (pda.singleInputPDA ? "Single" : "Multiple") + " Input Pushdown Automaton", frame);
            case TuringMachineBuildingBlocks turingBB -> new NewInstanceOfCurrentAction(base + "Turing Machine with Building Blocks", frame);
            case TuringMachine turing -> new NewInstanceOfCurrentAction(base + (turing.tapes == 1 ? "" : turing.tapes + " Tape ") + "Turing Machine", frame);
            case Grammar cfg -> new NewInstanceOfCurrentAction(base + "Grammar", frame);
            case LSystem lSys -> new NewInstanceOfCurrentAction(base + "L-System", frame);
            case RegularExpression re -> new NewInstanceOfCurrentAction(base + "Regular Expression", frame);
            case RegPumpingLemmaChooser rePump -> new NewInstanceOfCurrentAction(base + "Regular Pumping Lemma", frame);
            case CFPumpingLemmaChooser cfPump -> new NewInstanceOfCurrentAction(base + "Context-Free Pumping Lemma", frame);
            default -> null;
        };
    }

    private void createAndPositionWindow(Serializable object) {
        JFrame newFrame = FrameFactory.createFrame(object, false);
        // Position the window over where the dialog used to be.
        if (newFrame != null) {
            guaranteedPositionFrameOnWindow(newFrame, this.frame);
            newFrame.setVisible(true);
        }
    }
}
