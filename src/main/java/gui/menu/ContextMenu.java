package gui.menu;

import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class ContextMenu implements ActionListener {
    protected AutomatonPane view;
    protected AutomatonDrawer drawer;
    protected boolean allListenersAdded = false;

    public ContextMenu(AutomatonPane view, AutomatonDrawer drawer) {
        this.view = view;
        this.drawer = drawer;
    }

    //void addMenuItems(MenuElement menu);
    protected void addMenuItemHelper(MenuElement menu, JMenuItem item) {
        if (menu instanceof JPopupMenu) {
            ((JPopupMenu) menu).add(item);
        } else if (menu instanceof JMenu) {
            ((JMenu) menu).add(item);
        }
        if (!allListenersAdded) {
            item.addActionListener(this);
        }
    }

    @Override
    public abstract void actionPerformed(ActionEvent e);
}
