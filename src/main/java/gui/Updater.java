package gui;

import com.sun.tools.javac.Main;
import gui.components.SaveFileDialog;
import gui.popups.UpdatePopup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static gui.Globals.*;

public class Updater {
    private String[] headers;
    public UpdatePopup updatePopup;
    public static final Pattern versionRegex = Pattern.compile("v(\\d+)\\.(\\d+)\\.(\\d+)");
    public static final Pattern extractVersionRegex = Pattern.compile(".*?-(v\\d+\\.\\d+\\.\\d+)\\..*?");

    public Updater() {
        // TODO update headers
        headers = new String[]{"Authorization", "",
                "Accept", "application/jar"};

        updatePopup = new UpdatePopup();

        print("Updater loaded.");
    }

    public enum Version {
        SAME, OLDER, NEWER, ERROR
    }

    private static String addUrlParams(String url, Map<String, String> parameters) {
        StringBuilder paramUrl = new StringBuilder(url);
        paramUrl.append("?");
        for (String key : parameters.keySet()) {
            paramUrl.append(key).append("=").append(parameters.get(key)).append("&");
        }
        paramUrl.deleteCharAt(paramUrl.length()-1);
        return paramUrl.toString();
    }


    private void downloadFile(HttpURLConnection connection, Path targetPath) throws IOException {
        //TODO add a loading bar that displays download progress
        Component parent = updatePopup.getComponent();
        int contentLength = connection.getContentLength();
        try (InputStream in = connection.getInputStream()) {
            ProgressMonitor progressMonitor = new ProgressMonitor(parent,
                    "Downloading " + targetPath.getFileName().toString(),
                    "", 0, contentLength);

            progressMonitor.setMillisToPopup(10); // Time to wait before popup, just an example value

            try (InputStream monitorInputStream = new ProgressMonitorInputStream(parent, "Downloading " + targetPath.getFileName().toString(), in)) {
                Files.copy(monitorInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }


    public Result downloadApp(JFrame frame, String latestFile) throws IOException {
        String url = APP_URL + LATEST_RELEASE_PATH + latestFile;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        Result result = new Result(Status.GOOD);

        if (connection.getResponseCode() == 200) {
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            try {
                // Can throw URISyntaxException
                File currentJar = new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());

                Path targetPath = (new File(currentJar.getParent() + "/" + latestFile)).toPath();
                downloadFile(connection, targetPath);

                /* Construct command: java -jar application.jar */
                ArrayList<String> command = new ArrayList<>();
                command.add(javaBin);
                command.add("-jar");
                command.add(targetPath.toString());
                command.add("update");
                command.add("\"" + currentJar.getPath() + "\"");

                // TODO: before quitting and starting new process, PROMPT USER TO SAVE ALL ACTIVE WORK before restarting
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.start();
                System.exit(0);
            } catch (URISyntaxException ignored) { }

            // If auto save and restart fails, do a manual save
            File targetFile = new SaveFileDialog(frame, new File(latestFile)).display();
            if (targetFile != null) {
                Path targetPath = targetFile.toPath();
                downloadFile(connection, targetPath);
            } else {
                result.status = Status.WARNING;
            }
        } else {
            String message = "Error downloading JAR file. Response Code: " + connection.getResponseCode();
            result = new Result(Status.ERROR, message, connection.getResponseMessage());
            errorPrint(message);
        }
        connection.disconnect();

        return result;
    }

    /**
     *
     *
     * @param currentVersion
     * @param otherVersion
     * @return Version.OLDER if currentVersion is older than otherVersion,
     *         Version.NEWER if currentVersion is newer than otherVersion,
     *         Version.SAME if currentVersion equals otherVersion,
     *         Version.ERROR if either currentVersion or otherVersion does not contain a substring like:
     *                       vMAJOR.MINOR.PATCH
     *                       where MAJOR, MINOR, and PATCH are all integers
     */
    public static Version compareVersions(String currentVersion, String otherVersion) {
        Matcher current = versionRegex.matcher(currentVersion);
        Matcher other = versionRegex.matcher(otherVersion);
        if (current.find() && other.find()) {
            for (int i = 1; i <= 3; i++) {
                int versionDiff = Integer.parseInt(current.group(i)) - Integer.parseInt(other.group(i));
                if (versionDiff != 0) {
                    if (versionDiff < 0) {
                        return Version.OLDER;
                    } else {
                        return Version.NEWER;
                    }
                }
            }
            return Version.SAME;
        } else {
            return Version.ERROR;
        }
    }

    public HttpResponse<String> getUrl(String url) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder().uri(new URI(url)).headers(headers).GET().build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return response;
    }

    public HttpResponse<String> getUrl(String url, String branch) {
        Map<String, String> params = Map.of("ref", branch);
        return getUrl(addUrlParams(url, params));
    }

    public static class CheckUpdateAction extends AbstractAction {
        public CheckUpdateAction() {
            super("Check for Updates...");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            updater.updatePopup.showPopup();
        }
    }
}
