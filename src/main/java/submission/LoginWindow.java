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
    private JButton loginButton;

    public LoginWindow() {
        contentPane = new JPanel();
        serverTF = new JTextField();
        portTF = new JTextField();
        emailTF = new JTextField();
        passwordTF = new JPasswordField();
        loginButton = new JButton("Login");

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

        // Add loginButton to contentPane
        changeSize(loginButton, 16);
        loginButton.setPreferredSize(new Dimension(360, 36));
        //loginButton.setMargin(new Insets(6, 12, 6, 12));
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        //c.insets = new Insets(5, hozInset, vrtInset, hozInset);
        c.insets = new Insets(10, hozInset, vrtInset, hozInset);
        contentPane.add(loginButton, c);
    }

    public static JPanel createInputPanel(Component component, String headerText, boolean setMargin) {
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // Create headerLabel
        JLabel headerLabel = new JLabel(headerText);
        changeSize(headerLabel, 16);
        // Add headerLabel to inputPanel
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        c.insets = new Insets(0, 0, 5, 0);
        inputPanel.add(headerLabel, c);

        // Add component to inputPanel
        c = setConstraints(1, 1, 0, y++, GridBagConstraints.LINE_START);
        //c.fill = GridBagConstraints.HORIZONTAL;
        changeSize(component, 16);
        //component.setPreferredSize(new Dimension(360, 36)); // Bad way of setting this - breaks vertical centering
        if (setMargin) {
            ((JTextField) component).setMargin(new Insets(6, 12, 6, 12));
        }
        inputPanel.add(component, c);

        return inputPanel;
    }

    public static JPanel createTextInputPanel(JTextField textField, String headerText) {
        return createInputPanel(textField, headerText, true);
    }

    public static JPanel createComboBoxPanel(JComboBox comboBox, String headerText) {
        return createInputPanel(comboBox, headerText, false);
    }
}
