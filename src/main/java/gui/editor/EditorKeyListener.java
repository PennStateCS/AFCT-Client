package gui.editor;

import gui.environment.Environment;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class EditorKeyListener extends KeyAdapter {
    /**
     * General constants
     */
    private final int UNDO = KeyEvent.CTRL_DOWN_MASK;
    private final int CTRL_SHIFT_MASK = KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK;
    private final int REDO = CTRL_SHIFT_MASK;

    /** The environment that this frame displays. */
    private Environment environment;

    public EditorKeyListener(Environment environment) {
        this.environment = environment;

    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            // delete or backspace pressed
            case KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
                environment.handleDelete();
            }
            // d pressed
            case KeyEvent.VK_D -> {
                if (e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    // ctrl+d pressed
                    environment.handleDuplicate();
                }
            }
            // c pressed
            case KeyEvent.VK_C -> {
                if (e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    // ctrl+c pressed
                    environment.handleCopy();
                }
            }
            // x pressed
            case KeyEvent.VK_X -> {
                if (e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    // ctrl+x pressed
                    environment.handleCut();
                }
            }
            // v pressed
            case KeyEvent.VK_V -> {
                if (e.getModifiersEx() == KeyEvent.CTRL_DOWN_MASK) {
                    // ctrl+v pressed
                    environment.handlePaste();
                }
            }
            // z pressed
            case KeyEvent.VK_Z -> {
                if ((e.getModifiersEx() & CTRL_SHIFT_MASK) == UNDO) {
                    // ctrl+z pressed
                    environment.handleUndo();
                } else if ((e.getModifiersEx() & CTRL_SHIFT_MASK) == REDO) {
                    // ctrl+shift+z pressed
                    environment.handleRedo();
                }
            }
        }
    }
}
