package submission;


import gui.Globals;
import gui.popups.UpdatePopup;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;

import static gui.Globals.sizeAndCenterWindow;
import static submission.AFCTClient.fixUrl;
import static submission.LoginResult.*;


public class SessionHandler {
    public final Preferences preferences;
    private final DateFormat dateFormat;
    private int expireAfterDays = 7;

    private AFCTClient client = null;
    private String token = null;
    private List<Map<String, Object>> courses;
    private List<Map<String, Object>> assignments;
    private List<Map<String, Object>> problems;


    private String server = null;
    private String port = null;
    private String email = null;
    private String password = null;

    // GUI elements
    private final JFrame frame;
    private final JFrame loginFrame;
    private final JFrame submitFrame;
    private final JPanel cards;
    private final LoginWindow loginWindow;
    private final SubmitWindow submitWindow;

    // GUI identifiers
    private static final String LOGIN = "LOGIN";
    private static final String SUBMIT = "SUBMIT";

    // Preferences
    public static final String PREF_HAS_USED_SAVED_CREDS = "has_used_saved_creds";
    public static final String PREF_SAVED_CREDS_EXPIRE_AFTER = "saved_creds_expire_after";
    public static final String PREF_SERVER = "server";
    public static final String PREF_PORT = "port";
    public static final String PREF_EMAIL = "email";
    public static final String PREF_PASSWORD = "password";
    public static final String PREF_HOMEWORK = "homework";
    public static final String PREF_PROBLEM = "problem";

    // Default values
    public static final String defaultServer = "http://localhost";
    public static final String defaultPort = "3000";
    public static final String defaultEmail = "student@example.com";
    public static final String defaultPassword = "";

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);

        // GUI elements
        this.frame = new JFrame();
        this.loginFrame = new JFrame();
        this.submitFrame = new JFrame();
        this.cards = new JPanel(new CardLayout());
        this.loginWindow = new LoginWindow(frame, this);
        this.submitWindow = new SubmitWindow(this.frame);
        this.setupGUI();

        // TODO: remove after testing
        CardLayout cl = (CardLayout)(cards.getLayout());
        cl.show(cards, LOGIN);
        //frame.pack();
        //frame.setVisible(true);
    }

    private void setupGUI() {
        frame.getContentPane().add(cards);

        //cards.add(LOGIN, loginWindow.getContentPane());

        //cards.add(SUBMIT, submitWindow.getContentPane());

        loginFrame.getContentPane().add(loginWindow.getContentPane());
        loginFrame.pack();
        loginFrame.setVisible(true);

        submitFrame.getContentPane().add(submitWindow.getContentPane());
        submitFrame.pack();
        submitFrame.setVisible(true);
    }

    private void show() {
        frame.setTitle("Login - " + Globals.APP_NAME);
    }

    public AFCTClient getClient() {
        // TODO: check if authenticated:
        //      if not: open login window
        //      otherwise: check if session is still active
        //              if so: return client
        //              if not: try to reauth with saved creds
        //                      if this fails: open login window

        return this.client;
    }
    
    public LoginResult login(String serverUrl, String portText, String userEmail, String userPassword) {
        serverUrl = fixUrl(serverUrl);
        portText = portText.trim();
        userEmail = userEmail.trim();
        saveLoginInfo(serverUrl, portText, userEmail, userPassword);

        try {
            client = new AFCTClient(serverUrl + ":" + portText);
            token = client.login(userEmail, userPassword);
            if (token != null && !token.isBlank()) {
                // Login succeeded
                this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "yes");
                // Set creds to expire after 7 days
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, expireAfterDays);
                preferences.put(PREF_SAVED_CREDS_EXPIRE_AFTER, dateFormat.format(calendar.getTime()));
                loadCoursesAsync();
                return getSuccessResult();
            } else {
                return getFailureResult();
            }
        } catch (IOException ex) {
            return getErrorResult(ex.getMessage());
        }
    }
    

    /**
     *
     * Saved credentials will not be used to auto re-authenticate if they have not been used in the last 7 days.
     *
     * @return
     */
    private boolean autoReAuthenticate() {
        boolean usedCreds = !this.preferences.get(PREF_HAS_USED_SAVED_CREDS, "no").equals("no");
        if (!usedCreds) {
            return false;
        }

        String expireAfter = preferences.get(PREF_SAVED_CREDS_EXPIRE_AFTER, null);
        if (expireAfter == null) {
            return false;
        } else {
            String strCurrent = dateFormat.format(new Date());
            // if the current date is before the saved date, return true
            try {
                Date current = dateFormat.parse(strCurrent);
                Date saved = dateFormat.parse(expireAfter);
                if (current.before(saved)) {
                    // TODO: re-login here
                    //TODO return show(UpdatePopup.UpdateStatus.REMIND_LATER);
                }
            } catch (ParseException ignored) { }
        }

        return false;
    }

    private void tryLogin(String email, String password) {
        //TODO boolean credCHanged = ;

//        if (!Objects.equals(this.email, email) || !Objects.equals(this.password, password)) {
//            this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
//        }

        // If login succeeds:
        this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "yes");
        // Set creds to expire after 7 days
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, expireAfterDays);
        preferences.put(PREF_SAVED_CREDS_EXPIRE_AFTER, dateFormat.format(calendar.getTime()));
    }

    private void tryLogin() {
        //tryLogin(this.email, this.password);
    }

    public void saveLoginInfo(String serverUrl, String portText, String userEmail, String userPassword) {
        serverUrl = fixUrl(serverUrl);
        portText = portText.trim();
        userEmail = userEmail.trim();
        
        String savedServer = preferences.get(PREF_SERVER, defaultServer);
        String savedPort = preferences.get(PREF_PORT, defaultPort);
        String savedEmail = preferences.get(PREF_EMAIL, defaultEmail);
        String savedPassword = preferences.get(PREF_PASSWORD, defaultPassword);
        
        preferences.put(PREF_SERVER, serverUrl);
        preferences.put(PREF_PORT, portText);
        preferences.put(PREF_EMAIL, userEmail);
        preferences.put(PREF_PASSWORD, userPassword);

        this.email = userEmail;
        
        boolean serverChanged = !Objects.equals(savedServer, serverUrl);
        boolean portChanged = !Objects.equals(savedPort, portText);
        boolean emailChanged = !Objects.equals(savedEmail, userEmail);
        boolean passwordChanged = !Objects.equals(savedPassword, userPassword);
        if (serverChanged || portChanged || emailChanged || passwordChanged) {
            this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
            this.preferences.remove(PREF_SAVED_CREDS_EXPIRE_AFTER);
        }
    }

    private void loadCoursesAsync() {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                submitWindow.toggleCourseBox(false);
                client = getClient();
                if (client != null) {
                    try {
                        // Load courses on worker thread
                        courses = client.getCourses(email);
                        // Generate model
                        DefaultComboBoxModel<CourseItem> model = new DefaultComboBoxModel<>();
                        model.addElement(new CourseItem("", SubmitWindow.PLACEHOLDER));

                        for (Map<String, Object> course : courses) {
                            model.addElement(new CourseItem(course.get("id").toString(), course.get("name").toString()));
                        }

                        submitWindow.courseBox.setModel(model);
                        submitWindow.toggleCourseBox(true);

                        // Display number of courses loaded
                        int numCourses = courses.size();
                        publish(String.format("Loaded %s %s", numCourses, numCourses == 1 ? "course" : "courses"));

                        // If there is only one course, select it and load the assignments for that course
                        if (numCourses == 1) {
                            submitWindow.courseBox.setSelectedIndex(1);
                        }
                    } catch (IOException ex) {
                        publish("Error loading courses: " + ex.getMessage());
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) submitWindow.appendResult(s);
            }

            @Override
            protected void done() {
                //signInButton.setEnabled(true);
            }
        }.execute();
    }
}
