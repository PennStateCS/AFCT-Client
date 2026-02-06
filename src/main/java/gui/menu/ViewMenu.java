package gui.menu;

import gui.editor.EditorPane;
import gui.environment.Environment;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ViewMenu extends JMenu {
    private static final String stateLabels_TEXT = "Display State Labels";
    private static final String adaptView_TEXT = "Auto-Zoom";
    private Environment environment;
    private AutomatonPane view;
    private AutomatonDrawer drawer;
    private JCheckBoxMenuItem stateLabelsCheckbox, autoZoomCheckbox;

    public ViewMenu(Environment environment) {
        super("View");
        this.environment = environment;
        EditorPane ep = (EditorPane)environment.getActive();
        ContextActions contextActions = ep.getDrawer().contextActions;
        this.view = contextActions.getView();
        this.drawer = contextActions.getDrawer();

        stateLabelsCheckbox = new JCheckBoxMenuItem(stateLabels_TEXT);
        autoZoomCheckbox = new JCheckBoxMenuItem(adaptView_TEXT);

        setupMenuOptions();

        this.add(stateLabelsCheckbox);
        this.add(autoZoomCheckbox);
    }

    private void setupMenuOptions() {
        // stateLabelsCheckbox
        stateLabelsCheckbox.setSelected(true);
        stateLabelsCheckbox.addActionListener(new  ActionListener() {
            public void actionPerformed(ActionEvent e) {
                drawer.shouldDrawStateLabels(stateLabelsCheckbox.isSelected());
            }
        });

        // autoZoomCheckbox
        autoZoomCheckbox.setSelected(false);
        autoZoomCheckbox.addActionListener(new  ActionListener() {
            public void actionPerformed(ActionEvent e) {
                view.setAdapt(autoZoomCheckbox.isSelected());
            }
        });
    }
}
