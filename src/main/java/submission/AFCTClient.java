package submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import gui.Globals;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import static gui.Globals.stringToJson;

public class AFCTClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private String token;
    private int connectTimeoutMs = 15_000;
    private int readTimeoutMs = 60_000; // TODO: add a way to get the appropriate timeout from the server

    public AFCTClient(String baseUrl) {
        //this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = fixUrl(baseUrl);
    }

    public static String fixUrl(String baseUrl) {
        String fixedUrl = baseUrl.trim();
        // Strip ending "/" if present
        if (baseUrl.endsWith("/")) {
            fixedUrl = baseUrl.substring(0, baseUrl.length() - 1).trim();
        }

        // TODO: test *https* on AFCT server
        // Add "http://" to the beginning if it is missing
        if (!fixedUrl.startsWith("http://") && !fixedUrl.startsWith("https://")) {
            fixedUrl = "http://" + fixedUrl;
        }
        return fixedUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public AFCTClient timeouts(int connectMs, int readMs) {
        this.connectTimeoutMs = connectMs;
        this.readTimeoutMs = readMs;
        return this;
    }

    // ================================================================
    // Auth
    // ================================================================
    public String login(String email, String password) throws IOException {
        URL url = new URL(baseUrl + "/api/public/login");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        String json = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status != 200) {
            Globals.sessionHandler.clearStartTime();
            throw httpError("POST /api/public/login", status, body);
        } else {
            Globals.sessionHandler.updateStartTime();
        }

        Map<String, Object> res = parseJson(body, Map.class);
        this.token = (String) res.get("token");
        return this.token;
    }

    public boolean isAuthenticated() {
        return token != null && !token.isBlank();
    }

    // ================================================================
    // Courses
    // ================================================================
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCourses(String userEmail) throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + "/api/courses/userCourses/" + userEmail);
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status != 200) {
            throw httpError("GET /api/courses/userCourses/" + userEmail, status, body);
        }
        return parseJson(body, List.class);
    }

    // ================================================================
    // Assignments
    // ================================================================
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAssignments(String courseId) throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + "/api/courses/" + courseId + "/assignments");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status != 200) {
            throw httpError("GET /api/courses/[id]/assignments", status, body);
        }
        return parseJson(body, List.class);
    }

    // ================================================================
    // Problems
    // ================================================================
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getProblems(String assignmentId) throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + "/api/assignments/" + assignmentId + "/problems");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status != 200) {
            throw httpError("GET /api/assignments/[id]/problems", status, body);
        }
        return parseJson(body, List.class);
    }

    // ================================================================
    // Submissions (multipart/form-data)
    // ================================================================
    @SuppressWarnings("unchecked")
    public Map<String, Object> createSubmission(String assignmentId, String problemId, String content, File file) throws IOException {
        // TODO: this looks like it does not actually submit the courseID as part of the submission.
        //      Is this true? and if so - why? is it a mistake?
        ensureAuth();

        String boundary = "----JavaBoundary" + System.currentTimeMillis();
        URL url = new URL(baseUrl + "/api/submissions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            writeFormField(out, "assignmentId", assignmentId, boundary);
            writeFormField(out, "problemId", problemId, boundary);
            writeFormField(out, "content", content, boundary);
            if (file != null && file.exists()) {
                writeFileField(out, "file", file, boundary);
            }
            out.writeBytes("--" + boundary + "--\r\n");
        }

        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status != 201) {
            throw httpError("POST /api/submissions", status, body);
        }
        return parseJson(body, Map.class);
    }

    // ================================================================
    // Helpers
    // ================================================================
    private HttpURLConnection openGet(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("GET");
        if (isAuthenticated()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        return conn;
    }

    private static String readBody(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return ""; // can be null in some network errors
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static IOException httpError(String label, int status, String body) {
        String pretty = tryPretty(body);
        System.err.println("HTTP ERROR " + status + " on " + label);
        System.err.println("Body:\n" + pretty);

        if (body == null || body.isEmpty()) {
            return new IOException("HTTP " + status + " on " + label);
        }

        try {
            JsonObject jsonBody = stringToJson(body);
            if (jsonBody.has("error")) {
                return new IOException(jsonBody.get("error").getAsString());
            }
        } catch (JsonSyntaxException e) {
            return new IOException("HTTP " + status + " on " + label);
        }

        return new IOException("HTTP " + status + " on " + label);
    }

    private static String tryPretty(String body) {
        if (body == null || body.isBlank()) return "<empty>";
        try {
            Object any = MAPPER.readValue(body, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(any);
        } catch (Exception ignore) {
            return body;
        }
    }

    private static <T> T parseJson(String json, Class<T> cls) throws IOException {
        return MAPPER.readValue(json, cls);
    }

    private static void prettyPrint(Object obj) {
        try {
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
        } catch (Exception e) {
            System.out.println(String.valueOf(obj));
        }
    }

    private void ensureAuth() throws IOException {
        if (!isAuthenticated()) {
            throw new IOException("Not authenticated; call login() first.");
        }
    }

    private static void writeFormField(DataOutputStream out, String name, String value, String boundary) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.writeBytes(value + "\r\n");
    }

    private static void writeFileField(DataOutputStream out, String name, File file, String boundary) throws IOException {
        String fileName = file.getName();
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n");
        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
        out.writeBytes("\r\n");
    }
}