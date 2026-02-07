package gui.menu;

import automata.Automaton;
import gui.editor.EditorPane;
import gui.environment.Environment;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;

public class ViewMenu extends JMenu {
    private static final String stateLabels_TEXT = "Display State Labels";
    private static final String adaptView_TEXT = "Auto-Zoom";
    private static final String highlightNondeterminism_TEXT = "Highlight Nondeterministic States";
    private Environment environment;
    private AutomatonPane view;
    private AutomatonDrawer drawer;
    private JCheckBoxMenuItem stateLabelsCheckbox, autoZoomCheckbox, highlightNondeterminismCheckbox;

    public ViewMenu(Environment environment) {
        super("View");
        this.environment = environment;
        Serializable object = environment.getObject();
        boolean isAutomata = object instanceof Automaton;

        if (isAutomata) {
            EditorPane ep = (EditorPane) environment.getActive();
            ContextActions contextActions = ep.getDrawer().contextActions;
            this.view = contextActions.getView();
            this.drawer = contextActions.getDrawer();

            stateLabelsCheckbox = new JCheckBoxMenuItem(stateLabels_TEXT);
            autoZoomCheckbox = new JCheckBoxMenuItem(adaptView_TEXT);
            highlightNondeterminismCheckbox = new JCheckBoxMenuItem(highlightNondeterminism_TEXT);

            setupAutomataMenuOptions();

            this.add(stateLabelsCheckbox);
            this.add(autoZoomCheckbox);
            this.add(highlightNondeterminismCheckbox);
        }
    }

    private void setupAutomataMenuOptions() {
        // stateLabelsCheckbox
        stateLabelsCheckbox.setSelected(drawer.doesDrawStateLabels());
        stateLabelsCheckbox.addActionListener(new  ActionListener() {
            public void actionPerformed(ActionEvent e) {
                drawer.shouldDrawStateLabels(stateLabelsCheckbox.isSelected());
            }
        });

        // autoZoomCheckbox
        autoZoomCheckbox.setSelected(view.getAdapt());
        autoZoomCheckbox.addActionListener(new  ActionListener() {
            public void actionPerformed(ActionEvent e) {
                view.setAdapt(autoZoomCheckbox.isSelected());
            }
        });

        // highlightNondeterminismCheckbox
        highlightNondeterminismCheckbox.setSelected(false);


    }
}
