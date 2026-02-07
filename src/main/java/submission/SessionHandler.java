package submission;


import gui.Globals;
import gui.environment.Environment;
import gui.popups.UpdatePopup;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import static gui.Globals.*;
import static gui.Globals.colorHTMLErrorMessage;
import static submission.AFCTClient.fixUrl;
import static submission.LoginResult.*;
import static submission.SubmitWindow.ComboBoxTarget.*;


public class SessionHandler {
    public final Preferences preferences;
    private final DateFormat dateFormat;
    private int expireAfterDays = 7;

    private Instant startTime = Instant.MIN;

    private AFCTClient client = null;
    private String token = null;
    private Map<String, Map<String, Object>> courses;
    private List<Map<String, Object>> courseListCache = null;
    /** true means all, false means upcoming */
    private Map<String, Map<Boolean, List<Map<String, Object>>>> courseToAssignmentMap;
    /** true means all, false means uncompleted */
    private Map<String, Map<Boolean, List<Map<String, Object>>>> assignmentToProblemMap;

    private String server = null;
    private String port = null;
    private String email = null;
    private String password = null;

    public boolean loggedIn = false;

    // Submit windows
    private ArrayList<SubmitWindow> submitWindows;

    // Login GUI elements
    private final LoginWindow loginWindow;

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
    public static final String defaultPort = "3001";
    public static final String defaultEmail = "student@example.com";
    public static final String defaultPassword = "";

    public SessionHandler() {
        this.preferences = Preferences.userNodeForPackage(SessionHandler.class);
        this.dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
        this.submitWindows = new ArrayList<>();
        this.courses = new HashMap<>();
        this.courseToAssignmentMap = new HashMap<>();
        this.assignmentToProblemMap = new HashMap<>();

        // Login GUI elements
        this.loginWindow = new LoginWindow(this);
    }

    public SubmitWindow createNewSubmitWindow(Environment environment) {
        SubmitWindow submitWindow = new SubmitWindow(environment);
        submitWindows.add(submitWindow);
        return submitWindow;
    }

    public void displayLoginThenSubmission(SubmitWindow submitWindowToShow) {
        // Try to auto login asynchronously
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                // Try to automatically log in (to avoid showing login GUI)
                boolean successful = autoReAuthenticate();

                // If unsuccessful, display login window to user
                if (!successful) {
                    loginWindow.displayLoginThenSubmission(sessionHandler, submitWindowToShow);
                } else {
                    submitWindowToShow.displaySubmitWindow();
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
            }

            @Override
            protected void done() {
                //System.out.println("Done");
            }
        }.execute();
    }

    public void updateStartTime() {
        startTime = Instant.now();
    }

    public void clearStartTime() {
        startTime = Instant.MIN;
    }

    public AFCTClient getClient() {
        // TODO: check if authenticated:
        //      if not: open login window
        //      otherwise: check if session is still active
        //              if so: return client
        //              if not: try to reauth with saved creds
        //                      if this fails: open login window

        // Check if 15 minutes have passed
        Instant currentTime = Instant.now();
        Duration duration = Duration.between(startTime, currentTime);
        long minutesPassed = duration.toMinutes();

        boolean needToReAuth = this.client == null || !this.client.isAuthenticated() || minutesPassed >= 14;

        if (needToReAuth) {
            // Re-login - try to do automatically
            boolean successful = autoReAuthenticate();

            // If unsuccessful, display login window to user
            if (!successful) {
                loginWindow.displayLoginWindow(this);
            }
        }

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
                this.loggedIn = true;
                this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "yes");

                // Set creds to expire after 7 days
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, expireAfterDays);
                preferences.put(PREF_SAVED_CREDS_EXPIRE_AFTER, dateFormat.format(calendar.getTime()));
                return getSuccessResult();
            } else {
                // Login failed
                this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
                this.loggedIn = false;
                return getFailureResult();
            }
        } catch (IOException ex) {
            // Connection failed
            this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
            this.loggedIn = false;
            return getErrorResult(ex.getMessage());
        }
    }

    public void logout() {
        this.preferences.put(PREF_HAS_USED_SAVED_CREDS, "no");
        this.loggedIn = false;
        this.client = null;

        // TODO: maybe track which submitWindows are visible, and re open them when the user logs back in?
        for (SubmitWindow submitWindow : submitWindows) {
            submitWindow.setVisible(false);
        }
    }

    /**
     * Saved credentials will not be used to auto re-authenticate if they have not been used in the last 7 days.
     *
     * @return
     */
    private boolean autoReAuthenticate(boolean forceManualReLogin) {
        //TODO - add an option to prefs - ask login every time AFCT is opened

        boolean usedCreds = !this.preferences.get(PREF_HAS_USED_SAVED_CREDS, "no").equals("no");
        if (!usedCreds) {
            return false;
        }

        if (forceManualReLogin) {
            return false;
        }

        String expireAfter = preferences.get(PREF_SAVED_CREDS_EXPIRE_AFTER, null);
        if (expireAfter != null) {
            String strCurrent = dateFormat.format(new Date());
            // if the current date is before the saved date, return true
            try {
                Date current = dateFormat.parse(strCurrent);
                Date saved = dateFormat.parse(expireAfter);
                if (current.before(saved)) {
                    // Try to Re-login here
                    String serverUrl = preferences.get(PREF_SERVER, defaultServer);
                    String portText = preferences.get(PREF_PORT, defaultPort);
                    String userEmail = preferences.get(PREF_EMAIL, defaultEmail);
                    String userPassword = preferences.get(PREF_PASSWORD, defaultPassword);
                    LoginResult loginResult = login(serverUrl, portText, userEmail, userPassword);
                    if (loginResult.status == LoginResult.LoginStatus.SUCCESS) {
                        return true;
                    } else {
                        loginWindow.appendResult(colorHTMLErrorMessage(loginResult.message));
                    }
                }
            } catch (ParseException ignored) { }
        }

        return false;
    }

    private boolean autoReAuthenticate() {
        return autoReAuthenticate(false);
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

    public void disableAndResetAllSubmitWindows() {
        for (SubmitWindow submitWindow : submitWindows) {
            submitWindow.disableAndResetAllComboBoxes();
        }
    }

    private void loadCoursesAsync() {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                for (SubmitWindow submitWindow : submitWindows) {
                    submitWindow.toggleCourseBox(false);
                }
                AFCTClient client = getClient();
                if (client != null) {
                    try {
                        // Load courses on worker thread
                        List<Map<String, Object>> courseList = client.getCourses(email);
                        courseListCache = courseList;

                        for (Map<String, Object> course : courseList) {
                            courses.put(course.get("id").toString(), course);
                        }

                        // Clear assignment and problem caches
                        courseToAssignmentMap.clear();
                        assignmentToProblemMap.clear();

                        //TODO: replace with generic method - updateComboBoxesWithoutChangingSelection()
                        // Update course combobox for all SubmitWindows, without changing which course is selected
                        for (SubmitWindow submitWindow : submitWindows) {
                            CourseItem selectedCourse = (CourseItem) submitWindow.courseBox.getSelectedItem();
                            if (submitWindow.courseBox.getSelectedIndex() > 0 && selectedCourse != null) {
                                submitWindow.isPopulating = true;
                                submitWindow.courseBox.setModel(createCourseModelFromList(courseList));
                                for (int i = 0; i < submitWindow.courseBox.getItemCount(); i++) {
                                    if (submitWindow.courseBox.getItemAt(i).id.equals(selectedCourse.id)) {
                                        submitWindow.courseBox.setSelectedIndex(i);
                                        submitWindow.isPopulating = false;
                                        break;
                                    }
                                }
                            } else {
                                submitWindow.courseBox.setModel(createCourseModelFromList(courseList));
                                // If the user is in only one course, select it automatically
                                if (courseList.size() == 1) {
                                    submitWindow.courseBox.setSelectedIndex(1);
                                }
                            }
                            // Re-enable CourseBox
                            submitWindow.toggleCourseBox(true);
                        }

                        // Display number of courses loaded
                        int numCourses = courseList.size();
                        //publish(String.format("Loaded %s %s", numCourses, numCourses == 1 ? "course" : "courses"));
                    } catch (IOException ex) {
                        // TODO: determine why this will sometimes be shown when first opening submit window during a session
                        publish(colorHTMLErrorMessage("Error loading courses: " + ex.getMessage()));
                        // Re-enable CourseBox refresh buttons
                        for (SubmitWindow submitWindow : submitWindows) {
                            submitWindow.toggleCourseRefreshButton(true);
                        }
                    }
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    for (SubmitWindow submitWindow : submitWindows) {
                        submitWindow.appendResult(s);
                    }
                }
            }

            @Override
            protected void done() {
                //signInButton.setEnabled(true);
            }
        }.execute();
    }

    public void populateCourses(SubmitWindow submitWindow, boolean forceReload) {
        // Disable and reset CourseBox
        submitWindow.disableAndResetTargetComboBox(COURSE);
        // Disable and reset AssignmentBox
        submitWindow.disableAndResetTargetComboBox(ASSIGNMENT);
        // Disable and reset ProblemBox
        submitWindow.disableAndResetTargetComboBox(PROBLEM);


        if (courseListCache != null && !forceReload) {
            submitWindow.courseBox.setModel(createCourseModelFromList(courseListCache));
            // If the user is in only one course, select it automatically
            if (courseListCache.size() == 1) {
                submitWindow.courseBox.setSelectedIndex(1);
            }
            // Re-enable CourseBox
            submitWindow.toggleCourseBox(true);
        } else {
            loadCoursesAsync();
        }
    }

    public void populateCourses(SubmitWindow submitWindow) {
        populateCourses(submitWindow, false);
    }

    private void loadAssignmentsAsync(CourseItem selectedCourse) {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    assert selectedCourse != null;

                    AFCTClient client = getClient();
                    if (client == null) {
                        return null;
                    }

                    String courseId;
                    String dueDateStr;
                    LocalDateTime currTime;
                    boolean isUpcoming;

                    courseId = selectedCourse.id;

                    // Load assignments on worker thread
                    List<Map<String, Object>> assignmentList = client.getAssignments(courseId);

                    // Get current time for default boxes
                    currTime = LocalDateTime.now();

                    // Generate list of Upcoming Assignments
                    List<Map<String, Object>> upcomingAssignments = new ArrayList<>();

                    // Get assignments based on default parameters
                    for (Map<String, Object> assignment : assignmentList) {
                        // Remove cached problems for this assignment
                        assignmentToProblemMap.remove(assignment.get("id").toString());

                        // Get the date this assignment is due
                        dueDateStr = assignment.get("dueDate").toString();
                        assert dueDateStr != null;

                        // Parse the date correctly
                        dueDateStr = dueDateStr.charAt(dueDateStr.length() - 1) == 'Z' ? dueDateStr.substring(0, dueDateStr.length() - 1) : dueDateStr;

                        // Find if the assignment is upcoming
                        isUpcoming = LocalDateTime.parse(dueDateStr).isAfter(currTime);

                        // Add to upcomingAssignments if applicable
                        if (isUpcoming) {
                            upcomingAssignments.add(assignment);
                        }
                    }

                    // Cache Assignments
                    Map<Boolean, List<Map<String, Object>>> modelMap = new HashMap<>();
                    modelMap.put(true, assignmentList);
                    modelMap.put(false, upcomingAssignments);
                    // Save to assignmentMap
                    courseToAssignmentMap.put(selectedCourse.id, modelMap);

                    // Add model to drop-down menu
                    for (SubmitWindow submitWindow : submitWindows) {
                        CourseItem selectedItem = (CourseItem) submitWindow.courseBox.getSelectedItem();
                        if (selectedItem != null && Objects.equals(selectedItem.id, selectedCourse.id)) {
                            if (submitWindow.allAssignments.isSelected()) {
                                updateComboBoxWithoutChangingSelection(submitWindow, ASSIGNMENT, createAssignmentModelFromList(assignmentList));
                            } else {
                                updateComboBoxWithoutChangingSelection(submitWindow, ASSIGNMENT, createAssignmentModelFromList(upcomingAssignments));
                            }
                            // Re-enable AssignmentBox
                            submitWindow.toggleAssignmentBox(true);
                        }
                    }

                    // Display number of assignments loaded
                    int numTotalAssignments = assignmentList.size();
                    //publish(String.format("Loaded %s %s", numTotalAssignments, numTotalAssignments == 1 ? "assignment" : "assignments"));
                } catch (IOException ex) {
                    publish("Failed to load assignments: " + ex.getMessage());
                    // TODO: handle this case - if necessary
                    //setModel(assignmentBox, List.of(SubmitWindow.PLACEHOLDER), true);
                }
                //publish("");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    for (SubmitWindow submitWindow : submitWindows) {
                        submitWindow.appendResult(s);
                    }
                }
            }

            @Override
            protected void done() {
//                updateSelectFileEnabled();
//                updateSubmitEnabled();
            }
        }.execute();
    }

    public void populateAssignments(SubmitWindow submitWindow, CourseItem selectedCourse, boolean forceReload) {
        // Disable and reset AssignmentBox
        submitWindow.disableAndResetTargetComboBox(ASSIGNMENT);
        // Disable and reset ProblemBox
        submitWindow.disableAndResetTargetComboBox(PROBLEM);

        if (courseToAssignmentMap.containsKey(selectedCourse.id) && !forceReload) {
            if (submitWindow.allAssignments.isSelected()) {
                submitWindow.assignmentBox.setModel(createAssignmentModelFromList(courseToAssignmentMap.get(selectedCourse.id).get(true)));
            } else {
                submitWindow.assignmentBox.setModel(createAssignmentModelFromList(courseToAssignmentMap.get(selectedCourse.id).get(false)));
            }

            handleComboBoxAutoSelect(submitWindow.assignmentBox);

            // Re-enable AssignmentBox
            submitWindow.toggleAssignmentBox(true);
        } else {
            loadAssignmentsAsync(selectedCourse);
        }
    }

    public void populateAssignments(SubmitWindow submitWindow, CourseItem selectedCourse) {
        populateAssignments(submitWindow, selectedCourse, false);
    }

    private void loadProblemsAsync(AssignmentItem selectedAssignment) {
        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                try {
                    assert selectedAssignment != null;

                    AFCTClient client = getClient();
                    if (client == null) {
                        return null;
                    }

                    // Load problems on worker thread
                    List<Map<String, Object>> problemsList = client.getProblems(selectedAssignment.id);

                    // Generate list of Uncompleted Problems
                    List<Map<String, Object>> uncompletedProblems = new ArrayList<>();

                    // Get problems based on default parameters
                    for (Map<String, Object> problem : problemsList) {
                        Boolean isSolved = (Boolean) problem.get("solved");

                        // Add to uncompletedProblems if applicable
                        if (!isSolved) {
                            uncompletedProblems.add(problem);
                        }
                    }

                    // Cache Problems
                    Map<Boolean, List<Map<String, Object>>> modelMap = new HashMap<>();
                    modelMap.put(true, problemsList);
                    modelMap.put(false, uncompletedProblems);
                    // Save to assignmentMap
                    assignmentToProblemMap.put(selectedAssignment.id, modelMap);

                    // Add model to drop-down menu
                    for (SubmitWindow submitWindow : submitWindows) {
                        AssignmentItem selectedItem = (AssignmentItem) submitWindow.assignmentBox.getSelectedItem();
                        if (selectedItem != null && Objects.equals(selectedItem.id, selectedAssignment.id)) {
                            if (submitWindow.allProblems.isSelected()) {
                                updateComboBoxWithoutChangingSelection(submitWindow, PROBLEM, createProblemModelFromList(problemsList));
                            } else {
                                updateComboBoxWithoutChangingSelection(submitWindow, PROBLEM, createProblemModelFromList(uncompletedProblems));
                            }
                            // Re-enable ProblemBox
                            submitWindow.toggleProblemBox(true);

                            // Disable submit button if the submission succeeded and the problem is no longer in the
                            // combobox due to the "Uncompleted Problems" option being selected
                            if (submitWindow.problemBox.getSelectedIndex() <= 0) {
                                submitWindow.toggleSubmitButton(false);
                            }
                        }
                    }

                    // Display number of problems loaded
                    int numTotalProblems = problemsList.size();
                    //publish(String.format("Loaded %s %s", numTotalProblems, numTotalProblems == 1 ? "problem" : "problems"));
                } catch (IOException ex) {
                    publish(colorHTMLErrorMessage("Failed to load problems: " + ex.getMessage()));
                    // TODO: handle this case - if necessary
                    //setModel(problemBox, List.of(PLACEHOLDER), true);
                }
                //publish("");
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    for (SubmitWindow submitWindow : submitWindows) {
                        submitWindow.appendResult(s);
                    }
                }
            }

            @Override
            protected void done() {
//                updateSelectFileEnabled();
//                updateSubmitEnabled();
            }
        }.execute();
    }

    public void populateProblems(SubmitWindow submitWindow, AssignmentItem selectedAssignment, boolean forceReload, boolean avoidComboBoxReset) {
        // Disable and reset ProblemBox
        if (avoidComboBoxReset) {
            submitWindow.toggleTargetComboBox(PROBLEM, false);
        } else {
            submitWindow.disableAndResetTargetComboBox(PROBLEM);
        }

        if (assignmentToProblemMap.containsKey(selectedAssignment.id) && !forceReload) {
            if (submitWindow.allProblems.isSelected()) {
                submitWindow.problemBox.setModel(createProblemModelFromList(assignmentToProblemMap.get(selectedAssignment.id).get(true)));
            } else {
                submitWindow.problemBox.setModel(createProblemModelFromList(assignmentToProblemMap.get(selectedAssignment.id).get(false)));
            }

            handleComboBoxAutoSelect(submitWindow.problemBox);

            // Re-enable ProblemBox
            submitWindow.toggleProblemBox(true);
        } else {
            loadProblemsAsync(selectedAssignment);
        }
    }

    public void populateProblems(SubmitWindow submitWindow, AssignmentItem selectedAssignment, boolean forceReload) {
        populateProblems(submitWindow, selectedAssignment, forceReload, false);
    }

    public void populateProblems(SubmitWindow submitWindow, AssignmentItem selectedAssignment) {
        populateProblems(submitWindow, selectedAssignment, false);
    }

    private <T> void updateComboBoxWithoutChangingSelection(SubmitWindow submitWindow, SubmitWindow.ComboBoxTarget target, ComboBoxModel<T> model) {
        JComboBox<DropdownItem> comboBox = submitWindow.getTargetComboBox(target);
        // Disable ComboBox
        submitWindow.toggleTargetComboBox(target, false);

        DropdownItem selectedItem = (DropdownItem) comboBox.getSelectedItem();
        if (comboBox.getSelectedIndex() > 0 && selectedItem != null) {
            // Item selected, try to update without changing selection
            submitWindow.isPopulating = true;
            comboBox.setModel((ComboBoxModel<DropdownItem>) model);
            for (int i = 0; i < comboBox.getItemCount(); i++) {
                if (comboBox.getItemAt(i).id.equals(selectedItem.id)) {
                    comboBox.setSelectedIndex(i);
                    submitWindow.isPopulating = false;
                    break;
                }
            }
        } else {
            // No item selected, just update model like normal
            comboBox.setModel((ComboBoxModel<DropdownItem>) model);
        }

        // Slight issue, this only runs when data is reloaded from the network
        // I am adding class to his in other places to account for this
        handleComboBoxAutoSelect(comboBox);

        // Re-enable ComboBox
        submitWindow.toggleTargetComboBox(target, true);
    }

    /**
     * If there is only one (non-placeholder) item in the ComboBox, select it automatically.
     *
     * @param comboBox the combobox to autoselect an item in
     * @param <T> DropdownItem (CourseItem, AssignmentItem, or ProblemItem)
     */
    private <T> void handleComboBoxAutoSelect(JComboBox<T> comboBox) {
        // If there is only one (non-placeholder) item in the ComboBox, select it automatically

        // Check for comboBox.getSelectedIndex() <= 0 so that this is only triggered if the user has nothing selected
        // Check for comboBox.getItemCount() == 2, because we expect the ComboBox to have its first element always be a
        //      PLACEHOLDER element (like "— Select —", so it is obvious to the user that nothing is selected)
        if (comboBox.getSelectedIndex() <= 0 && comboBox.getItemCount() == 2) {
            comboBox.setSelectedIndex(1);
        }
    }


    /**
     * Update target combobox for all SubmitWindows, without changing which item is selected
     * (CourseItem, AssignmentItem, or ProblemItem)
     *
     * @param target the type of combobox to update
     * @param model the model to set
     */
    private void updateComboBoxesWithoutChangingSelection(SubmitWindow.ComboBoxTarget target, ComboBoxModel<DropdownItem> model) {
        for (SubmitWindow submitWindow : submitWindows) {
            updateComboBoxWithoutChangingSelection(submitWindow, target, model);
        }
    }

    private DefaultComboBoxModel<CourseItem> createCourseModelFromList(List<Map<String, Object>> courseList) {
        // Generate model
        DefaultComboBoxModel<CourseItem> model = new DefaultComboBoxModel<>();
        model.addElement(new CourseItem("", SubmitWindow.PLACEHOLDER));

        for (Map<String, Object> course : courseList) {
            model.addElement(new CourseItem(course.get("id").toString(), course.get("name").toString()));
        }

        return model;
    }

    private DefaultComboBoxModel<AssignmentItem> createAssignmentModelFromList(List<Map<String, Object>> assignmentList) {
        // Generate model
        DefaultComboBoxModel<AssignmentItem> model = new DefaultComboBoxModel<>();
        model.addElement(new AssignmentItem("",  SubmitWindow.PLACEHOLDER));
        for (Map<String, Object> assignment : assignmentList) {
            model.addElement((new AssignmentItem(assignment.get("id").toString(), assignment.get("title").toString(), assignment.get("description").toString())));
        }
        return model;
    }

    private DefaultComboBoxModel<ProblemItem> createProblemModelFromList(List<Map<String, Object>> problemList) {
        // Generate model
        DefaultComboBoxModel<ProblemItem> model = new DefaultComboBoxModel<>();
        model.addElement(new ProblemItem("",  SubmitWindow.PLACEHOLDER));
        for (Map<String, Object> problem : problemList) {
            Boolean isSolved = (Boolean) problem.get("solved");
            String instTitle = String.format("%s %s", problem.get("title"), isSolved ? "\u2714" : " ");
            model.addElement(new ProblemItem(problem.get("id").toString(), instTitle, problem.get("description").toString()));
        }
        return model;
    }
}
