package submission;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static gui.Globals.*;

//TODO: should this be a Dialog that block other input?
public class LoginWindow {
    private JPanel contentPane;
    private JTextField serverTF;
    private JTextField portTF;
    private JTextField emailTF;
    private JTextField passwordTF;

    public LoginWindow() {
        contentPane = new JPanel();
        serverTF = new JTextField();
        portTF = new JTextField();
        emailTF = new JTextField();
        passwordTF = new JTextField();

        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // Create headerLabel
        JLabel headerLabel = new JLabel("AFCT Server - Login");
        changeSize(headerLabel, 20);

        // Add headerLabel to contentPane
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.CENTER);
        c.insets = new Insets(10, 10, 10, 10);
        contentPane.add(headerLabel, c);

        createTextInputPanel(serverTF, "Server");
        createTextInputPanel(portTF, "Port");
        createTextInputPanel(emailTF, "Email");
        createTextInputPanel(passwordTF, "Password");

    }

    private JPanel createTextInputPanel(JTextField textField, String headerText) {
        JPanel textInputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // Create headerLabel
        JLabel headerLabel = new JLabel(headerText);
        // Add headerLabel to textInputPanel
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        setAllInsets(c, 10);
        textInputPanel.add(headerLabel, c);

        // Add textField to textInputPanel
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        setAllInsets(c, 10);
        c.fill = GridBagConstraints.HORIZONTAL;
        textInputPanel.add(textField, c);

        return textInputPanel;
    }

    public JPanel getContentPane() {
        return contentPane;
    }
}
