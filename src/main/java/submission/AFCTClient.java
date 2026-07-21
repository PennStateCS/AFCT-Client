package submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import static gui.Globals.stringToJson;

public class AFCTClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** All client endpoints live under this prefix (except /api/health). */
    private static final String API_PREFIX = "/api/client/v1";

    private final String baseUrl;
    private String token;           // bearer token from POST /auth/login
    private int connectTimeoutMs = 15000;
    private int readTimeoutMs = 60_000; // TODO: add a way to get the appropriate timeout from the server
    private boolean useHTTPS = false;
    /**
     * When true, HTTPS connections skip certificate/hostname validation (self-signed
     * servers). When false, connections use the JVM's normal trust store and will
     * fail on an untrusted or mismatched certificate — this must be respected per
     * connection, since a bearer token is sent over it.
     */
    private final boolean insecureTls;

    /** Cache of problems per assignment, populated by getAssignments() (problems come embedded). */
    private final Map<String, List<Map<String, Object>>> assignmentProblemsCache = new java.util.HashMap<>();

    /**
     * The course's IANA timezone and the server's clock, as of the last getAssignments() call.
     * dueDate/lateCutoff are UTC on the wire — callers should render them in this timezone —
     * and serverTime lets callers compare against the server's clock instead of the local
     * machine's, which may be skewed or in a different zone.
     */
    private String lastAssignmentsTimezone;
    private Instant lastAssignmentsServerTime;

    /** Equivalent to {@code AFCTClient(baseUrl, true)} — kept for source compatibility. */
    public AFCTClient(String baseUrl) {
        this(baseUrl, true);
    }

    public AFCTClient(String baseUrl, boolean insecureTls) {
        String url = fixUrl(baseUrl);
        this.insecureTls = insecureTls;

        if (baseUrl.trim().startsWith("https://") || url.endsWith("443")) {
            this.useHTTPS = true;
        }

        if (useHTTPS) {
            this.baseUrl = "https://" + url;
        } else {
            this.baseUrl = "http://" + url;
        }
    }

    /**
     * Full constructor used by {@link AFCTClientBuilder}.
     */
    AFCTClient(String baseUrl, boolean insecureTls, Duration connectTimeout, Duration readTimeout,
               int maxRetries, long baseBackoffMs) {
        this(baseUrl, insecureTls);
        this.connectTimeoutMs = (int) connectTimeout.toMillis();
        this.readTimeoutMs = (int) readTimeout.toMillis();
        // maxRetries, baseBackoffMs stored for future use
    }

    public static String fixUrl(String baseUrl) {
        String fixedUrl = baseUrl.trim();
        // Strip ending "/" if present
        if (baseUrl.endsWith("/")) {
            fixedUrl = baseUrl.substring(0, baseUrl.length() - 1).trim();
        }

        // TODO: test *https* on AFCT server
        // Add "http://" to the beginning if it is missing
//        if (!fixedUrl.startsWith("http://") && !fixedUrl.startsWith("https://")) {
//            fixedUrl = "http://" + fixedUrl;
//        }

        if (fixedUrl.startsWith("http://")) {
            fixedUrl = fixedUrl.substring("http://".length());
        } else if (fixedUrl.startsWith("https://")) {
            fixedUrl = fixedUrl.substring("https://".length());
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
    /**
     * Logs in via POST /api/client/v1/auth/login and stores the bearer token.
     * The token has a sliding 30-day expiry; every authenticated call renews it.
     */
    @SuppressWarnings("unchecked")
    public String login(String email, String password) throws IOException {
        URL url = new URL(baseUrl + API_PREFIX + "/auth/login");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("deviceName", deviceName());
        try (OutputStream os = conn.getOutputStream()) {
            os.write(MAPPER.writeValueAsBytes(payload));
        }

        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status == 401) {
            throw new IOException("Invalid email or password.");
        }
        if (status == 429) {
            String retryAfter = conn.getHeaderField("Retry-After");
            throw new IOException("Too many login attempts. Try again in "
                    + (retryAfter != null ? retryAfter + " seconds." : "a moment."));
        }
        if (status != 200) {
            throw httpError("POST " + API_PREFIX + "/auth/login", status, body);
        }

        Map<String, Object> res = parseJson(body, Map.class);
        this.token = (String) res.get("token");
        if (this.token == null || this.token.isBlank()) {
            throw new IOException("Login succeeded but no token was returned.");
        }
        return this.token;
    }

    /** Revokes the current token via POST /auth/logout. Best-effort. */
    public void logout() {
        if (!isAuthenticated()) return;
        try {
            URL url = new URL(baseUrl + API_PREFIX + "/auth/logout");
            HttpURLConnection conn = openConnection(url);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);
            conn.setRequestMethod("POST");
            addAuthHeaders(conn);
            conn.getResponseCode(); // fire and forget
        } catch (IOException ex) {
            String msg = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            System.err.println("[AFCTClient] Logout revoke request failed: " + msg);
        } finally {
            this.token = null;
        }
    }

    /**
     * Checks whether the stored token is still valid via GET /auth/me.
     * Returns the user object on success, null if the token is expired/revoked.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkToken() throws IOException {
        if (!isAuthenticated()) return null;
        URL url = new URL(baseUrl + API_PREFIX + "/auth/me");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status == 401) {
            this.token = null; // don't retry a rejected token — server logs it as a security event
            return null;
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/auth/me", status, body);
        }
        Map<String, Object> res = parseJson(body, Map.class);
        return (Map<String, Object>) res.get("user");
    }

    private static String deviceName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "afct-client";
        }
    }

    public boolean isAuthenticated() {
        return token != null && !token.isBlank();
    }

    // ================================================================
    // Courses
    // ================================================================
    /** Courses visible to the signed-in user (derived from the token — no email needed). */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCourses() throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + API_PREFIX + "/courses");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status == 401) {
            throw handleUnauthorized("GET " + API_PREFIX + "/courses");
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/courses", status, body);
        }
        // Response shape: { "courses": [ ... ] }
        Map<String, Object> wrapper = parseJson(body, Map.class);
        List<Map<String, Object>> courses = (List<Map<String, Object>>) wrapper.get("courses");
        return courses != null ? courses : new java.util.ArrayList<>();
    }

    // ================================================================
    // Assignments
    // ================================================================
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAssignments(String courseId) throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + API_PREFIX + "/courses/" + courseId + "/assignments");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status == 401) {
            throw handleUnauthorized("GET " + API_PREFIX + "/courses/{id}/assignments");
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/courses/{id}/assignments", status, body);
        }

        // Response shape: { timezone, serverTime, assignments: [ { id, title, description,
        //   dueDate, allowLateSubmissions, lateCutoff, problems: [...] } ] }
        Map<String, Object> wrapper = parseJson(body, Map.class);
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) wrapper.get("assignments");
        if (assignments == null) assignments = new java.util.ArrayList<>();

        // Cache the course timezone and the server's clock so callers can render dueDate/
        // lateCutoff correctly and run "is this upcoming" checks without trusting the
        // local machine's clock.
        Object tz = wrapper.get("timezone");
        this.lastAssignmentsTimezone = tz != null ? String.valueOf(tz) : null;
        Object serverTimeStr = wrapper.get("serverTime");
        if (serverTimeStr != null) {
            try {
                this.lastAssignmentsServerTime = Instant.parse(String.valueOf(serverTimeStr));
            } catch (Exception e) {
                this.lastAssignmentsServerTime = null;
            }
        } else {
            this.lastAssignmentsServerTime = null;
        }

        // Cache embedded problems so getProblems() can serve them without a second network call.
        // "solved" = full marks on the problem (grade == maxPoints).
        assignmentProblemsCache.clear();
        for (Map<String, Object> a : assignments) {
            String assignmentId = String.valueOf(a.get("id"));
            List<Map<String, Object>> problems = (List<Map<String, Object>>) a.get("problems");
            if (problems != null) {
                List<Map<String, Object>> enriched = new java.util.ArrayList<>();
                for (Map<String, Object> p : problems) {
                    Map<String, Object> copy = new java.util.HashMap<>(p);
                    Object grade = copy.get("grade");
                    Object maxPoints = copy.get("maxPoints");
                    boolean solved = grade instanceof Number && maxPoints instanceof Number
                            && ((Number) grade).doubleValue() >= ((Number) maxPoints).doubleValue();
                    copy.put("solved", solved);
                    enriched.add(copy);
                }
                assignmentProblemsCache.put(assignmentId, enriched);
            }
        }

        return assignments;
    }

    /**
     * The course's IANA timezone as of the last getAssignments() call, or null if not
     * yet fetched. Use this to render dueDate/lateCutoff (which arrive as UTC).
     */
    public String getLastAssignmentsTimezone() {
        return lastAssignmentsTimezone;
    }

    /**
     * The server's clock as of the last getAssignments() call, or null if not yet
     * fetched. Prefer this over Instant.now()/LocalDateTime.now() for "is this due
     * date upcoming" checks — the docs call this out specifically so callers don't
     * have to trust the local machine's clock.
     */
    public Instant getLastAssignmentsServerTime() {
        return lastAssignmentsServerTime;
    }

    /**
     * The caller's entire course tree in one call: every visible course, each with its
     * assignments, each assignment with its problems. Lets the UI load once and filter
     * locally instead of fetching per course. Returns the raw wrapper
     * { serverTime, courses: [ { ..., assignments: [ { ..., problems: [...] } ] } ] };
     * also caches the server clock for "is this upcoming" checks.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTree() throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + API_PREFIX + "/tree");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status == 401) {
            throw handleUnauthorized("GET " + API_PREFIX + "/tree");
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/tree", status, body);
        }

        Map<String, Object> wrapper = parseJson(body, Map.class);
        if (wrapper == null) wrapper = new java.util.HashMap<>();

        // Cache the server's clock so "upcoming" checks don't trust the local machine.
        Object serverTimeStr = wrapper.get("serverTime");
        if (serverTimeStr != null) {
            try {
                this.lastAssignmentsServerTime = Instant.parse(String.valueOf(serverTimeStr));
            } catch (Exception e) {
                this.lastAssignmentsServerTime = null;
            }
        }
        return wrapper;
    }

    // ================================================================
    // Problems
    // ================================================================
    public List<Map<String, Object>> getProblems(String assignmentId) throws IOException {
        ensureAuth();

        // Problems arrive embedded in the assignments response; there is no separate
        // problems endpoint in the client API. getAssignments() populates this cache.
        List<Map<String, Object>> cached = assignmentProblemsCache.get(assignmentId);
        if (cached != null) {
            return cached;
        }
        throw new IOException("Problems not loaded yet — refresh the course to reload assignments.");
    }

    // ================================================================
    // Submissions (multipart/form-data)
    // ================================================================
    /**
     * Uploads a solution via POST /api/client/v1/submissions (multipart).
     * Returns { submissionId, status: "PENDING" } on 202 — poll {@link #getSubmission}
     * for the result. courseId is derived server-side from the assignment.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createSubmission(String courseId, String assignmentId, String problemId, File file) throws IOException {
        ensureAuth();

        String boundary = "----JavaBoundary" + System.currentTimeMillis();
        URL url = new URL(baseUrl + API_PREFIX + "/submissions");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        addAuthHeaders(conn);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            writeFormField(out, "assignmentId", assignmentId, boundary);
            writeFormField(out, "problemId", problemId, boundary);
            if (file != null && file.exists()) {
                writeFileField(out, "file", file, boundary);
            }
            out.writeBytes("--" + boundary + "--\r\n");
        }

        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status == 401) {
            throw handleUnauthorized("POST " + API_PREFIX + "/submissions");
        }
        if (status == 429) {
            String retryAfter = conn.getHeaderField("Retry-After");
            throw new IOException("Resubmit cooldown active. Try again in "
                    + (retryAfter != null ? retryAfter + " seconds." : "a moment."));
        }
        // Server returns 202 Accepted on success
        if (status < 200 || status >= 300) {
            throw httpError("POST " + API_PREFIX + "/submissions", status, body);
        }
        return parseJson(body, Map.class);
    }

    /**
     * Fetches one submission's result: { id, status, correct, grade, feedback }.
     * status moves PENDING → PROCESSING → COMPLETED | FAILED; correct/grade/feedback
     * are null until evaluation finishes.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSubmission(String submissionId) throws IOException {
        ensureAuth();
        URL url = new URL(baseUrl + API_PREFIX + "/submissions/" + submissionId);
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status == 401) {
            throw handleUnauthorized("GET " + API_PREFIX + "/submissions/{id}");
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/submissions/{id}", status, body);
        }
        return parseJson(body, Map.class);
    }

    /**
     * The caller's submission history for one problem, newest first. Each entry:
     * { id, status, correct, submittedAt, fileName, feedback }. `feedback` (the
     * evaluator witness) is null while the submission is still queued/processing.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSubmissions(String assignmentId, String problemId) throws IOException {
        ensureAuth();
        String query = "?assignmentId=" + java.net.URLEncoder.encode(assignmentId, StandardCharsets.UTF_8)
                + "&problemId=" + java.net.URLEncoder.encode(problemId, StandardCharsets.UTF_8);
        URL url = new URL(baseUrl + API_PREFIX + "/submissions" + query);
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status == 401) {
            throw handleUnauthorized("GET " + API_PREFIX + "/submissions");
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/submissions", status, body);
        }
        Map<String, Object> wrapper = parseJson(body, Map.class);
        List<Map<String, Object>> subs = (List<Map<String, Object>>) wrapper.get("submissions");
        return subs != null ? subs : new java.util.ArrayList<>();
    }

    /**
     * Polls {@link #getSubmission} until the submission reaches COMPLETED or FAILED,
     * or the timeout elapses. Returns the last submission state seen.
     * Blocking — call from a background thread.
     */
    public Map<String, Object> waitForResult(String submissionId, Duration timeout) throws IOException {
        return waitForResult(submissionId, timeout, null);
    }

    /**
     * Same as {@link #waitForResult(String, Duration)} but reports every polled state
     * to {@code onUpdate} (called on the polling thread).
     */
    public Map<String, Object> waitForResult(String submissionId, Duration timeout,
                                             java.util.function.Consumer<Map<String, Object>> onUpdate) throws IOException {
        Instant deadline = Instant.now().plus(timeout);
        long delayMs = 1000;
        Map<String, Object> sub = getSubmission(submissionId);
        while (Instant.now().isBefore(deadline)) {
            if (onUpdate != null) onUpdate.accept(sub);
            String status = String.valueOf(sub.get("status"));
            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return sub;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return sub;
            }
            delayMs = Math.min(delayMs * 2, 5000); // gentle backoff, capped at 5s
            sub = getSubmission(submissionId);
        }
        return sub;
    }

    // ================================================================
    // Helpers
    // ================================================================
    private HttpURLConnection openGet(URL url) throws IOException {
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("GET");
        addAuthHeaders(conn);
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

    /**
     * Clears the stored token on a 401 so isAuthenticated() reports false and the next
     * requireAuthenticated() call triggers a fresh login instead of silently resending
     * a rejected token (the server logs repeated invalid-token requests as a security
     * event, so this must not be retried in a loop).
     */
    private IOException handleUnauthorized(String label) {
        this.token = null;
        System.err.println("HTTP ERROR 401 on " + label + " — token cleared, re-login required");
        return new IOException("Your session has expired. Please log in again.");
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

    /** Adds the bearer token. Every endpoint except login/health requires it. */
    private void addAuthHeaders(HttpURLConnection conn) {
        if (token != null && !token.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Only bypass certificate/hostname validation when the user has explicitly
        // opted into it (unchecked "Validate SSL Certificate" -> insecureTls=true),
        // e.g. for a self-signed dev/lab server. Otherwise leave the connection on
        // the JVM's normal trust store, since a bearer token travels over this
        // connection and a MITM could otherwise capture it silently.
        if (insecureTls && conn instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) conn;
            try {
                javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    }
                };
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                sc.init(null, trustAll, new java.security.SecureRandom());
                https.setSSLSocketFactory(sc.getSocketFactory());
                https.setHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                System.err.println("[AFCTClient] Failed to apply trust-all SSL: " + e.getMessage());
            }
        }

        return conn;
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