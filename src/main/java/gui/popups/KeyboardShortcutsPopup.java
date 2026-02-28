package gui.popups;

import gui.editor.EditorKeyBindings;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import static gui.Globals.*;

public class KeyboardShortcutsPopup implements ExtensionPopup {
    private boolean popupShown = false;
    private final JFrame frame;
    private final JPanel headerPane;
    private final JPanel contentPane;
    private final JScrollPane scrollPane;
    private final JTextField searchField;
    private final JPanel shortcutsPanel;
    private final Font monospacedFont;
    private ShortcutSection[] shortcutSections;

    public KeyboardShortcutsPopup() {
        popups.add(this);

        // create frame
        frame = new JFrame();

        // Initialize GUI elements
        headerPane = new JPanel();
        headerPane.setLayout(new BoxLayout(headerPane, BoxLayout.Y_AXIS));
        contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        searchField = new JTextField();
        shortcutsPanel = new JPanel();
        shortcutsPanel.setLayout(new BoxLayout(shortcutsPanel, BoxLayout.PAGE_AXIS));
        int fontSize = 14;
        fontSize = 16;
        monospacedFont = new Font("Monospaced", Font.PLAIN, fontSize);

        int vrtInset = 15;
        int hozInset = 20;
        setupGui(vrtInset, hozInset);
        setupEventHandlers();

        // Complete setup
        shortcutsPanel.setBorder(new EmptyBorder(vrtInset, hozInset, vrtInset, hozInset));
        scrollPane = new JScrollPane(shortcutsPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // TODO: do this for the editor window too

        headerPane.setBorder(new EmptyBorder(vrtInset, hozInset, vrtInset-5, hozInset));
        contentPane.add(headerPane);
        contentPane.add(scrollPane);


        frame.getContentPane().add(contentPane);
        frame.setVisible(false);
    }

    private void setupGui(int vrtInset, int hozInset) {
        // Create headerLabel
        JLabel headerLabel = new JLabel("Keyboard Shortcuts");
        changeSize(headerLabel, 24);
        headerLabel.setBorder(new EmptyBorder(0, 0, vrtInset, 0));
        // Add headerLabel to headerPane
        headerPane.add(headerLabel);

        // create searchFieldPanel
//        JPanel feedbackLabelPanel = new JPanel(new GridBagLayout());
//        feedbackLabelPanel.setBackground(Color.WHITE);
//        feedbackLabelPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

        // TODO: add a search icon to the left part of the searchField
        //  Try a modified version of the code below?
        //  Icon icon = new ImageIcon(getClass().getResource("/images/search.png"));
        //  Border matteBorder = new MatteBorder(0, icon.getIconWidth() + 5, 0, 0, icon);
        //  searchField.setBorder(BorderFactory.createCompoundBorder(searchField.getBorder(), matteBorder));

        // Add searchField to headerPane
        searchField.setBorder(new EmptyBorder(12, 12, 12, 12));
//        searchField.setBorder(new CompoundBorder(
//                BorderFactory.createLineBorder(Color.GRAY, 1),
//                new EmptyBorder(12, 12, 12, 12)
//        ));
//        searchField.setBorder(new CompoundBorder(
//                new RoundedBorder(30),
//                new EmptyBorder(12, 12, 12, 12)
//        ));
        changeSize(searchField, monospacedFont.getSize());
        headerPane.add(searchField);

        populateGui(vrtInset, 0);
    }

    private void populateGui(int vrtInset, int hozInset) {
        ArrayList<ShortcutSection> sections = new ArrayList<>();
        for (String sectionTitle : EditorKeyBindings.sectionOrder) {
            ShortcutSection sectionPanel = createShortcutSection(sectionTitle, EditorKeyBindings.shortcutMap.get(sectionTitle));
            sectionPanel.setBorder(BorderFactory.createEmptyBorder(vrtInset, hozInset, vrtInset, hozInset));
            shortcutsPanel.add(sectionPanel);
            sections.add(sectionPanel);
        }
        shortcutSections = sections.toArray(new ShortcutSection[0]);
    }

    private ShortcutSection createShortcutSection(String sectionTitle, ArrayList<EditorKeyBindings.Shortcut> shortcuts) {
        int vrtPadding = 16;

        ShortcutSection sectionPanel = new ShortcutSection(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // Create headerLabel
        JLabel headerLabel = new JLabel(sectionTitle);
        headerLabel.setBorder(new EmptyBorder(vrtPadding, 0, vrtPadding, 0));
        //changeSize(headerLabel, 16);
        changeSize(headerLabel, 20);
        // Add headerLabel to sectionPanel
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        sectionPanel.add(headerLabel, c);

        // Add shortcuts
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        for (int i = 0; i < shortcuts.size(); i++) {
            ShortcutPanel shortcutPanel = addShortcut(shortcuts.get(i));
            if (i == 0) {
                shortcutPanel.setBorder(new EmptyBorder(vrtPadding, 0, vrtPadding, 0));
            } else {
                shortcutPanel.setBorder(new CompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(196, 199, 197)),
                        new EmptyBorder(vrtPadding, 0, vrtPadding, 0)
                ));
            }
            sectionPanel.add(shortcutPanel, c);
            sectionPanel.track(shortcutPanel);
            c.gridy = y++;
        }

        return sectionPanel;
    }

    private ShortcutPanel addShortcut(EditorKeyBindings.Shortcut shortcut) {
        StringBuilder text = new StringBuilder();
//        BorderLayout borderLayout = new BorderLayout();
//        borderLayout.setHgap(4);
        //FlowLayout layout = new FlowLayout(FlowLayout.LEFT, 4, 0);
        //SpringLayout layout = new SpringLayout();
//        GridBagLayout layout = new GridBagLayout();
//        JPanel panel = new JPanel(layout);
        ShortcutPanel panel = new ShortcutPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        //SpringLayout.Constraints c = layout.getConstraints(panel);
//        GridBagConstraints c = new GridBagConstraints();
//        int x = 0;
//        c.gridx = x++;
//        c.gridy = 0;

        // Create descriptionLabel
        JLabel descriptionLabel = new JLabel(shortcut.displayName);
        changeSize(descriptionLabel, monospacedFont.getSize());
        unBoldFont(descriptionLabel);

        text.append(shortcut.displayName);
        text.append(" ");

        // Add descriptionLabel to panel
//        panel.add(descriptionLabel, BorderLayout.LINE_START);
        //panel.add(descriptionLabel);
        //layout.putConstraint(SpringLayout.WEST, descriptionLabel, 0, SpringLayout.WEST, panel);
//        c.anchor = GridBagConstraints.LINE_START;
//        panel.add(descriptionLabel, c);
        panel.add(descriptionLabel);

        panel.add(Box.createHorizontalGlue());

//        JLabel last = descriptionLabel;


        //c.anchor = GridBagConstraints.LINE_END;
        // add the stylized keyboard commands here
//        for (int VK_modifier : shortcut.modifiers) {
//            JLabel keyLabel = createDialogKey(KeyEvent.getKeyText(VK_modifier));
////            panel.add(keyLabel, BorderLayout.LINE_END);
////            panel.add(keyLabel, FlowLayout.RIGHT);
////            panel.add(keyLabel);
////            if (last != null) {
////                layout.putConstraint(SpringLayout.EAST, last, 4, SpringLayout.EAST, keyLabel);
////            }
////            last = keyLabel;
//
//            //panel.add(keyLabel, c);
//            //c.gridx = x++;
//            panel.add(keyLabel);
//        }
        //layout.putConstraint(SpringLayout.EAST, last, 0, SpringLayout.EAST, panel);

        //// add the stylized keyboard commands here (added right to left)
        int hPad = 4;

        JLabel[] shortcutKeys = getShortcutKeys(shortcut);

        for (int i = 0; i < shortcutKeys.length; i++) {
            panel.add(shortcutKeys[i]);
            text.append(shortcutKeys[i].getText());
            if (i < shortcutKeys.length - 1) {
                panel.add(Box.createRigidArea(new Dimension(hPad,0)));
                text.append(" ");
            }
        }

        panel.text = text.toString();

        return panel;
    }

    private class ShortcutSection extends JPanel {
        protected ArrayList<ShortcutPanel> trackedPanels;

        public ShortcutSection(GridBagLayout gridBagLayout) {
            super(gridBagLayout);
            trackedPanels = new ArrayList<>();
        }

        public void track(ShortcutPanel shortcutPanel) {
            trackedPanels.add(shortcutPanel);
        }
    }

    private class ShortcutPanel extends JPanel {
        protected String text;
    }

    private JLabel[] getShortcutKeys(EditorKeyBindings.Shortcut shortcut) {
        ArrayList<JLabel> keys = new ArrayList<>();

        for (int i = 0; i < shortcut.modifiers.length; i++) {
            int VK_modifier = shortcut.modifiers[i];
            JLabel keyLabel = createDialogKey(KeyEvent.getModifiersExText(VK_modifier));
            keys.add(keyLabel);
        }

        // mouseButton should end up last
        if (shortcut.mouseButton != null) {
            String text = switch(shortcut.mouseButton) {
                case MouseEvent.BUTTON1 -> "Click";
                case MouseEvent.BUTTON2 -> "Middle-Click";
                case MouseEvent.BUTTON3 -> "Right-Click";
                default -> "Click";
            };
            keys.add(createDialogKey(text));
        }

        if (shortcut.keyEvent != null) {
            keys.add(createDialogKey(KeyEvent.getKeyText(shortcut.keyEvent)));
        }

        return keys.toArray(new JLabel[0]);
    }

    private JLabel createDialogKey(String text) {
        JLabel label = new JLabel(text);
        label.setFont(monospacedFont);

        int radius = 4;
        radius = 8;
        //radius = 16;
        radius = 12;
        radius = 10;

        //label.setBorder(new RoundedBorder(radius));

        int hoz = -4;
        hoz = -8;
        hoz = -4;
        int vrt = -16;
        vrt = -18;
        vrt = 0;
        label.setBorder(new CompoundBorder(
                new RoundedBorder(radius),
                new EmptyBorder(vrt, hoz, vrt, hoz)
        ));

        //label.setBorder(new EmptyBorder(0, 0, 0, 0));

        label.setForeground(new Color(31, 31, 31));

        return label;
    }


    /**
     * Sets action listeners for user inputs.
     */
    private void setupEventHandlers() {
        handlers_search();
    }

    public void showPopup(JFrame window) {
        popupShown = true;
        // pack the frame
        frame.pack();

        positionFrameNearWindow(frame, Position.CENTER, window);

        // display the popup
        frame.setVisible(true);
    }

    public void handlers_search() {
        // TODO filter the shortcuts
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                e.getKeyCode();
            }
        });
    }

    @Override
    public void closePopup() {
        popupShown = false;
        frame.dispose();
    }
}
