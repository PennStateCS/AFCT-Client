package submission;

import gui.Globals;
import gui.environment.Environment;
import gui.environment.EnvironmentFrame;
import gui.environment.Universe;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;

/**
 * The "File to Submit" row: which file will be sent, and the two ways to change
 * it. Owns the one piece of state that matters — whether the student browsed to
 * a file explicitly (which stops the display auto-following the editor's open
 * file until "Use open file" snaps it back).
 */
class SubmissionFilePanel extends JPanel {

    private final Environment environment;
    /** Receives the window-title text whenever the shown file changes. */
    private final Consumer<String> titleSetter;

    private final JTextField fileField = new JTextField();
    private final JButton useOpenFileBtn = new JButton("Use open file");
    private final JButton browseBtn = new JButton("Browse…");

    private File selectedFile = null;
    private boolean fileManuallyChosen = false;

    SubmissionFilePanel(Environment environment, Consumer<String> titleSetter) {
        super(new BorderLayout(8, 0));
        this.environment = environment;
        this.titleSetter = titleSetter;
        setOpaque(false);

        // Current file display. Defaults to the file open in the editor; the buttons
        // beside it let the user browse to a different file or snap back to the open one.
        fileField.setEditable(false);
        fileField.setMargin(new Insets(6, 10, 6, 10));
        fileField.setForeground(Theme.TEXT_DARK);
        fileField.setBackground(new Color(0xFA, 0xFB, 0xFC));
        fileField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CARD_BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        fileField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fileField.setToolTipText("The file that will be submitted. Click to browse for another.");
        fileField.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                browseForFile();
            }
        });

        useOpenFileBtn.setToolTipText("Submit the file currently open in the editor");
        Theme.styleTintedButton(useOpenFileBtn);
        Globals.setPointerCursor(useOpenFileBtn);
        useOpenFileBtn.addActionListener(e -> useOpenFile());

        browseBtn.setToolTipText("Choose a different file to submit");
        Theme.styleTintedButton(browseBtn);
        Globals.setPointerCursor(browseBtn);
        browseBtn.addActionListener(e -> browseForFile());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(useOpenFileBtn);
        buttons.add(browseBtn);

        add(fileField, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);

        // Seed the display from the editor's open file (default source).
        updateFromEditor();
    }

    /** The file to submit, or null when the editor has nothing open. */
    File chosenFile() {
        return selectedFile;
    }

    /** True when the student browsed to a file explicitly (their own file: never delete it). */
    boolean isManuallyChosen() {
        return fileManuallyChosen;
    }

    private void browseForFile() {
        JFileChooser chooser = new JFileChooser();
        // Start in the directory of the currently selected file, or user home
        if (selectedFile != null && selectedFile.getParentFile() != null) {
            chooser.setCurrentDirectory(selectedFile.getParentFile());
        }
        chooser.setDialogTitle("Choose file to submit");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File chosen = chooser.getSelectedFile();
            // The user picked a file explicitly: keep it, and stop auto-following the
            // editor's open file until they choose "Use open file".
            fileManuallyChosen = true;
            selectedFile = chosen;
            fileField.setText(chosen.getName());
            fileField.setForeground(new Color(60, 60, 60));
            fileField.setToolTipText(chosen.getAbsolutePath());
            titleSetter.accept(chosen.getName() + " - Submit");
            refreshButtons();
        }
    }

    /** Discards a browsed override and falls back to the editor's currently open file. */
    private void useOpenFile() {
        fileManuallyChosen = false;
        updateFromEditor();
    }

    /** Refreshes the display from the editor's open file (unless a browse override is active). */
    void updateFromEditor() {
        // Respect a file the user browsed to; only refresh the button state for it.
        if (fileManuallyChosen) {
            refreshButtons();
            return;
        }

        EnvironmentFrame frame = Universe.frameForEnvironment(environment);
        titleSetter.accept(frame.getDescription() + " - Submit");

        File envFile = environment.getFile();
        if (envFile != null && envFile.exists()) {
            // A saved file on disk.
            selectedFile = envFile;
            fileField.setText(envFile.getName());
            fileField.setForeground(new Color(60, 60, 60));
            fileField.setToolTipText(envFile.getAbsolutePath());
        } else if (envFile != null) {
            // Named but not saved yet: the editor's content is what will submit.
            selectedFile = envFile;
            fileField.setText(displayName(frame));
            fileField.setForeground(new Color(60, 60, 60));
            fileField.setToolTipText("Unsaved document — save it in the editor before submitting.");
        } else {
            // Brand-new unnamed document: the editor's content still submits.
            selectedFile = null;
            fileField.setText(frame.getDescription());
            fileField.setForeground(new Color(60, 60, 60));
            fileField.setToolTipText("Unsaved document — save it in the editor before submitting.");
        }

        refreshButtons();
    }

    /**
     * Enables "Use open file" only when a browsed override is active and the editor
     * actually has an open file to snap back to.
     */
    private void refreshButtons() {
        useOpenFileBtn.setEnabled(fileManuallyChosen && environment.getFile() != null);
    }

    private String displayName(EnvironmentFrame frame) {
        if (frame != null) {
            String desc = frame.getDescription();
            // Remove the dirty marker (*) if present
            if (desc != null && desc.startsWith("*")) {
                desc = desc.substring(1);
            }
            if (desc != null) return desc;
        }
        File envFile = environment.getFile();
        return envFile != null ? envFile.getName() : "Unsaved document";
    }
}
