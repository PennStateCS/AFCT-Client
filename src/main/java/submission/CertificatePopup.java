package submission;

import gui.popups.ExtensionPopup;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
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
    private CopyableJLabel subjectCountryValue;
    private CopyableJLabel subjectStateValue;
    private CopyableJLabel subjectLocalityValue;
    private CopyableJLabel subjectOrganizationValue;
    private CopyableJLabel subjectOrganizationalUnitValue;
    private CopyableJLabel subjectCommonNameValue;

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
        int top = 0;
        top = 15;
        c.insets = new Insets(top, 20, 24, 0);
        c.gridy = y++;
        contentPane.add(headerCertificateLabel, c);

        // Set constraints that are unchanged for all certificate info sections
        c.insets = new Insets(0, 0, 0, 0);
        c.gridy = y++;

        contentPane.add(setUpHeader(), c);
        c.gridy = y++;
        contentPane.add(setUpSubjectSection(), c);
    }

    private JPanel setUpHeader() {
        Color cyan_ish = new Color(0, 202, 219);
        headerPanel = new JPanel(new GridBagLayout());
        //headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, cyan_ish));
        headerPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 1, Color.GRAY),
                BorderFactory.createMatteBorder(0, 0, 3, 0, cyan_ish)
        ));

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
        //subjectPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        subjectPanel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(26, 30, 26, 30)
        ));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        int y = 0;

        //Color labelColor = new Color(32, 35, 36);
        Color labelColor = new Color(100, 100, 100);
        double labelWeightX = 0.25;
        double valueWeightX = 0.75;

        // Create subjectTitleLabel
        JLabel subjectTitleLabel = new JLabel("Subject Name");
        boldFontAndChangeSize(subjectTitleLabel, 16);
        // Add subjectTitleLabel
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 15, 30);
        c.weightx = labelWeightX;
        c.gridy = y++;
        subjectPanel.add(subjectTitleLabel, c);

        /** Create and add all info lines */

        // Country info line
        c.gridy = y++;
        subjectCountryValue = new CopyableJLabel("US"); // TODO -- change: just for testing
        addCertificateInfoLine("Country", subjectCountryValue, subjectPanel, c, labelColor, labelWeightX, valueWeightX);

        // State info line
        c.gridy = y++;
        subjectStateValue = new CopyableJLabel("State"); // TODO -- change: just for testing
        addCertificateInfoLine("State/Province", subjectStateValue, subjectPanel, c, labelColor, labelWeightX, valueWeightX);

        // Locality info line
        c.gridy = y++;
        subjectLocalityValue = new CopyableJLabel("City"); // TODO -- change: just for testing
        addCertificateInfoLine("Locality", subjectLocalityValue, subjectPanel, c, labelColor, labelWeightX, valueWeightX);

        // Organization info line
        c.gridy = y++;
        subjectOrganizationValue = new CopyableJLabel("AFCT"); // TODO -- change: just for testing
        addCertificateInfoLine("Organization", subjectOrganizationValue, subjectPanel, c, labelColor, labelWeightX, valueWeightX);

        // Organizational Unit info line
        c.gridy = y++;
        subjectOrganizationalUnitValue = new CopyableJLabel("Dev"); // TODO -- change: just for testing
        addCertificateInfoLine("Organizational Unit", subjectOrganizationalUnitValue, subjectPanel, c, labelColor, labelWeightX, valueWeightX);

        // Common Name info line
        c.gridy = y++;
        subjectCommonNameValue = new CopyableJLabel("localhost"); // TODO -- change: just for testing
        addCertificateInfoLine("Common Name", subjectCommonNameValue, subjectPanel, c, labelColor, labelWeightX, valueWeightX);

        return subjectPanel;
    }

    private void addCertificateInfoLine(String labelText, JLabel value, JPanel panel, GridBagConstraints c, Color labelColor, double labelWeightX, double valueWeightX) {
        JLabel label = new JLabel(labelText);
        addCertificateInfoLine(label, value, panel, c, labelColor, labelWeightX, valueWeightX);
    }

    private void addCertificateInfoLine(JLabel label, JLabel value, JPanel panel, GridBagConstraints c, Color labelColor, double labelWeightX, double valueWeightX) {
        // Set label font size and color
        boldFontAndChangeSize(label, 16);
        label.setForeground(labelColor);
        // Add label to panel
        c.insets = new Insets(1, 0, 1, 30);
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        c.weightx = labelWeightX;
        panel.add(label, c);

        // Set value font size
        changeSize(value, 16);
        unBoldFont(value);
        // Add value to panel
        c.insets = new Insets(1, 0, 1, 0);
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = valueWeightX;
        panel.add(value, c);
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
