package gui.components;

import javax.swing.*;
import java.io.File;

public class SaveFileDialog extends JFileChooser{
    private JFrame frame;

    public SaveFileDialog(JFrame frame) {
        super();
        setup(frame, null);
    }

    public SaveFileDialog(JFrame frame, File initialSelectedFile) {
        super();
        setup(frame, initialSelectedFile);
    }

    private void setup(JFrame frame, File initialSelectedFile) {
        this.frame = frame;
        this.setFileSelectionMode(JFileChooser.FILES_ONLY);
        this.setMultiSelectionEnabled(false);
        this.setDialogTitle("Save As");
        this.setSelectedFile(initialSelectedFile);
        this.updateUI();
    }

    public File display() {
        int option = this.showSaveDialog(frame);
        File selectedFile = null;
        if (option == JFileChooser.APPROVE_OPTION) {
            selectedFile = this.getSelectedFile();
        }
        return selectedFile;
    }

    @Override
    public void approveSelection() {
        File selectedFile = this.getSelectedFile();
        if (selectedFile == null) {
            return;
        }

        if (selectedFile.exists()) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
            panel.add(new JLabel(selectedFile.getName() + " already exists."));
            panel.add(new JLabel("Do you want to replace it?"));

            int returnVal = JOptionPane.showConfirmDialog(frame, panel, "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (returnVal == JOptionPane.NO_OPTION) {
                return;
            }
        }

        super.approveSelection();
    }
}
