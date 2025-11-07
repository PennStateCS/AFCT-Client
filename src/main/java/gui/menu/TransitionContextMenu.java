package gui.menu;

import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class TransitionContextMenu extends ContextMenu implements ActionListener {
    public TransitionContextMenu(AutomatonPane view, AutomatonDrawer drawer) {
        super(view, drawer);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }
}
