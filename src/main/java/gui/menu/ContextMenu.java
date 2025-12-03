package gui.menu;

import automata.Note;
import gui.environment.AutomatonEnvironment;
import gui.viewer.AutomatonDrawer;
import gui.viewer.AutomatonPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class ContextMenu implements ActionListener {
    protected AutomatonPane view;
    protected AutomatonDrawer drawer;
    protected boolean allListenersAdded = false;

    protected JMenuItem addNote;

    protected static final String addNote_TEXT = "Add Note";

    protected static final String DEFAULT_NOTE_TEXT = "insert_text";

    public ContextMenu(AutomatonPane view, AutomatonDrawer drawer) {
        this.view = view;
        this.drawer = drawer;
        addNote = new JMenuItem(addNote_TEXT);
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

    protected Note addNote(Point point) {
        ((AutomatonEnvironment)drawer.getAutomaton().getEnvironmentFrame().getEnvironment()).saveStatus();
        Note newNote = new Note(point, DEFAULT_NOTE_TEXT);
        newNote.initializeForView(view);
        drawer.getAutomaton().addNote(newNote);
        return newNote;
    }

    @Override
    public abstract void actionPerformed(ActionEvent e);
}
