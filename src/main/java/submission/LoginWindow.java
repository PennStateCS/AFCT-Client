package submission;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.util.Calendar;

import static gui.Globals.*;

//TODO: should this be a Dialog that block other input?
public class LoginWindow {
    private JPanel contentPane;
    private JTextField serverTF;
    private JTextField portTF;
    private JTextField emailTF;
    private JPasswordField passwordTF;

    public LoginWindow() {
        contentPane = new JPanel();
        serverTF = new JTextField();
        portTF = new JTextField();
        emailTF = new JTextField();
        passwordTF = new JPasswordField();

        setupGui();
        // TODO: when the login window closes, save creds to perfs

    }

    public JPanel getContentPane() {
        return contentPane;
    }


    private void setupGui() {
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        int vrtInset = 15;
        int hozInset = 20;

        // Create headerLabel
        JLabel headerLabel = new JLabel("AFCT Server - Login");
        changeSize(headerLabel, 24);

        // Add headerLabel to contentPane
        c = setConstraints(1, 1, 0, y++, GridBagConstraints.NORTH);
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(vrtInset, hozInset, vrtInset, hozInset);
        contentPane.add(headerLabel, c);

        // Add text input fields
        c.insets = new Insets(vrtInset, hozInset, 0, hozInset);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = y++;
        contentPane.add(createTextInputPanel(serverTF, "Server"), c);
        c.gridy = y++;
        contentPane.add(createTextInputPanel(portTF, "Port"), c);
        c.gridy = y++;
        contentPane.add(createTextInputPanel(emailTF, "Email"), c);
        c.insets = new Insets(vrtInset, hozInset, vrtInset, hozInset);
        c.gridy = y++;
        contentPane.add(createTextInputPanel(passwordTF, "Password"), c);
        //TODO: maybe add button to allow showing the password instead of just the dots.
        //passwordTF.setMargin(new Insets(0, 12, 0, 40));

        // Create loginButton
        JButton loginButton = new JButton("Login");
        changeSize(loginButton, 16);
        loginButton.setPreferredSize(new Dimension(360, 36));
        // Add loginButton to contentPane
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        //c.insets = new Insets(5, hozInset, vrtInset, hozInset);
        c.insets = new Insets(10, hozInset, vrtInset, hozInset);
        contentPane.add(loginButton, c);
    }

    private JPanel createTextInputPanel(JTextField textField, String headerText) {
        JPanel textInputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // Create headerLabel
        JLabel headerLabel = new JLabel(headerText);
        changeSize(headerLabel, 16);
        // Add headerLabel to textInputPanel
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        c.insets = new Insets(0, 0, 5, 0);
        textInputPanel.add(headerLabel, c);

        // Add textField to textInputPanel
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        c.fill = GridBagConstraints.HORIZONTAL;
        textField.setPreferredSize(new Dimension(360, 36));
        textField.setMargin(new Insets(0, 12, 0, 12));
        changeSize(textField, 16);
        textInputPanel.add(textField, c);

        return textInputPanel;
    }
}
