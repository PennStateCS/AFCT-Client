package submission;


import gui.Globals;
import gui.popups.UpdatePopup;

import javax.swing.*;
import java.awt.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.prefs.Preferences;

import static gui.Globals.sizeAndCenterWindow;


public class SessionHandler {
    public final Preferences preferences;
    private final DateFormat dateFormat;
    private int expireAfterDays = 7;

    private AFCTClient client = null;
    private String server = null;
    private String port = null;
    private String email = null;
    private String password = null;

    // GUI elements
    private final JFrame frame;
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

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);

        // GUI elements
        this.frame = new JFrame();
        this.cards = new JPanel(new CardLayout());
        this.loginWindow = new LoginWindow();
        this.submitWindow = new SubmitWindow();
        this.setupGUI();

        // TODO: remove after testing
        CardLayout cl = (CardLayout)(cards.getLayout());
        cl.show(cards, LOGIN);
        frame.pack();
        frame.setVisible(true);
    }

    private void setupGUI() {
        frame.getContentPane().add(cards);

        cards.add(LOGIN, loginWindow.getContentPane());

        cards.add(SUBMIT, submitWindow.getContentPane());
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

    private void setBaseUrl(String baseUrl) {
        // Set this.baseUrl if this.baseUrl is null (has not been set before), or baseUrl != this.baseUrl
        if (this.server == null || !Objects.equals(baseUrl, this.server)) {
            this.server = baseUrl;
            this.client = new AFCTClient(this.server);
            this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
            this.preferences.remove(PREF_SAVED_CREDS_EXPIRE_AFTER);
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

        if (!Objects.equals(this.email, email) || !Objects.equals(this.password, password)) {
            this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
        }

        // If login succeeds:
        this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "yes");
        // Set creds to expire after 7 days
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, expireAfterDays);
        preferences.put(PREF_SAVED_CREDS_EXPIRE_AFTER, dateFormat.format(calendar.getTime()));
    }

    private void tryLogin() {
        tryLogin(this.email, this.password);
    }
}
