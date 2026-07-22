package gui.action;

import gui.environment.EnvironmentFrame;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class KeyboardShortcutsAction extends RestrictedAction {
    EnvironmentFrame environmentFrame;

    public KeyboardShortcutsAction(EnvironmentFrame environmentFrame) {
        super("Keyboard shortcuts", null);
        this.environmentFrame = environmentFrame;
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_SLASH,
                MAIN_MENU_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        environmentFrame.showKeyboardShortcutsPopup();
    }
}
