package submission;

import gui.popups.ExtensionPopup;

import javax.swing.*;
import java.awt.*;

import static gui.Globals.*;

public class CertificatePopup implements ExtensionPopup {
    private CertificateHandler certificateHandler;

    /** GUI Components */
    private final JFrame frame;
    private JPanel contentPane;
    private JScrollPane scrollPane;
    private JPanel headerPanel;
    private JLabel headerLabel;

    /** Subject section */
    private JPanel subjectPanel;
    private JLabel subjectTitleLabel;
    private JLabel subjectCountryLabel;
    private JTextField subjectCountryValue;

    public CertificatePopup(CertificateHandler certificateHandler) {
        this.certificateHandler = certificateHandler;

        this.frame = new JFrame();
        this.contentPane = new JPanel(new GridBagLayout());



        setupGui();

        this.scrollPane = new JScrollPane(this.contentPane);
        this.frame.getContentPane().add(this.scrollPane);


        // TODO: remove after testing
        showPopup();
    }

    private void setupGui() {
        int y = 0;
        GridBagConstraints c = setConstraints(1, 1, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Create headerCertificateLabel
        JLabel headerCertificateLabel = new JLabel("Certificate");
        changeSize(headerCertificateLabel, 24);
        // Add headerCertificateLabel
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 20, 24, 0);
        c.gridy = y++;
        contentPane.add(headerCertificateLabel, c);

        c.insets = new Insets(0, 0, 0, 0);
        c.gridy = y++;

        contentPane.add(setUpHeader(), c);
        c.gridy = y++;
        contentPane.add(setUpSubjectSection(), c);
    }

    private JPanel setUpHeader() {
        Color cyan_ish = new Color(0, 202, 219);
        headerPanel = new JPanel(new GridBagLayout());
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, cyan_ish));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        int y = 0;

        // Create headerLabel
        headerLabel = new JLabel("localhost"); // TODO -- change: just for testing
        changeSize(headerLabel, 17);
        headerLabel.setForeground(cyan_ish);
        // Add headerLabel
        c.anchor = GridBagConstraints.NORTH;
        c.insets = new Insets(18, 18, 15, 18);
        c.gridy = y++;
        headerPanel.add(headerLabel, c);

       return headerPanel;
    }

    private JPanel setUpSubjectSection() {
        subjectPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        int y = 0;

        Color labelColor = new Color(32, 35, 36);
        double labelWeightX = 0.25;
        double valueWeightX = 0.75;


        // Create subjectTitleLabel
        subjectTitleLabel = new JLabel("Subject Name");
        boldFontAndChangeSize(subjectTitleLabel, 16);
        // Add subjectTitleLabel
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 15, 30);
        c.weightx = labelWeightX;
        c.gridy = y++;
        subjectPanel.add(subjectTitleLabel, c);

        // Set constraints that are unchanged for all following elements
        c.insets = new Insets(1, 0, 1, 0);

        // Create subjectCountryLabel
        subjectCountryLabel = new JLabel("Country");
        boldFontAndChangeSize(subjectCountryLabel, 16);
        subjectCountryLabel.setForeground(labelColor);
        // Add subjectCountryLabel
        c.anchor = GridBagConstraints.EAST;
        c.weightx = labelWeightX;
        c.gridy = y++;
        subjectPanel.add(subjectCountryLabel, c);

        // Create subjectCountryValue
        subjectCountryValue = new JTextField("US"); // TODO -- change: just for testing
        boldFontAndChangeSize(subjectCountryValue, 16);
        // Add countryLabel
        c.anchor = GridBagConstraints.WEST;
        c.weightx = valueWeightX;
        subjectPanel.add(subjectCountryValue, c);

        return subjectPanel;
    }

    private void addCertificateInfoLine() {

    }

    private void setUpCertificateInfoSection() {

    }

    public void showPopup() {
        // pack the frame
        frame.pack();
        sizeAndCenterWindow(frame);
        // display the popup
        frame.setVisible(true);
    }

    @Override
    public void closePopup() {
        // TODO
    }
}
