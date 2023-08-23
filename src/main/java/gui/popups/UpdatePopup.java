package gui.popups;

import gui.Updater;
import gui.components.LinkLabel;
import gui.Globals;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.http.HttpResponse;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;

import static gui.Globals.*;
import static gui.Updater.*;
import static javax.swing.BorderFactory.createEmptyBorder;

public class UpdatePopup implements ExtensionPopup {
    private boolean popupShown = false;
    private final JFrame frame;
    private DateFormat dateFormat;
    private JLabel version;
    private String latestVersion;
    private LinkLabel releaseLink;
    private final JPanel cards;
    private static final String LOADING = "LOADING";
    private static final String UPDATE_AVAILABLE_PANEL = "UPDATE_AVAILABLE_PANEL";
    private static final String NO_UPDATES_PANEL = "NO_UPDATES_PANEL";

    enum UpdateStatus {
        LOADING, AVAILABLE, NO_UPDATES, REMIND_LATER
    }

    public UpdatePopup() {
        popups.add(this);

        // create frame
        frame = new JFrame();

        cards = new JPanel(new CardLayout());
        frame.getContentPane().add(cards);
        frame.setTitle(Globals.APP_NAME);

        JPanel loadingPanel = setupLoadingPanel();
        cards.add(LOADING, loadingPanel);

        JPanel updatePanel = setupUpdateAvailablePanel();
        cards.add(UPDATE_AVAILABLE_PANEL, updatePanel);

        JPanel noUpdatePanel = setupNoUpdatePanel();
        cards.add(NO_UPDATES_PANEL, noUpdatePanel);
    }

    private JPanel setupLoadingPanel() {
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.PAGE_AXIS));
        contentPane.setBorder(createEmptyBorder(10,10,10,10));

        JLabel versionLabel = new JLabel("Checking for updates.");
        versionLabel.setBorder(createEmptyBorder(0,0,10,0));
        contentPane.add(versionLabel);

        return contentPane;
    }

    private JPanel setupUpdateAvailablePanel() {
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints c;
        int y = 0;

        // create updateAvailableLabel
        JLabel updateAvailableLabel = new JLabel("An update for AFCT is available.");
        changeSize(updateAvailableLabel, 20);

        // add updateAvailableLabel to contentPane
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.FIRST_LINE_START);
        c.insets = new Insets(10, 10, 10, 10);
        contentPane.add(updateAvailableLabel, c);


        // create topPanel
        JPanel topPanel = new JPanel(new GridBagLayout());
        int topY = 0;

        // add versionTextLabel
        JLabel versionTextLabel = new JLabel("Version:");
        c = setConstraints(0, 0, 0, topY, GridBagConstraints.FIRST_LINE_START);
        c.insets = new Insets(10, 0, 10, 10);
        topPanel.add(versionTextLabel, c);

        // add version
        version = new JLabel();
        c = setConstraints(0.5, 0, 1, topY++, GridBagConstraints.FIRST_LINE_START);
        topPanel.add(version, c);

        // add releaseTextLabel
        JLabel releaseTextLabel = new JLabel("Release:");
        c = setConstraints(0, 0, 0, topY, GridBagConstraints.FIRST_LINE_START);
        c.insets = new Insets(10, 0, 10, 10);
        topPanel.add(releaseTextLabel, c);

        // add releaseLink
        releaseLink = new LinkLabel("", "");
        c = setConstraints(0.5, 0, 1, topY++, GridBagConstraints.FIRST_LINE_START);
        topPanel.add(releaseLink,c);

        // add topPanel to contentPane
        c = setConstraints(0.5, 0, 0, y++, GridBagConstraints.FIRST_LINE_START);
        c.insets = new Insets(5, 15, 0, 15);
        contentPane.add(topPanel, c);

        // add blank panel to "eat" vertical space
        c = setConstraints(1, 1, 0, y++);
        contentPane.add(new JPanel(), c);

        // create remindLaterPanel
        JPanel remindLaterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // add remindLabel
        JLabel remindLabel = new JLabel("Remind me:");
        remindLaterPanel.add(remindLabel);

        // add remindDropdown
        JComboBox<String> remindDropdown = new JComboBox<>();
        String blank = "";
        String tomorrow = "Tomorrow";
        String nextWeek = "Next week";
        String nextUpdate = "Next update";
        for (String element : Arrays.asList(blank, tomorrow, nextWeek, nextUpdate)) {
            remindDropdown.addItem(element);
        }
        remindLaterPanel.add(remindDropdown);

        // add remindLaterPanel to contentPane
        c = setConstraints(0, 0, 0, y++, GridBagConstraints.LINE_START);
        c.insets = new Insets(10,10,0,10);
        contentPane.add(remindLaterPanel, c);

        ExtensionPopup popup = this;
        WindowAdapter windowListener = new WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                Object item = remindDropdown.getSelectedItem();
                if (item == null || ((String) item).isBlank()) {
                    popupShown = false;
                    frame.dispose();
                    return;
                }

                String selected = (String) item;
                if (!selected.equals(nextUpdate)) {
                    int days;
                    if (selected.equals(nextWeek)) {
                        days = 7;
                    } else {
                        days = 1;
                    }
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DAY_OF_MONTH, days);
                    preferences.put(UPDATE, dateFormat.format(calendar.getTime()));
                } else {
                    preferences.put(UPDATE, latestVersion);
                }

                popupShown = false;
                frame.dispose();
            }
        };

        // add windowListener
        frame.addWindowListener(windowListener);


        // create buttonPanel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));


        // add closeButton
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(evt -> SwingUtilities.invokeLater(() -> windowListener.windowClosing(null)));
        buttonPanel.add(closeButton);

        // add updateButton
        JButton updateButton = new JButton("Update");
        // TODO: pick color - makeButtonOrange(updateButton);
        updateButton.addActionListener(evt -> {
            SwingUtilities.invokeLater(() -> {
                Runnable runnable = () -> {
                    Result result = new Result(Status.ERROR);

                    while (result.status == Status.ERROR) {
                        try {
                            result = updater.downloadApp(frame, latestVersion);
                        } catch (IOException e) {
                            String message;
                            if (e instanceof NoRouteToHostException) {
                                message = "Error encountered: " + e.getMessage();
                            } else {
                                message = "Error encountered while trying to access: " + e.getMessage();
                            }
                            result = new Result(Status.ERROR, message);
                            errorPrint(message);
                        }
                        if (result.status == Status.ERROR) {
                            JPanel panel = new JPanel();
                            panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

                            JLabel errorMessage = new JLabel(result.message);
                            errorMessage.setToolTipText(result.toolTip);
                            panel.add(errorMessage);
                            panel.add(new JLabel("Try downloading the update again?"));

                            Object[] options = {"Retry", "Close"};
                            int returnVal = JOptionPane.showOptionDialog(frame, panel, "Download Failed",
                                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                                    null, options, options[1]);
                            if (returnVal == JOptionPane.NO_OPTION) {
                                result.status = null;
                            }
                        }
                    }

                    if (result.status == Status.GOOD) {
                        popupShown = false;
                        frame.dispose();
                        preferences.remove(UPDATE);

                        // restart to update AFCT
                        String title = "AFCT update downloaded.";
                        // TODO REWORD
                        String message = "To use the new update, unload this app, then load the updated version.";


                        print(title);
                        print(message);
                        JOptionPane.showMessageDialog(getActiveWindow(),
                                message,
                                title,
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                };

                Thread thread = new Thread(runnable);
                thread.start();
            });
        });
        buttonPanel.add(updateButton);


        // add buttonPanel to contentPane
        c = setConstraints(0, 0, 0, y++,  GridBagConstraints.LINE_END);
        c.insets = new Insets(10,10,5,10);
        contentPane.add(buttonPanel, c);

        return contentPane;
    }

    private JPanel setupNoUpdatePanel() {
        JPanel contentPane = new JPanel();

        JLabel upToDateLabel = new JLabel("AFCT is up to date.");

        JLabel versionLabel = new JLabel("You are already using the latest version of AFCT.");
        boldFontAndChangeSize(versionLabel, 20);
        versionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        contentPane.add(versionLabel);

        return contentPane;
    }

    private UpdateStatus checkAppVersion() {
        HttpResponse<String> response = updater.getUrl(APP_URL + LATEST_RELEASE_PATH);
        JsonObject json = stringToJson(response.body());
        latestVersion = json.get("tag_name").getAsString();

        Version comp = compareVersions(currentVersion, latestVersion);
        // if currentVersion is newer than latestVersion, or they are the same, return
        if (comp == Version.NEWER || comp == Version.SAME) {
            return show(UpdateStatus.NO_UPDATES);
        }

        version.setText(JUST_NAME + " " + latestVersion);
        String url = json.get("html_url").getAsString();
        releaseLink.update(url, url);

        Matcher m;
        dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
        String updateAfter = preferences.get(UPDATE, null);
        if (updateAfter != null) {
            m = versionRegex.matcher(updateAfter);
            if (m.find()) {
                // if updateAfter is newer than latestVersion, or they are the same, return
                // otherwise, show the popup
                Version saved = compareVersions(updateAfter, latestVersion);
                if (saved == Version.NEWER || saved == Version.SAME) {
                    return show(UpdateStatus.REMIND_LATER);
                }
            } else {
                String strCurrent = dateFormat.format(new Date());
                // if the current date is before the saved date, return
                try {
                    Date current = dateFormat.parse(strCurrent);
                    Date saved = dateFormat.parse(updateAfter);
                    if (current.before(saved)) {
                        return show(UpdateStatus.REMIND_LATER);
                    }
                } catch (ParseException ignored) { }
            }
        }

        return show(UpdateStatus.AVAILABLE);
    }

    private UpdateStatus show(UpdateStatus updateAvailable) {
        CardLayout cl = (CardLayout)(cards.getLayout());

        switch (updateAvailable) {
            case AVAILABLE, REMIND_LATER -> cl.show(cards, UPDATE_AVAILABLE_PANEL);
            case NO_UPDATES ->  cl.show(cards, NO_UPDATES_PANEL);
            case LOADING ->  cl.show(cards, LOADING);
        }

        return updateAvailable;
    }

    public void showPopup() {
        if (popupShown) {
            if (!frame.isActive()) {
                frame.setState(JFrame.ICONIFIED);
                frame.setState(JFrame.NORMAL);
            }
            return;
        }
        popupShown = true;
        show(UpdateStatus.LOADING);

        // pack the frame
        frame.pack();

        sizeAndCenterWindow(frame);

        // display the popup
        frame.setVisible(true);

        checkAppVersion();
    }

    public void showOnLoad() {
        if (checkAppVersion() == UpdateStatus.AVAILABLE) {
            popupShown = true;

            // pack the frame
            frame.pack();

            sizeAndCenterWindow(frame);

            // display the popup
            frame.setVisible(true);
        }
    }

    @Override
    public void closePopup() {
        popupShown = false;
        frame.dispose();
    }
}
