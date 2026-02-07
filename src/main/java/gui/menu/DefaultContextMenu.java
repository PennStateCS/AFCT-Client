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
    private JMenuItem layoutGraph, renameStates, adaptView;

    private static final String stateLabels_TEXT = "Display State Labels";
    private static final String layoutGraph_TEXT = "Layout Graph";
    private static final String renameStates_TEXT = "Rename States";
    private static final String adaptView_TEXT = "Auto-Zoom";


    public DefaultContextMenu(AutomatonPane view, AutomatonDrawer drawer) {
        super(view, drawer);

        stateLabels = new JCheckBoxMenuItem(stateLabels_TEXT);
        layoutGraph = new JMenuItem(layoutGraph_TEXT);
        renameStates = new JMenuItem(renameStates_TEXT);
        adaptView = new JCheckBoxMenuItem(adaptView_TEXT);
    }

    public void addMenuItems(MenuElement menu, boolean displayOnly) {
        //addMenuItemHelper(menu, stateLabels); // moved to view menu

        if (!displayOnly) {
            addMenuItemHelper(menu, layoutGraph);
            addMenuItemHelper(menu, renameStates);
            addMenuItemHelper(menu, addNote);
            //addMenuItemHelper(menu, adaptView); // moved to view menu
        }
        allListenersAdded = true;
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
                //TODO make it more clear what this is doing.
                ((AutomatonEnvironment)drawer.getAutomaton().getEnvironmentFrame().getEnvironment()).saveStatus();
                StateRenamer.rename(drawer.getAutomaton());
                break;
            case addNote_TEXT:
                addNote(myPoint);
                break;
            case adaptView_TEXT:
                view.setAdapt(item.isSelected());
                break;
        }
        view.repaint();
    }
}
