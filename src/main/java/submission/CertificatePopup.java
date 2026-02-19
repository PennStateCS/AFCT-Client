package submission;

import gui.Globals;
import gui.popups.ExtensionPopup;

import javax.security.auth.x500.X500Principal;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;

import static gui.Globals.*;

public class CertificatePopup implements ExtensionPopup {
    private CertificateHandler certificateHandler;

    private final Color cyan_ish = new Color(0, 202, 219);
    //private final Color labelColor = new Color(32, 35, 36);
    private final Color labelColor = new Color(100, 100, 100);
    private final double labelWeightX = 0.25;
    private final double valueWeightX = 0.75;

    /** GUI Components */
    private final JFrame frame;
    private JPanel contentPane;
    private final JPanel cards;
    private JScrollPane scrollPane;
    private CertificateTab lastSelected = null;

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

        // Create frame
        this.frame = new JFrame();
        frame.setTitle("TEST"); // TODO: replace

        // Create contentPane
        this.contentPane = new JPanel(new GridBagLayout());

        int y = setUpHeader();
        JPanel certificateTabPanel = createCertificateTabPanel(y);
        y += 1;

        // Create cards
        this.cards = new JPanel(new CardLayout());

        // Add cards to contentPane
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = y;
        this.contentPane.add(this.cards, c);



        for (X509Certificate cert : this.certificateHandler.getCertificateChain()) {
            String commonName = cert.get(); // TODO: get the Common Name somehow
            CertificateTab certificateTab = new CertificateTab(commonName, this, cards);
            certificateTabPanel.add(certificateTab);

            JPanel infoSection = setUpInfoSections(cert);
            // TODO: handle possibility of multiple certs having the same common name
            cards.add(commonName, infoSection);

            if (this.lastSelected == null) {
                certificateTab.setAsSelectedTab(true);
                this.lastSelected = certificateTab;
            }
        }

        this.scrollPane = new JScrollPane(this.contentPane);
        this.frame.getContentPane().add(this.scrollPane);


        // TODO: remove after testing
        showPopup();
    }

    public CertificateTab getLastSelected() {
        return lastSelected;
    }

    public void setLastSelected(CertificateTab lastSelected) {
        this.lastSelected = lastSelected;
    }

    private int setUpHeader() {
        int y = 0;
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;

        // Create headerCertificateLabel
        JLabel headerCertificateLabel = new JLabel("Certificate");
        changeSize(headerCertificateLabel, 24);
        // Add headerCertificateLabel
        int top = 0;
        top = 15;
        c.insets = new Insets(top, 20, 24, 0);
        c.gridy = y++;
        this.contentPane.add(headerCertificateLabel, c);

        return y;
    }

    private JPanel createCertificateTabPanel(int y) {
        // Create certificateTabPanel
        JPanel certificateTabPanel = new JPanel();
        certificateTabPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

        // Add certificateTabPanel
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = y;
        this.contentPane.add(certificateTabPanel, c);

        return certificateTabPanel;
    }

    public class CertificateTab extends JPanel {
        private CertificatePopup certificatePopup;
        private JPanel cards;
        private JLabel headerLabel;
        private Color defaultForegroundColor;
        public boolean selected = false;
        public final String commonName;

        public CertificateTab(String commonName, CertificatePopup certificatePopup, JPanel cards) {
            super(new GridBagLayout());

            this.certificatePopup = certificatePopup;
            this.cards = cards;
            this.commonName = commonName;

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;

            // Create headerLabel
            headerLabel = new JLabel(commonName);
            changeSize(headerLabel, 17);
            defaultForegroundColor = headerLabel.getForeground();
            // Add headerLabel
            c.anchor = GridBagConstraints.NORTH;
            c.insets = new Insets(18, 18, 15, 18);
            this.add(headerLabel, c);

            setPointerCursor(this);
            CertificateTab certificateTab = this;
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setAsSelectedTab(true);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    // the mouse has entered the panel
                    if (!certificateTab.selected) {
                        //certificateTab.setBorder(BorderFactory.createMatteBorder(1, 1, 3, 1, Color.GRAY));
                        certificateTab.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, Color.GRAY));
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // the mouse has exited the panel
                    if (!certificateTab.selected) {
                        //certificateTab.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                        certificateTab.setBorder(BorderFactory.createEmptyBorder());
                    }
                }
            });
        }

        public void setAsSelectedTab(boolean selected) {
            if (selected) {
                this.selected = true;
                setDefaultCursor(this);

                CardLayout cl = (CardLayout)(cards.getLayout());
                cl.show(cards, this.commonName);

                CertificateTab lastSelected = certificatePopup.getLastSelected();
                if (lastSelected != null) {
                    lastSelected.setAsSelectedTab(false);
                }
                certificatePopup.setLastSelected(this);

                headerLabel.setForeground(cyan_ish);
                this.setBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, cyan_ish));
//                this.setBorder(new CompoundBorder(
//                        BorderFactory.createMatteBorder(1, 1, 0, 1, Color.GRAY),
//                        BorderFactory.createMatteBorder(0, 0, 3, 0, cyan_ish)
//                ));
            } else {
                this.selected = false;
                setPointerCursor(this);
                headerLabel.setForeground(defaultForegroundColor);
                //this.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
                this.setBorder(BorderFactory.createEmptyBorder());
            }
        }
    }

    private JPanel setUpInfoSections(X509Certificate cert) {
        JPanel panel = new JPanel(new GridBagLayout());
        int y = 0;
        GridBagConstraints c = setConstraints(1, 1, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;


        // Set constraints that are unchanged for all certificate info sections
        c.insets = new Insets(0, 0, 0, 0);
        c.gridy = y++;



        panel.add(setUpSubjectSection(), c);// TODO: remove

        // TODO: figure this out
        for (section : cert.sections) {
            String sectionTitle = section.title;
            JPanel infoSectionPanel = setUpTargetInfoSection(sectionTitle, section.elements);
        }

        return panel;
    }


    private JPanel setUpTargetInfoSection(String sectionTitle, [] sectionElements) {
        // TODO: make this dynamic
        JPanel panel = new JPanel(new GridBagLayout());
        //panel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        panel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(26, 30, 26, 30)
        ));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        int y = 0;

        // Create titleLabel
        JLabel titleLabel = new JLabel(sectionTitle);
        boldFontAndChangeSize(titleLabel, 16);
        // Add titleLabel
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 15, 30);
        c.weightx = labelWeightX;
        c.gridy = y++;
        panel.add(titleLabel, c);

        // TODO: figure this out
        for (element : sectionElements) {
            String elementValue = element.value;
            String elementName = element.name;

            c.gridy = y++;
            JLabel value = new CopyableJLabel(elementValue);
            addCertificateInfoLine(elementName, value, panel, c);
        }

        return panel;
    }


    private JPanel setUpSubjectSection() {
        // TODO: make this dynamic
        subjectPanel = new JPanel(new GridBagLayout());
        //subjectPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        subjectPanel.setBorder(new CompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(26, 30, 26, 30)
        ));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        int y = 0;

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

        // TODO: this should be done dynamically based on what is is the cert chain

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

    private void addCertificateInfoLine(String labelText, JLabel value, JPanel panel, GridBagConstraints c) {
        JLabel label = new JLabel(labelText);
        addCertificateInfoLine(label, value, panel, c, labelColor, labelWeightX, valueWeightX);
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
