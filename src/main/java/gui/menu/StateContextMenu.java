package gui.menu;

import automata.Note;
import automata.State;
import automata.Transition;
import automata.gnfa.GNFA;
import automata.turing.TMState;
import automata.turing.TMTransition;
import automata.turing.TuringMachineBuildingBlocks;
import gui.editor.EditBlockPane;
import gui.environment.AutomatonEnvironment;
import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.tag.CriticalTag;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class StateContextMenu extends ContextMenu implements ActionListener {
    private State[] states;
    private State state;
    private Point myPoint;

    protected final JCheckBoxMenuItem makeFinal, makeInitial;

    private final JMenuItem changeLabel, deleteLabel, deleteAllLabels, editBlock, copyBlock, replaceSymbol, setName;

    private static final String makeFinal_DEFAULT = "Final";
    private static final String makeInitial_DEFAULT = "Initial";
    private static final String changeLabel_DEFAULT = "Change Label";
    private static final String deleteLabel_DEFAULT = "Clear Label";
    private static final String deleteAllLabels_DEFAULT = "Clear All Labels";
    private static final String setName_DEFAULT = "Set Name";
    private static final String editBlock_DEFAULT = "Edit Block";
    private static final String copyBlock_DEFAULT = "Duplicate Block";
    private static final String replaceSymbol_DEFAULT = "Replace Symbol";

    private static final String makeFinal_MULTI = "Mark Selected as Final";
    private static final String changeLabel_MULTI = "Change Selected Labels";
    private static final String deleteLabel_MULTI = "Clear Selected Labels";
    private static final String setName_MULTI = "Set Selected Names"; // TODO: maybe should be "Rename selected states"?

    public StateContextMenu(AutomatonPane view, AutomatonDrawer drawer) {
        super(view, drawer);

        makeFinal = new JCheckBoxMenuItem(makeFinal_DEFAULT);
        makeInitial = new JCheckBoxMenuItem(makeInitial_DEFAULT);
        changeLabel = new JMenuItem(changeLabel_DEFAULT);
        deleteLabel = new JMenuItem(deleteLabel_DEFAULT);
        deleteAllLabels = new JMenuItem("Clear All Labels");
        setName = new JMenuItem(setName_DEFAULT);
        editBlock = new JMenuItem("Edit Block");
        copyBlock = new JMenuItem("Duplicate Block");
        replaceSymbol = new JMenuItem("Replace Symbol");
    }

    public void addMenuItems(MenuElement menu, boolean skipFinal, boolean isTurningBlock, boolean allowOnlyFinal) {
        if (allowOnlyFinal) {
            addMenuItemHelper(menu, makeFinal);
            return;
        }

        if (!skipFinal) {
            addMenuItemHelper(menu, makeFinal);
        }
        addMenuItemHelper(menu, makeInitial);
        addMenuItemHelper(menu, changeLabel);
        addMenuItemHelper(menu, deleteLabel);
        addMenuItemHelper(menu, deleteAllLabels);
        addMenuItemHelper(menu, setName);
        addMenuItemHelper(menu, addNote);

        if (isTurningBlock) {
            addMenuItemHelper(menu, editBlock);
            addMenuItemHelper(menu, copyBlock);
            addMenuItemHelper(menu, replaceSymbol);
        }
        allListenersAdded = true;
    }

    public void selectAndEnableMenuItems(State[] states, Point p) {
        myPoint = Objects.requireNonNullElseGet(p, () -> new Point(0, 0));
        this.states = states;
        int numSelectedStates = 0;
        int numSelectedFinalStates = 0;
        int numSelectedLabeledStates = 0;
        Set<State> finalStates = drawer.getAutomaton().finalStates;
        for (State state : states) {
            if (state.isSelected()) {
                this.state = state;
                numSelectedStates += 1;
                if (finalStates.contains(state)) {
                    numSelectedFinalStates += 1;
                }
                if (state.getLabel() != null) {
                    numSelectedLabeledStates += 1;
                }
            }
        }

        if (numSelectedStates == 1) {
            makeFinal.setText(makeFinal_DEFAULT);
            changeLabel.setText(changeLabel_DEFAULT);
            deleteLabel.setText(deleteLabel_DEFAULT);
            setName.setText(setName_DEFAULT);

            makeFinal.setSelected(drawer.getAutomaton().isFinalState(this.state));
            makeInitial.setSelected(drawer.getAutomaton().getInitialState() == this.state);
            makeInitial.setEnabled(true);
            deleteLabel.setEnabled(this.state.getLabel() != null);
        } else {
            makeFinal.setText(makeFinal_MULTI);
            changeLabel.setText(changeLabel_MULTI);
            deleteLabel.setText(deleteLabel_MULTI);
            setName.setText(setName_MULTI);

            makeFinal.setSelected(numSelectedStates == numSelectedFinalStates);
            makeInitial.setSelected(false);
            makeInitial.setEnabled(false);
            deleteLabel.setEnabled(numSelectedLabeledStates > 0);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JMenuItem item = (JMenuItem) e.getSource();
        if (drawer.getAutomaton().getEnvironmentFrame() != null) {
            ((AutomatonEnvironment)drawer.getAutomaton().getEnvironmentFrame().getEnvironment()).saveStatus();
        }

        switch (item.getText()) {
            case makeFinal_DEFAULT:
                // fall through
            case makeFinal_MULTI:
                for (State state : states) {
                    if (state.isSelected()) {
                        if (item.isSelected()){
                            // GNFAs can only have one final state; remove other final states before setting a new one
                            if (drawer.getAutomaton() instanceof GNFA &&
                                drawer.getAutomaton().getFinalStates().length == 1){
                                drawer.getAutomaton().finalStates = new HashSet<State>();
                            }
                            drawer.getAutomaton().addFinalState(state);
                        } else {
                            drawer.getAutomaton().removeFinalState(state);
                        }
                    }
                }
                break;
            case makeInitial_DEFAULT:
                if (item.isSelected()) {
                    drawer.getAutomaton().setInitialState(this.state);
                } else {
                    drawer.getAutomaton().setInitialState(null);
                }
                break;
            case changeLabel_DEFAULT:
                // fall through
            case changeLabel_MULTI:
                String oldlabel = this.state.getLabel();
                oldlabel = oldlabel == null ? "" : oldlabel;
                // TODO make sure item.getParent() works for this
                String label = (String) JOptionPane.showInputDialog(null,
                        "Input a new label, or \n"
                                + "set blank to remove the label", "New Label",
                        JOptionPane.QUESTION_MESSAGE, null, null, oldlabel);
                if (label == null)
                    return;
                if (label.isEmpty())
                    label = null;
                for (State state : states) {
                    if (state.isSelected()) {
                        state.setLabel(label);
                    }
                }
                break;
            case deleteLabel_DEFAULT:
                // fall through
            case deleteLabel_MULTI:
                for (State state : states) {
                    if (state.isSelected()) {
                        state.setLabel(null);
                    }
                }
                break;
            case deleteAllLabels_DEFAULT:
                for (State state : states) {
                    state.setLabel(null);
                }
                break;
            case setName_DEFAULT:
                // fall through
            case setName_MULTI:
                String oldName = state.getName();
                oldName = oldName == null ? "" : oldName;
                // TODO make sure item.getParent() works for this
                String name = (String) JOptionPane.showInputDialog(item.getParent(),
                        "Input a new name, or \n"
                                + "set blank to remove the name", "New Name",
                        JOptionPane.QUESTION_MESSAGE, null, null, oldName);
                if (name == null)
                    return;
                if (name.isEmpty())
                    name = null;
                for (State state : states) {
                    if (state.isSelected()) {
                        state.setName(name);
                    }
                }
                break;
            case addNote_TEXT:
                Note note = addNote(myPoint);
                State clickedState = drawer.stateAtPoint(myPoint);
                if (clickedState != null) {
                    clickedState.setNote(note);
                }
                break;
            case editBlock_DEFAULT:
                //this implies that this was a TMState to begin with, because only TM states would have this menu option
                // - is this actually true?

                //not sure why need highest level automaton, but okay
                TMState parent = (TMState) state;
                while (((TuringMachineBuildingBlocks)parent.getAutomaton()).getParent() != null) {
                    parent = ((TuringMachineBuildingBlocks)parent.getAutomaton()).getParent();
                }
                //pop up box asking for building block name if myInternalName has not already been set or
                //was set to a default machine name.
                TMState tmState = (TMState) state;
                if (tmState.myInternalName == null || tmState.myInternalName.contains("Machine" + tmState.getID())) {
                    JPanel panel = new JPanel(new GridLayout(3, 1));
                    JTextField field = new JTextField();
                    panel.add(new JLabel("Note: If you want to save this block as a seperate file, use 'Save As' while in the 'Edit Block' window"));
                    panel.add(new JLabel("Building Block Name" + " "));
                    panel.add(field);
                    int result = JOptionPane.showOptionDialog((Component) e.getSource(), panel, "Give Building Block a Name",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                            null, null, null);
                    if (result != JOptionPane.YES_OPTION && result != JOptionPane.OK_OPTION) {
                        return;
                    }
                    String input = field.getText();
                    TMState parent2 = tmState;
                    while (((TuringMachineBuildingBlocks)parent2.getAutomaton()).getParent() != null) {
                        parent2 = ((TuringMachineBuildingBlocks)parent2.getAutomaton()).getParent();
                        if (parent2.myInternalName != null) {
                            if (parent2.myInternalName.equals(input + ".jff")) {
                                JOptionPane.showMessageDialog((Component) e.getSource(), "Cannot use the same name as a parent block!",
                                        "A Parent Block Already Has This Name",JOptionPane.ERROR_MESSAGE);
                                return;
                            }
                        }
                    }
                    //loop through state to see if there is already a block with this name
                    for (State regState: tmState.getAutomaton().states) {
                        TMState stateTM = (TMState) regState;
                        if (stateTM.getInternalName().equals(input + ".jff")) {
                            Object[] options = { "CANCEL", "YES" };
                            int selectedOption = JOptionPane.showOptionDialog((Component) e.getSource(), "We STRONGLY suggest to NOT "
                                            + "use building blocks with the same name. Do you wish to continue anyways?", "Same Name as Another Building Block",
                                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                                    null, options, options[0]);
                            System.out.println(selectedOption);
                            if (selectedOption != 1) {
                                return;
                            }
                            break;
                        }
                    }
                    state.setName(input);
                    System.out.println(tmState.myInternalName);
                    tmState.setInternalName(input + ".jff");
                    System.out.println(tmState.myInternalName);
                }

                EditBlockPane editor = new EditBlockPane(((TMState)state).getInnerTM()); //give it a Turing Machine //just edit the Automaton directly; there is no need for a repaint either, because the other guy does not paint it
                editor.getAutomaton().setEnvironmentFrame(drawer.getAutomaton().getEnvironmentFrame());

                EnvironmentFrame rootFrame = parent.getAutomaton().getEnvironmentFrame();

                editor.setBlock(state);
                Environment envir = rootFrame.getEnvironment();
                envir.add(editor, "Edit Block", new CriticalTag() {
                });

                envir.setActive(editor);
                break;
            case copyBlock_DEFAULT:
                // TODO: some weirdness here... created block seems to not copy correctly? need to test more
                //MERLIN MERLIN MERLIN MERLIN MERLIN//
                //TMState buffer = ((TuringMachine) getAutomaton()).createTMState((Point)state.getPoint()); //again, we assume that the cast will work, since copyBlock hould never be there except with Turing.
                TMState buffer = ((TuringMachineBuildingBlocks) drawer.getAutomaton()).createTMState(new Point(state.getPoint().x+4, state.getPoint().y)); //again, we assume that the cast will work, since copyBlock hould never be there except with Turing.
                buffer.setInnerTM((TuringMachineBuildingBlocks)((TMState) state).getInnerTM().clone()); //all states have an inner TM, although this inner TM might have zero states within it, in which case it acts as a simple state.
                break;
            case replaceSymbol_DEFAULT:
                assert state instanceof TMState;

                String replaceWith = null;
                String toReplace = null;
                String old = JOptionPane.showInputDialog(null, "Find");
                if (old == null)
                    return;
                toReplace = old;

                String newString = JOptionPane.showInputDialog(null, "Replace With");
                if (newString == null)
                    return;
                replaceWith = newString;

                replaceCharactersInBlock((TMState) state, toReplace, replaceWith);
                break;
        }
        view.repaint();
    }

    /**
     * this shall be a recursive method, replacing the inside and then the out
     *
     * @param start
     * @param toReplace
     * @param replaceWith
     */
    private void replaceCharactersInBlock(TMState start, String toReplace, String replaceWith){
        TuringMachineBuildingBlocks tm = start.getInnerTM();

        for (int i = 0; i < tm.getStates().length; i++)
            replaceCharactersInBlock((TMState)tm.getStates()[i], toReplace, replaceWith);

        Transition[] trans = tm.getTransitions();

        for (int i = 0; i < trans.length; ++i){
            TMTransition tmTrans = (TMTransition)trans[i];
            for(int k = 0; k < tmTrans.tapes(); k++){
                String read = tmTrans.getRead(k);
                tmTrans.setRead(k, read.replaceAll(toReplace, replaceWith));
                String write = tmTrans.getWrite(k);
                tmTrans.setWrite (k,write.replaceAll(toReplace, replaceWith));
            }
        }
    }
}
