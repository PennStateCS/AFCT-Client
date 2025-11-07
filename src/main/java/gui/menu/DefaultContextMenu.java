package gui.menu;

import automata.Note;
import automata.StateRenamer;
import automata.graph.AutomatonGraph;
import automata.graph.LayoutAlgorithm;
import automata.graph.layout.GEMLayoutAlgorithm;
import gui.editor.ArrowDisplayOnlyTool;
import gui.editor.ArrowTool;
import gui.environment.AutomatonEnvironment;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;

public class DefaultContextMenu extends ContextMenu implements ActionListener {
    private Point myPoint;
    private Note curNote;

    private JCheckBoxMenuItem stateLabels;
    private JMenuItem layoutGraph, addNote, renameStates, adaptView;

    private static final String stateLabels_TEXT = "Display State Labels";
    private static final String layoutGraph_TEXT = "Layout Graph";
    private static final String renameStates_TEXT = "Rename States";
    private static final String addNote_TEXT = "Add Note";
    private static final String adaptView_TEXT = "Auto-Zoom";

    private static final String DEFAULT_NOTE_TEXT = "insert_text";


    public DefaultContextMenu(AutomatonPane view, AutomatonDrawer drawer) {
        super(view, drawer);

        stateLabels = new JCheckBoxMenuItem(stateLabels_TEXT);
        layoutGraph = new JMenuItem(layoutGraph_TEXT);
        renameStates = new JMenuItem(renameStates_TEXT);
        addNote = new JMenuItem(addNote_TEXT);
        adaptView = new JCheckBoxMenuItem(adaptView_TEXT);
    }

    public void addMenuItems(MenuElement menu, boolean displayOnly) {
        addMenuItemHelper(menu, stateLabels);

        if (!displayOnly) {
            addMenuItemHelper(menu, layoutGraph);
            addMenuItemHelper(menu, renameStates);
            addMenuItemHelper(menu, addNote);
            addMenuItemHelper(menu, adaptView);
        }
    }

    public void selectAndEnableMenuItems(Point p) {
        stateLabels.setSelected(drawer.doesDrawStateLabels());
        adaptView.setSelected(view.getAdapt());
        myPoint = Objects.requireNonNullElseGet(p, () -> new Point(0, 0));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JMenuItem item = (JMenuItem) e.getSource();

        switch (item.getText()) {
            case stateLabels_TEXT:
                drawer.shouldDrawStateLabels(item.isSelected());
                break;
            case layoutGraph_TEXT:
                AutomatonGraph g = new AutomatonGraph(drawer.getAutomaton());
                LayoutAlgorithm alg = new GEMLayoutAlgorithm();
                alg.layout(g, null);
                g.moveAutomatonStates();
                view.fitToBounds(30);
                break;
            case renameStates_TEXT:
                ((AutomatonEnvironment)drawer.getAutomaton().getEnvironmentFrame().getEnvironment()).saveStatus();
                StateRenamer.rename(drawer.getAutomaton());
            case addNote_TEXT:
                view.setAdapt(item.isSelected());
                break;
            case adaptView_TEXT:
                ((AutomatonEnvironment)drawer.getAutomaton().getEnvironmentFrame().getEnvironment()).saveStatus();
                Note newNote = new Note(myPoint, "insert_text");
                newNote.initializeForView(view);
                drawer.getAutomaton().addNote(newNote);
                break;
        }
        view.repaint();
    }
}
