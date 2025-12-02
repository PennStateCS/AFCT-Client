package gui.editor;

import gui.environment.Environment;
import gui.environment.EnvironmentFrame;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class EditorKeyBindings {
    public static void SetUpKeyBindings(EnvironmentFrame environmentFrame) {
        Environment environment = environmentFrame.getEnvironment();

        JPanel contentPane = (JPanel) environmentFrame.getContentPane();
        int condition = JComponent.WHEN_IN_FOCUSED_WINDOW;
        InputMap inputMap = contentPane.getInputMap(condition);
        ActionMap actionMap = contentPane.getActionMap();

        // Delete action
        addActionNoModifiers("delete", KeyEvent.VK_DELETE, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleDelete();
            }
        });

        // Backspace action
        addActionNoModifiers("backspace", KeyEvent.VK_BACK_SPACE, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleDelete();
            }
        });

        // ctrl+d action
        addCTRLAction("duplicate", KeyEvent.VK_D, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleDuplicate();
            }
        });

        // ctrl+c action
        addCTRLAction("copy", KeyEvent.VK_C, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleCopy();
            }
        });

        // ctrl+x action
        addCTRLAction("cut", KeyEvent.VK_X, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleCut();
            }
        });

        // ctrl+v action
        addCTRLAction("paste", KeyEvent.VK_V, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handlePaste();
            }
        });

        // ctrl+z action
        addCTRLAction("undo", KeyEvent.VK_Z, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleUndo();
            }
        });

        // ctrl+shift+z action
        addAction("redo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleRedo();
            }
        });
    }

    private static void addAction(String actionName, KeyStroke keyStroke, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(keyStroke, actionName);
        actionMap.put(actionName, action);
    }

    private static void addActionNoModifiers(String actionName, int keyEvent, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(KeyStroke.getKeyStroke(keyEvent, 0), actionName);
        actionMap.put(actionName, action);
    }

    private static void addCTRLAction(String actionName, int keyEvent, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(KeyStroke.getKeyStroke(keyEvent, InputEvent.CTRL_DOWN_MASK), actionName);
        actionMap.put(actionName, action);
    }
}
