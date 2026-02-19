package gui;

import automata.Automaton;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import gui.popups.ExtensionPopup;
import submission.SessionHandler;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

import static gui.editor.IconKeeper.getRefreshIcon;
import static gui.viewer.StateDrawer.HIGHLIGHT_COLOR;
import static java.lang.Math.abs;
import static java.lang.Math.floor;

/**
 * A class containing global values, subclasses, and static methods.
 *
 * @author Jesse Burdick-Pless
 */
public class Globals {
    public static String testingPath = "src\\main\\resources";
    public final static String currentVersion = "v" + Globals.class.getPackage().getImplementationVersion();
    public static final String JUST_NAME = "AFCT ";
    public final static String APP_NAME = JUST_NAME + currentVersion;
    public static final String APP_URL = "https://www.cs.rit.edu/~afct";
    public static final String LATEST_RELEASE_PATH = "/client/";
    public static String JAR_PATH = "TODO";
    public static String JAR_NAME = "afct-client.jar";
    public static String AFCT_DATA_FOLDER_NAME = "AFCT-Data";
    public static String PREFERENCES_FILE_NAME = "AFCT-Preferences.xml";
    public static String FILE_CREATED_WITH_STRING = "Created with AFCT " + currentVersion + " (based on JFLAP " + gui.AboutBox.VERSION + ")";

    private static final String htmlProperty = "html.disable";
    public final static String UPDATE = "UPDATE";

    public static final Color FROM_COLOR = new Color(37,99,235);
    //public static Color TO_COLOR = new Color(84,132,234);
    //public static final Color BOTH_COLOR = new Color(84,132,234);
    //public static final Color BOTH_COLOR = new Color(71,154,188);
    //public static final Color BOTH_COLOR = new Color(54,128,211);
    //public static final Color BOTH_COLOR = new Color(68,135,134);
    //public static final Color BOTH_COLOR = new Color(108,69,247);
    public static final Color BOTH_COLOR = new Color(116, 49, 255);
    //public static final Color BOTH_COLOR = new Color(84,116,229);
    public static final Color TEST_COLOR = new Color(84,255,132);
    //public static Color TO_COLOR = new Color(178,255,255);
    //public static final Color TO_COLOR = new Color(153,255,255);
    //public static final Color TO_COLOR = new Color(127,255,255);
    //public static final Color TO_COLOR = new Color(69,129,142);
    //public static final Color TO_COLOR = new Color(32,178,204);
    public static final Color TO_COLOR = HIGHLIGHT_COLOR;
    public static final Color NEITHER_COLOR = new Color(216,216,216);

    private static int positioningFudgeFactor = 20;

    public final static Preferences preferences = Preferences.userNodeForPackage(Globals.class);
    public static ArrayList<ExtensionPopup> popups = new ArrayList<>();

    public static Updater updater = new Updater();

    public static String lastCopiedString = null;
    public static Automaton lastCopiedAutomaton = null;

    public static SessionHandler sessionHandler = new SessionHandler();

    public enum Status {
        ERROR, WARNING, GOOD
    }

    public static class Result {
        public Status status;
        public String message;
        public String toolTip;

        public Result(Status status) {
            this.status = status;
            this.message = null;
            this.toolTip = null;
        }

        public Result(Status status, String message) {
            this.status = status;
            this.message = message;
            this.toolTip = null;
        }

        public Result(Status status, String message, String toolTip) {
            this.status = status;
            this.message = message;
            this.toolTip = toolTip;
        }
    }

    public static JsonObject stringToJson(String jsonString) throws JsonSyntaxException {
        JsonElement element = JsonParser.parseString(jsonString);
        return element.getAsJsonObject();
    }

    public static ImageIcon getImageIcon(String path) {
        ImageIcon image = new ImageIcon(testingPath + path);
        if (image.getImageLoadStatus() != MediaTracker.COMPLETE) {
            image = new ImageIcon(Objects.requireNonNull(Globals.class.getResource(path)));
        }
        return image;
    }

    public static GridBagConstraints setConstraints(double weightx, double weighty, int gridx, int gridy) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = weightx;
        c.weighty = weighty;
        c.gridx = gridx;
        c.gridy = gridy;
        return c;
    }

    public static GridBagConstraints setConstraints(double weightx, double weighty, int gridx, int gridy, int anchor) {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = weightx;
        c.weighty = weighty;
        c.gridx = gridx;
        c.gridy = gridy;
        c.anchor = anchor;
        return c;
    }

    public static void setAllInsets(GridBagConstraints constraints, int insetAmount) {
        constraints.insets = new Insets(insetAmount, insetAmount, insetAmount, insetAmount);
    }

    public static void allowHTMLInComponent(JTextArea textComponent) {
        textComponent.putClientProperty(htmlProperty, null);
        textComponent.addPropertyChangeListener(evt -> {
            if (htmlProperty.equals(evt.getPropertyName())) {
                textComponent.putClientProperty(htmlProperty, null);
            }
        });
    }

    public static void allowHTMLInComponent(JLabel textComponent) {
        textComponent.putClientProperty(htmlProperty, null);
        textComponent.addPropertyChangeListener(evt -> {
            if (htmlProperty.equals(evt.getPropertyName())) {
                textComponent.putClientProperty(htmlProperty, null);
            }
        });
    }

    public static Window getActiveWindow() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    public static Dimension getSize(JFrame frame) {
        return getSize(frame, null);
    }

    public static Dimension getSize(JFrame frame, Double scaleFactor) {
        Rectangle frameBounds = frame.getBounds();

        // determine which screen the popup will appear on
        Dimension maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getSize();
        int width = maxBounds.width;
        int height = maxBounds.height;
        boolean skipWidth = false;
        boolean skipHeight = false;
        Rectangle screenBounds;

        // get the bounds of each screen the device has
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screenBounds = device.getDefaultConfiguration().getBounds();

            // LEFT EDGE: check if the left edge of the parent window is on this screen
            if ((frameBounds.x >= screenBounds.x) && (frameBounds.x <= screenBounds.x + screenBounds.width)) {
                width = screenBounds.width;
                skipWidth = true;
            }

            // TOP EDGE: check if the top edge of the parent window is on this screen
            if ((frameBounds.y >= screenBounds.y) && (frameBounds.y <= screenBounds.y + screenBounds.height)) {
                height = screenBounds.height;
                skipHeight = true;
            }

            if (skipWidth && skipHeight) {
                break;
            }
            if (!skipWidth) {
                width = Integer.min(screenBounds.width, width);
            }
            if (!skipHeight) {
                height = Integer.min(screenBounds.height, height);
            }
        }

        if (scaleFactor == null) {
            scaleFactor = 0.93;
        }

        if (scaleFactor != 1) {
            width = Integer.min(frame.getWidth(), (int) floor(scaleFactor * width));
            height = Integer.min(frame.getHeight(), (int) floor(scaleFactor * height));
        }
        return new Dimension(width, height);
    }

    public static void setComponentSize(Component toChange, int width, int height) {
        Dimension size = new Dimension(width, height);
        setComponentSize(toChange, size);
    }

    public static void setComponentSize(Component toChange, Dimension size) {
        toChange.setMaximumSize(size);
        toChange.setPreferredSize(size);
        toChange.setSize(size);
        toChange.setMinimumSize(size);
    }

    public static void sizeAndCenterWindow(JFrame frame) {
        sizeAndCenterWindow(frame, null, true);
    }

    public static void sizeAndCenterWindow(JFrame frame, Double scaleFactor) {
        sizeAndCenterWindow(frame, scaleFactor, true);
    }

    private static void sizeAndCenterWindow(JFrame frame, Double scaleFactor, boolean center) {
        Window window = getActiveWindow();
        if (window == null) {
            return;
        }

        // center the popup such that it only appears on one screen
        if (center) {
            frame.setLocationRelativeTo(window);
        }

        // set popup bounds
        Dimension size = getSize(frame, scaleFactor);
        setComponentSize(frame, size);

        // center the popup
        if (center) {
            frame.setLocationRelativeTo(window);
        }
    }

    public static void guaranteedPositionFrameOnWindow(JFrame frame, JFrame window, boolean selectScreenByArea) {
        if (window.isShowing()) {
            frame.setLocationRelativeTo(window);
            return;
        }

        // Determine which screen the frame should appear on
        Rectangle screenBounds;
        if (selectScreenByArea) {
            screenBounds = getScreenBoundsForWindowByArea(window);
        } else {
            screenBounds = getScreenBounds(window);
        }
        if (screenBounds == null) {
            frame.setLocationRelativeTo(window);
            return;
        }

        Rectangle frameBounds = frame.getBounds();

        Dimension windowSize = window.getSize();
        //Point windowLocation1 = window.getLocationOnScreen();
        Point windowLocation = window.getLocation();
        int dx = windowLocation.x + ((windowSize.width - frameBounds.width) / 2);
        int dy = windowLocation.y + ((windowSize.height - frameBounds.height) / 2);

        // Avoid being placed off the edge of the screen
        WindowFit targetFit = fitToScreen(dx, dy, frameBounds, screenBounds);
        frame.setLocation(targetFit.x, targetFit.y);
    }

    public static void guaranteedPositionFrameOnWindow(JFrame frame, JFrame window) {
        guaranteedPositionFrameOnWindow(frame, window, true);
    }

    public enum Position {
        TOP_LEFT, TOP, TOP_RIGHT,
        LEFT, CENTER, RIGHT,
        BOT_LEFT, BOT, BOT_RIGHT,
    }

    private static int positionNewX(int newX, Position targetPosition, Rectangle frameBounds, Rectangle screenBounds) {
        Position tPos = targetPosition;
        if (tPos == Position.TOP_LEFT || tPos == Position.LEFT || tPos == Position.BOT_LEFT) {
            newX -= frameBounds.width;
        } else if (tPos == Position.TOP_RIGHT || tPos == Position.RIGHT || tPos == Position.BOT_RIGHT) {
            newX += frameBounds.width;
        }

        int xMod;
        if (newX > screenBounds.width) {
            xMod = newX % screenBounds.width;
        } else if (newX < -screenBounds.width) {
            xMod = -((-newX) % screenBounds.width);
        } else {
            xMod = newX;
        }

        if (xMod + frameBounds.width > screenBounds.width) {
            // Check if frame runs partly off the right side of the screen

            int temp = newX - xMod;
            // newX = xMod = 3
            // screenBounds.width = 5
            // xMod + frameBounds.width = 6
            // frameBounds.width = 3
            // newX = newX + (screenBounds.width - (xMod + frameBounds.width))
            // newX = 3 + (5 - (3 + 3))
            // newX = 3 + (5 - 6)
            // newX = 3 -1
            // newX = 2

            newX = newX + (screenBounds.width - (xMod + frameBounds.width));
        } else if (xMod < screenBounds.width) {
            // Check if frame runs partly off the left side of the screen

            int temp = newX - xMod;
            // newX = xMod = -1
            // screenBounds.width = 5
            // xMod + frameBounds.width = 6
            // frameBounds.width = 3
            // newX = newX + (screenBounds.width - (xMod + frameBounds.width))
            // newX = 3 + (5 - (3 + 3))
            // newX = 3 + (5 - 6)
            // newX = 3 -1
            // newX = 2

            newX = newX + (screenBounds.width - (xMod + frameBounds.width));
        }

        return newX;
    }

    public static Position getOppositePosition(Position position) {
        return switch (position) {
            case TOP_LEFT -> Position.BOT_RIGHT;
            case TOP -> Position.BOT;
            case TOP_RIGHT -> Position.BOT_LEFT;
            case LEFT -> Position.RIGHT;
            case CENTER -> Position.CENTER;
            case RIGHT -> Position.LEFT;
            case BOT_LEFT -> Position.TOP_RIGHT;
            case BOT -> Position.TOP;
            case BOT_RIGHT -> Position.TOP_LEFT;
            default -> Position.CENTER;
        };
    }

    private static class WindowFit {
        public int x;
        public int y;

        public int xDiff;
        public int yDiff;

        public boolean forceToTarget = false;

        public WindowFit(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public WindowFit(int x, int y, int dx, int dy) {
            this.x = dx;
            this.y = dy;
            this.xDiff = dx - x;
            this.yDiff = dy - y;
        }

        public WindowFit(WindowFit oldFit, int x, int y) {
            this.x = x;
            this.y = y;
            this.xDiff = x - oldFit.x;
            this.yDiff = y - oldFit.y;
        }

        public boolean isSmallXChange() {
            return abs(xDiff) <= positioningFudgeFactor;
        }

        public boolean isSmallYChange() {
            return abs(yDiff) <= positioningFudgeFactor;
        }

        public boolean isGoodFit() {
            return isSmallXChange() && isSmallYChange();
        }
    }

    private static WindowFit getBestFit(List<WindowFit> fits) {
        WindowFit bestFit = null;
        double minDiag = Double.MAX_VALUE;

        double diag;
        for (WindowFit fit : fits) {
            diag = Math.sqrt((fit.xDiff * fit.xDiff) + (fit.yDiff * fit.yDiff));
            if (diag < minDiag) {
                minDiag = diag;
                bestFit = fit;
            }
        }
        return bestFit;
    }

    private static WindowFit fitToScreen(int dx, int dy, Rectangle frameBounds, Rectangle screenBounds) {
        // Avoid being placed off the edge of the screen:
        int x = dx;
        int y = dy;

        // bottom
        if (dy + frameBounds.height > screenBounds.y + screenBounds.height) {
            dy = screenBounds.y + screenBounds.height - frameBounds.height;
        }
        // top
        if (dy < screenBounds.y) {
            dy = screenBounds.y;
        }
        // right
        if (dx + frameBounds.width > screenBounds.x + screenBounds.width) {
            dx = screenBounds.x + screenBounds.width - frameBounds.width;
        }
        // left
        if (dx < screenBounds.x) {
            dx = screenBounds.x;
        }
        return new WindowFit(x, y, dx, dy);
    }

    private static WindowFit fitToScreen(WindowFit windowFit, Rectangle frameBounds, Rectangle screenBounds) {
        return fitToScreen(windowFit.x, windowFit.y, frameBounds, screenBounds);
    }

    private static WindowFit positionFrame(JFrame window, JFrame frame, Position targetPosition) {
        Rectangle windowBounds = window.getBounds();
        Rectangle frameBounds = frame.getBounds();

        int dx = window.getX();
        int dy = window.getY();

        Position tPos = targetPosition;
        if (tPos == Position.TOP_LEFT || tPos == Position.TOP || tPos == Position.TOP_RIGHT) {
            dy -= frameBounds.height;
        } else if (tPos == Position.BOT_LEFT || tPos == Position.BOT || tPos == Position.BOT_RIGHT) {
            dy += windowBounds.height;
        }

        if (tPos == Position.TOP_LEFT || tPos == Position.LEFT || tPos == Position.BOT_LEFT) {
            dx -= frameBounds.width;
        } else if (tPos == Position.TOP_RIGHT || tPos == Position.RIGHT || tPos == Position.BOT_RIGHT) {
            dx += windowBounds.width;
        }

        return new WindowFit(dx, dy);
    }

    private static WindowFit tryFitPosition(JFrame frame, Position targetPosition, JFrame window, Rectangle screenBounds, boolean forceToTarget) {
        Rectangle frameBounds = frame.getBounds();

        // Try to position frame at targetPosition
        WindowFit dPoint = positionFrame(window, frame, targetPosition);

        // Avoid being placed off the edge of the screen:
        dPoint = fitToScreen(dPoint, frameBounds, screenBounds);
        if (forceToTarget || dPoint.isGoodFit()) {
            dPoint.forceToTarget = true;
            //frame.setLocation(dPoint.x, dPoint.y);
            return dPoint;
        }
        return dPoint;
    }

    private static WindowFit tryFitPosition(JFrame frame, Position targetPosition, JFrame window, Rectangle screenBounds) {
        return tryFitPosition(frame, targetPosition, window, screenBounds, false);
    }

    private static Rectangle getScreenBounds(JFrame window) {
        // determine which screen the frame should appear on
        boolean validX = false;
        boolean validY = false;
        Rectangle screenBounds = null;
        Rectangle windowBounds = window.getBounds();

        // Get the bounds of each screen the device has
        // Determine which screen the window is on
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screenBounds = device.getDefaultConfiguration().getBounds();
            // Correct for HiDPI / OS display scaling
            DisplayMode dm = device.getDisplayMode();
            int screenWidth = dm.getWidth();
            int screenHeight = dm.getHeight();
            //screenBounds.width = screenWidth;
            //screenBounds.height = screenHeight;

            // LEFT EDGE: check if the left edge of the window is on this screen
            int test = screenBounds.x + screenWidth;
            if ((windowBounds.x >= screenBounds.x) && (windowBounds.x <= (screenBounds.x + screenWidth))) {
                validX = true;
            }

            // TOP EDGE: check if the top edge of the  window is on this screen
            if ((windowBounds.y >= screenBounds.y) && (windowBounds.y <= (screenBounds.y + screenHeight))) {
                validY = true;
            }

            if (validX && validY) {
                break;
            }
        }

        return screenBounds;
    }

    private static Rectangle getScreenBoundsForWindowByArea(JFrame window) {
        // determine which screen the frame should appear on
        Rectangle screenBounds;
        Rectangle windowBounds = window.getBounds();

        Rectangle firstScreenBounds = null;
        Rectangle bestScreenBounds = null;
        int maxArea = 0;

        // Get the bounds of each screen the device has
        // Determine which screen the window is on
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screenBounds = device.getDefaultConfiguration().getBounds();

            if (firstScreenBounds == null) {
                firstScreenBounds = screenBounds;
            }

            // Correct for HiDPI / OS display scaling
            DisplayMode dm = device.getDisplayMode();
            int screenWidth = dm.getWidth();
            int screenHeight = dm.getHeight();

            // Get window width on this screen
            int left;
            int right;
            int top;
            int bottom;

            // LEFT EDGE: check if the left edge of the window is on this screen
            if ((windowBounds.x >= screenBounds.x) && (windowBounds.x <= (screenBounds.x + screenWidth))) {
                // set left = the x coordinate of the left edge of the window
                left = windowBounds.x;
            } else {
                // if left edge of the window is NOT on this screen:
                // set left = the x coordinate of the left edge of the screen
                left = screenBounds.x;
            }

            // Check if ANY of the window is on this screen horizontally
            if (windowBounds.x > screenBounds.x + screenWidth) {
                // If not, continue to next screen to check
                continue;
            }

            // set right to the minimum of the right edge of the window and the right edge of the screen
            right = Math.min((windowBounds.x + windowBounds.width), (screenBounds.x + screenWidth));


            // TOP EDGE: check if the top edge of the  window is on this screen
            if ((windowBounds.y >= screenBounds.y) && (windowBounds.y <= (screenBounds.y + screenHeight))) {
                top = windowBounds.y;
            } else {
                top = screenBounds.y;
            }

            // Check if ANY of the window is on this screen vertically
            if (windowBounds.y > screenBounds.y + screenHeight) {
                // If not, continue to next screen to check
                continue;
            }

            // set bottom to the minimum of the bottom edge of the window and the bottom edge of the screen
            bottom = Math.min((windowBounds.y + windowBounds.height), (screenBounds.y + screenHeight));

            int width = (right - left);
            int height = (bottom - top);
            int area = width * height;

            if (area > maxArea) {
                maxArea = area;
                bestScreenBounds = screenBounds;
            }
        }

        // Handle the window not being on any screen
        // TODO: could be improved by using the screen bounds for the screen the window is closest to
        if (bestScreenBounds == null) {
            bestScreenBounds = firstScreenBounds;
        }

        return bestScreenBounds;
    }

    public static void positionFrameNearWindow(JFrame frame, Position targetPosition, JFrame window, boolean forceToTarget) {
        if (targetPosition == Position.CENTER) {
            frame.setLocationRelativeTo(window);
        }

        // Determine which screen the frame should appear on
        Rectangle screenBounds = getScreenBounds(window);
        if (screenBounds == null) {
            frame.setLocationRelativeTo(window);
            return;
        }

        // Try to position frame at targetPosition
        WindowFit targetFit = tryFitPosition(frame, targetPosition, window, screenBounds, forceToTarget);
        WindowFit backupFit;
        WindowFit bestFit = targetFit;

        if (forceToTarget || targetFit.isGoodFit()) {
            frame.setLocation(targetFit.x, targetFit.y);
            return;
        } else {
            // TODO: include more backup fits, especially ones that are more intelligently chosen
            backupFit = tryFitPosition(frame, getOppositePosition(targetPosition), window, screenBounds);
            if (backupFit.isGoodFit()) {
                bestFit = backupFit;
            } else {
                ArrayList<WindowFit> bestFits = new ArrayList<>();
                bestFits.add(targetFit);
                bestFits.add(backupFit);
                bestFit = getBestFit(bestFits);
            }
        }

        frame.setLocation(bestFit.x, bestFit.y);
    }

    public static void positionFrameNearWindow(JFrame frame, Position targetPosition, JFrame window) {
        positionFrameNearWindow(frame, targetPosition, window, false);
    }

    public static void changeSize(Component component, float fontSize) {
        component.setFont(component.getFont().deriveFont(fontSize));
    }

    public static void changeFontSize(Component component, float fontSize) {
        changeSize(component, fontSize);
    }

    public static void unBoldFont(Component component) {
        component.setFont(component.getFont().deriveFont(Font.PLAIN));
    }

    public static void boldFont(Component component) {
        component.setFont(component.getFont().deriveFont(Font.BOLD));
    }

    public static void boldFontAndChangeSize(Component component, int fontSize) {
        boldFont(component);
        changeSize(component, fontSize);
    }

    public static void italicFont(Component component) {
        component.setFont(component.getFont().deriveFont(Font.ITALIC));
    }

    public static Icon styleRefreshButton(JButton refreshButton) {
        Icon icon = getRefreshIcon();
        refreshButton.setIcon(icon);
        setPointerCursor(refreshButton);

        // Optional: remove borders for a cleaner look
        //refreshButton.setBorderPainted(false);
        //refreshButton.setContentAreaFilled(false);
        return icon;
    }

    public static void setPointerCursor(Component component) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void setDefaultCursor(Component component) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    public static String colorHTMLMessage(String message, String color) {
        return "<span style=\"color: " + color + ";\">" + message + "</span>";
    }

    public static String colorSuccessFailMessage(String message, boolean success) {
        String result;
        if (success) {
            result = colorHTMLMessage(message, "#00b050");
        } else {
            result = colorHTMLMessage(message, "red");
        }
        return result;
    }

    public static String colorHTMLSuccessMessage(String message) {
        return colorSuccessFailMessage(message, true);
    }

    public static String colorHTMLErrorMessage(String message) {
        return colorSuccessFailMessage(message, false);
    }

    public static String colorHTMLWarningMessage(String message) {
        //return colorMessage(message, "#ffd500");
        //return colorMessage(message, "#f1c232");
        return colorHTMLMessage(message, "#e69138");
    }

    public static void errorPrint(String output) {
        System.err.println(output);
    }

    public static void print(String output) {
        System.out.println(output);
    }

    public enum OSType {
        WINDOWS, MAC, LINUX, UNKNOWN
    }

    public static OSType determineOSType() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) {
            return OSType.WINDOWS;
        } else if (osName.startsWith("mac os")) {
            return OSType.MAC;
        } else if (osName.startsWith("linux") || osName.startsWith("unix")) {
            return OSType.LINUX;
        } else {
            return OSType.UNKNOWN;
        }
    }

    public static String OLD_getAFCTDataFolderPath() {
        OSType osType = determineOSType();
        String sep = FileSystems.getDefault().getSeparator();
        String dataFolderPath;
        //TODO: test if the paths for Mac and Linux actually work
        switch (osType) {
            case WINDOWS:
                String homeDir = System.getProperty("user.home");
                dataFolderPath = homeDir + sep + "AppData" + sep + "Local" + sep + AFCT_DATA_FOLDER_NAME;
                break;
            case MAC:
                dataFolderPath = "~" + sep + "Library" + sep + "Application Support" + sep + AFCT_DATA_FOLDER_NAME;
                break;
            case LINUX:
                dataFolderPath = "$HOME" + sep + ".config" + sep + AFCT_DATA_FOLDER_NAME;
                break;
            default:
                String workingDIr = System.getProperty("user.dir");
                dataFolderPath = workingDIr + sep + AFCT_DATA_FOLDER_NAME;
                break;
        }

        // TODO: handle being unable to create folder
        File folder = new File(dataFolderPath);
        boolean exists = (folder.exists() && folder.isDirectory());
        if (!exists) {
            exists = folder.mkdir();
            if (exists) {
                print("AFCT-Data folder created successfully.");
            } else {
                errorPrint("Error: Unable to create AFCT-Data folder.");
            }
        }

        return dataFolderPath;
    }

    public static String OLD_getPreferencesFilePath() {
        String dataFolderPath = OLD_getAFCTDataFolderPath();
        String sep = FileSystems.getDefault().getSeparator();

        String filepath = dataFolderPath + sep + PREFERENCES_FILE_NAME;

        File prefsFile = new File(filepath);

        // TODO: handle being unable to create preferences file
        try {
            if (prefsFile.createNewFile()) {
                print("AFCT-Preferences file created successfully: " + prefsFile.getName());
            }
        } catch (IOException e) {
            errorPrint("An error occurred while creating the file: " + e.getMessage());
            e.printStackTrace();
        }
        return filepath;
    }

    public static Path getAFCTDataFolderPath() {
        OSType osType = determineOSType();
        Path dataFolderPath;

        String homeDir = System.getProperty("user.home");

        //TODO: test if the paths for Mac and Linux actually work
        switch (osType) {
            case WINDOWS:
                dataFolderPath = Paths.get(homeDir, "AppData", "Local", AFCT_DATA_FOLDER_NAME);
                break;
            case MAC:
                // TODO This path seems to not work for macOS? Need to look into this later...
                dataFolderPath = Paths.get(homeDir, "Library", "Application Support", AFCT_DATA_FOLDER_NAME);
                break;
            case LINUX:
                String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
                if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
                    dataFolderPath = Paths.get(xdgConfigHome, AFCT_DATA_FOLDER_NAME);
                } else {
                    dataFolderPath = Paths.get(homeDir, ".config", AFCT_DATA_FOLDER_NAME);
                }
                break;
            default:
                String workingDIr = System.getProperty("user.dir");
                dataFolderPath = Paths.get(workingDIr, AFCT_DATA_FOLDER_NAME);
                break;
        }


        // Create the AFCT-Data folder if it is missing
        if (Files.notExists(dataFolderPath)) {
            try {
                Files.createDirectories(dataFolderPath);
                print("AFCT data folder created: " + dataFolderPath);
            } catch (IOException e) {
                errorPrint("Error creating AFCT data folder: " + dataFolderPath);
                errorPrint(e.getMessage());
            }
        } else if (!Files.isDirectory(dataFolderPath)) {
            errorPrint("Error: Path exists but is not a directory: " + dataFolderPath);
        } else {
            //print("AFCT data folder already exists: " + dataFolderPath);
        }

        return dataFolderPath;
    }

    public static Path getPreferencesFilePath() {
        Path dataFolderPath = getAFCTDataFolderPath();

        Path filepath = dataFolderPath.resolve(PREFERENCES_FILE_NAME);

        File prefsFile = filepath.toFile();

        // TODO: handle being unable to create preferences file
        try {
            if (prefsFile.createNewFile()) {
                print("AFCT-Preferences file created successfully: " + prefsFile.getName());
            }
        } catch (IOException e) {
            errorPrint("An error occurred while creating the file: " + e.getMessage());
            e.printStackTrace();
        }
        return filepath;
    }
}
