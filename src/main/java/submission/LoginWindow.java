package submission;

import gui.Globals;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import static gui.Globals.*;
import static submission.AFCTClient.fixUrl;
import static submission.SessionHandler.*;
import static submission.SessionHandler.PREF_PASSWORD;

//TODO: should this be a Dialog that block other input?
public class LoginWindow extends JFrame {
    private JPanel contentPane;
    private JTextField serverTF;
    private JTextField portTF;
    private JTextField emailTF;
    private JPasswordField passwordTF;
    private JButton loginButton;
    // TODO: replace this with better, more modern user feedback methods
    private JTextPane result;
    private String resultText = "";
    private JScrollPane resultScrollPane;


    private JScrollPane scrollPane;

    private SubmitWindow submitWindowToShow = null;

    public LoginWindow(SessionHandler sessionHandler) {
        contentPane = new JPanel();
        serverTF = new JTextField();
        portTF = new JTextField();
        emailTF = new JTextField();
        passwordTF = new JPasswordField();
        loginButton = new JButton("Login");

        result = new JTextPane();
        resultScrollPane = new JScrollPane(result);

        setupGui();
        populateGui(sessionHandler);
        setupEventHandlers();

        scrollPane = new JScrollPane(contentPane);

        this.getContentPane().add(scrollPane);
    }

    public void displayLoginWindow(SessionHandler sessionHandler) {
        this.toggleAllInputs(true);
        if (!this.isVisible()) {
            this.populateGui(sessionHandler);
        }
        this.pack();
        this.setVisible(true);
        this.toFront();
    }

    public void displayLoginThenSubmission(SessionHandler sessionHandler, SubmitWindow submitWindowToShow) {
        displayLoginWindow(sessionHandler);
        this.submitWindowToShow = submitWindowToShow;
    }

    private void appendResult(String line) {
        resultText += (line.endsWith("\n") ? line : (line + "\n"));
        result.setText(resultText);
        result.setCaretPosition(result.getDocument().getLength());
    }

    private void setupGui() {
        this.setTitle("Login - " + Globals.APP_NAME);

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
        //  - but this should only work/be available if the user typed their password during this session.
        //passwordTF.setMargin(new Insets(0, 12, 0, 40));

        // Add loginButton to contentPane
        changeSize(loginButton, 16);
        loginButton.setPreferredSize(new Dimension(360, 36));
        loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        //loginButton.setMargin(new Insets(6, 12, 6, 12));
        c = setConstraints(1, 0, 0, y++, GridBagConstraints.LINE_START);
        //c.insets = new Insets(5, hozInset, vrtInset, hozInset);
        c.insets = new Insets(10, hozInset, vrtInset, hozInset);
        contentPane.add(loginButton, c);

        // Add result to contentPane
        result.setBorder(new LineBorder(new Color(210, 210, 210)));
        c.gridy = y++;
        // TODO: probably remove this before pushing to students?
        contentPane.add(resultScrollPane, c);
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

    public static <T> JPanel createComboBoxPanel(JComboBox<T> comboBox, String headerText) {
        comboBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return createInputPanel(comboBox, headerText, false);
    }

    private void populateGui(SessionHandler sessionHandler) {
        Preferences prefs = sessionHandler.preferences;
        serverTF.setText(prefs.get(PREF_SERVER, defaultServer));
        portTF.setText(prefs.get(PREF_PORT, defaultPort));
        emailTF.setText(prefs.get(PREF_EMAIL, defaultEmail));
        passwordTF.setText(prefs.get(PREF_PASSWORD, defaultPassword));
    }

    /**
     * Sets action listeners for user inputs.
     */
    private void setupEventHandlers() {
        handlers_windowClose();
        handlers_login();
    }

    private void openQueuedSubmitWindow() {
        if (submitWindowToShow != null && sessionHandler.loggedIn) {
            submitWindowToShow.displaySubmitWindow();
            submitWindowToShow = null;
        }
    }

    private void handlers_windowClose() {
        LoginWindow frame = this;
        WindowAdapter windowListener = new WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveLoginInfo();
                frame.dispose();
                openQueuedSubmitWindow();
            }
        };

        // add windowListener
        frame.addWindowListener(windowListener);
    }

    private void saveLoginInfo() {
        final String serverUrl = serverTF.getText();
        final String portText = portTF.getText();
        final String userEmail = emailTF.getText();
        final String userPassword = new String(passwordTF.getPassword());
        Globals.sessionHandler.saveLoginInfo(serverUrl, portText, userEmail, userPassword);
    }

    private void toggleAllInputs(boolean enable) {
        serverTF.setEnabled(enable);
        portTF.setEnabled(enable);
        emailTF.setEnabled(enable);
        passwordTF.setEnabled(enable);
        loginButton.setEnabled(enable);
    }

    private void handlers_login() {
        loginButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                // SwingWorker
                final String serverUrl = fixUrl(serverTF.getText());
                final String portText = portTF.getText().trim();
                final String userEmail = emailTF.getText().trim();
                final String userPassword = new String(passwordTF.getPassword());

                toggleAllInputs(false);

                Globals.sessionHandler.disableAndResetAllSubmitWindows();

                appendResult("Authenticating…");

                //savePreferences(serverUrl, portText, userEmail, userPassword);

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() {
                        LoginResult loginResult = Globals.sessionHandler.login(serverUrl, portText, userEmail, userPassword);
                        if (loginResult.status == LoginResult.LoginStatus.SUCCESS) {
                            publish(colorHTMLSuccessMessage(loginResult.message));
                        } else {
                            publish(colorHTMLErrorMessage(loginResult.message));
                        }

                        if (loginResult.status == LoginResult.LoginStatus.SUCCESS) {
                            // Define the time delay in milliseconds (5000ms = 5 seconds)
                            int delay = 2000;

                            // TODO - should delay be kept?
                            delay = 500;
                            // Create and start the Swing Timer
                            Timer timer = new Timer(delay, new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    // This code runs after the delay
                                    dispose();
                                    openQueuedSubmitWindow();
                                }
                            });
                            timer.setRepeats(false); // Ensure the timer only runs once
                            timer.start();
                        }

                        return null;
                    }

                    @Override
                    protected void process(List<String> chunks) {
                        for (String s : chunks) appendResult(s);
                    }

                    @Override
                    protected void done() {
                        toggleAllInputs(true);
                    }
                }.execute();
            }
        });
    }
}
