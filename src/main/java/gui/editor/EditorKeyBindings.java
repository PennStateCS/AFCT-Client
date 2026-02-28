package gui.editor;

import gui.environment.Environment;
import gui.environment.EnvironmentFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;

/**
 * @author Jesse Burdick-Pless
 */
public class EditorKeyBindings {
    public static final int CTRL_CMD_SHORTCUT_MASK;
    // TODO: test that this still works for Windows, macOS, and Linux
    static {
        if (GraphicsEnvironment.isHeadless()) {
            // choose a safe default for headless environments to avoid crashes
            CTRL_CMD_SHORTCUT_MASK = InputEvent.CTRL_DOWN_MASK;
        } else {
            CTRL_CMD_SHORTCUT_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        }
    }

    public static HashMap<String, ArrayList<Shortcut>> shortcutMap = new HashMap<>();
    public static String[] sectionOrder = null;
    private static String currentSection = "";
    private static boolean setupComplete = false;

    public enum Modifiers {
        OR,
        AND,
        NONE
    }

    public static class Shortcut {
        // TODO: implement support for shortcuts like "ctrl+c OR ctrl+insert"
        public String displayName;
        public String actionName;
        public Integer keyEvent = null;
        public int[] modifiers;
        Modifiers modifierType = null; // KeyEvent.VK_UNDEFINED
        public Integer mouseButton = null;
        public Shortcut alternateShortcut = null;

        public Shortcut(String displayName, String actionName, int keyEvent, int[] modifiers, Modifiers modifierType) {
            this.displayName = displayName;
            this.actionName = actionName;
            this.keyEvent = keyEvent;
            this.modifiers = modifiers;
            this.modifierType = modifierType;
        }

        public Shortcut(String displayName, String actionName, int keyEvent, int[] modifiers) {
            this.displayName = displayName;
            this.actionName = actionName;
            this.keyEvent = keyEvent;
            this.modifiers = modifiers;
            this.modifierType = Modifiers.AND;
        }

        public Shortcut(String displayName, String actionName, Integer keyEvent, Integer mouseButton, int[] modifiers, Modifiers modifierType) {
            this.displayName = displayName;
            this.actionName = actionName;
            this.keyEvent = keyEvent;
            this.mouseButton = mouseButton;
            this.modifiers = modifiers;
            this.modifierType = modifierType;
        }

        public static Shortcut mouseShortcut(String displayName, String actionName, int keyEvent, int mouseButton, int[] modifiers) {
            return new Shortcut(displayName, actionName, keyEvent, mouseButton, modifiers, Modifiers.AND);
        }

        public static Shortcut mouseShortcut(String displayName, String actionName, int mouseButton, int[] modifiers) {
            return new Shortcut(displayName, actionName, null, mouseButton, modifiers, Modifiers.AND);
        }

        public static Shortcut mouseShortcut(String displayName, String actionName, int mouseButton, int[] modifiers, Modifiers modifierType) {
            return new Shortcut(displayName, actionName, null, mouseButton, modifiers, modifierType);
        }
    }

    public static void SetUpKeyBindings(EnvironmentFrame environmentFrame) {
        String editingSection = "Editing";
        String selectionSection = "Selection";
        String generalShortcutsSection = "General shortcuts";
        if (sectionOrder == null) {
            ArrayList<String> order = new ArrayList<>();
            order.add(selectionSection);
            order.add(editingSection);
            order.add(generalShortcutsSection);
            sectionOrder = order.toArray(new String[0]);
        } else {
            setupComplete = true;
        }

        Environment environment = environmentFrame.getEnvironment();

        JPanel contentPane = (JPanel) environmentFrame.getContentPane();
        int condition = JComponent.WHEN_IN_FOCUSED_WINDOW;
        InputMap inputMap = contentPane.getInputMap(condition);
        ActionMap actionMap = contentPane.getActionMap();

        // TODO: make it so that when actions are added, they are also put in a list of keyboard shortcuts
        //      that can be displayed to the user.

        String displayName;

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


        // Begin "Editing" section
        currentSection = editingSection;

        // TEST ACTION
        displayName = "TEST";
        addMouseShortcut("test", MouseEvent.BUTTON3, KeyEvent.VK_T, displayName, CTRL_CMD_SHORTCUT_MASK, ALT_DOWN_MASK, SHIFT_DOWN_MASK);

        // ctrl+d action
        displayName = "Duplicate selected states (and connected transitions)";
        addCTRLAction("duplicate", KeyEvent.VK_D, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleDuplicate(false);
            }
        });

        // ctrl+shift+d action
        // TODO: Decide if ctrl+shift should create states with original (like rn) or reset state names
        displayName = "Duplicate selected states (and connected transitions) keeping the original state names";
        addCTRLShiftAction("duplicatespecial", KeyEvent.VK_D, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleDuplicate(true);
            }
        });

        // ctrl+c action
        displayName = "Cut selected states";
        addCTRLAction("copy", KeyEvent.VK_C, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleCopy();
            }
        });

        // ctrl+x action
        displayName = "Cut selected states";
        addCTRLAction("cut", KeyEvent.VK_X, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleCut();
            }
        });

        // ctrl+v action
        displayName = "Paste automaton from clipboard";
        displayName = "Paste";
        addCTRLAction("paste", KeyEvent.VK_V, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handlePaste(false);
            }
        });

        // ctrl+shift+v action
        displayName = "Paste without state names";
        addCTRLShiftAction("pastespecial", KeyEvent.VK_V, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handlePaste(true);
            }
        });

        // ctrl+z action
        displayName = "Undo";
        addCTRLAction("undo", KeyEvent.VK_Z, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleUndo();
            }
        });

        // ctrl+shift+z action
        displayName = "Redo";
        addCTRLShiftAction("redo", KeyEvent.VK_Z, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleRedo();
            }
        });


        // Begin "Selection" section
        currentSection = selectionSection;

        // ctrl+a action
        displayName = "Select all states";
        //displayName = "Select all";
        addCTRLAction("selectall", KeyEvent.VK_A, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                environment.handleSelectAll();
                environmentFrame.getContentPane().repaint();
            }
        });


        // ctrl+mouse1 (left-click) OR shift+mouse1 (left-click)
        displayName = "Add/remove state from selection";
        addMouseShortcutOR("ctrlorshiftclick", MouseEvent.BUTTON1, displayName, CTRL_CMD_SHORTCUT_MASK, SHIFT_DOWN_MASK);

        // shift+mouse1 drag (left-click)
        displayName = "Add states to selection on mouse drag";
        addMouseShortcut("shiftdrag", MouseEvent.BUTTON1, displayName, SHIFT_DOWN_MASK);




        // TODO: maybe this should be in it's own "View" section?
        // alt+mouse1 (left-click)
        displayName = "Highlight connected states and transitions";
        addMouseShortcut("altclick", MouseEvent.BUTTON1, displayName, ALT_DOWN_MASK);



        // Begin "General shortcuts" section
        currentSection = generalShortcutsSection;


        // ctrl+shift+n to create a new instance of the currently selected type of editor window
        displayName = "New instance of the current type";



        // ctrl+/ action
        displayName = "Show keyboard shortcuts";
        addCTRLAction("showkeyboardshortcuts", KeyEvent.VK_SLASH, displayName, inputMap, actionMap, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO show the keyboard shortcuts popup (maybe have one instance globally?)
                environmentFrame.showKeyboardShortcutsPopup();
            }
        });

        setupComplete = true;
    }

    private static void addAction(String actionName, KeyStroke keyStroke, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(keyStroke, actionName);
        actionMap.put(actionName, action);
    }

    private static void addActionNoModifiers(String actionName, int keyEvent, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(KeyStroke.getKeyStroke(keyEvent, 0), actionName);
        actionMap.put(actionName, action);
    }

    private static void addCTRLAction(String actionName, int keyEvent, String displayName, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(KeyStroke.getKeyStroke(keyEvent, CTRL_CMD_SHORTCUT_MASK), actionName);
        actionMap.put(actionName, action);
        addShortcut(actionName, keyEvent, displayName, CTRL_CMD_SHORTCUT_MASK);
    }

    private static void addCTRLShiftAction(String actionName, int keyEvent, String displayName, InputMap inputMap, ActionMap actionMap, AbstractAction action) {
        inputMap.put(KeyStroke.getKeyStroke(keyEvent, CTRL_CMD_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK), actionName);
        actionMap.put(actionName, action);
        addShortcut(actionName, keyEvent, displayName, CTRL_CMD_SHORTCUT_MASK, InputEvent.SHIFT_DOWN_MASK);
    }

    private static void addShortcut(String actionName, int keyEvent, String displayName, int... modifiers) {
        Shortcut shortcut = new Shortcut(displayName, actionName, keyEvent, modifiers);
        addShortcut(shortcut);
    }

    private static void addMouseShortcut(String actionName, int mouseButton, int keyEvent, String displayName, int... modifiers) {
        Shortcut shortcut = Shortcut.mouseShortcut(displayName, actionName, keyEvent, mouseButton, modifiers);
        addShortcut(shortcut);
    }

    private static void addMouseShortcut(String actionName, int mouseButton, String displayName, int... modifiers) {
        Shortcut shortcut = Shortcut.mouseShortcut(displayName, actionName, mouseButton, modifiers);
        addShortcut(shortcut);
    }

    private static void addMouseShortcutOR(String actionName, int mouseButton, String displayName, int... modifiers) {
        Shortcut shortcut = Shortcut.mouseShortcut(displayName, actionName, mouseButton, modifiers, Modifiers.OR);
        addShortcut(shortcut);
    }

    private static void addShortcut(Shortcut shortcut) {
        if (setupComplete) {
            return;
        }
        //shortcutMap.putIfAbsent(currentSection, new ArrayList<>());
        if (!shortcutMap.containsKey(currentSection)) {
            shortcutMap.put(currentSection, new ArrayList<>());
        }
        shortcutMap.get(currentSection).add(shortcut);
    }
}
