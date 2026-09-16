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

import static gui.Globals.stringToJson;

public class AFCTClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** All client endpoints live under this prefix (except /api/health). */
    private static final String API_PREFIX = "/api/client/v1";

    private final String baseUrl;
    private String token;           // bearer token from POST /auth/login
    private int connectTimeoutMs = 15000;
    private int readTimeoutMs = 30000;

    /**
     * The trust manager from the most recent HTTPS connection, kept so a caller
     * that caught an SSLHandshakeException can ask what was refused and show the
     * chain. Requests on one client are sequential, so one slot is enough.
     */
    private volatile PinningTrustManager lastTrust;

    /**
     * The server's clock as of the last getTree() call. Callers compare due dates
     * against this instead of the local machine's clock, which may be skewed or in
     * a different zone.
     */
    private Instant lastServerTime;

    /** @throws IllegalArgumentException when the address cannot be parsed. */
    public AFCTClient(String baseUrl) {
        this.baseUrl = ServerAddress.parse(baseUrl).baseUrl();
    }

    // ================================================================
    // Auth
    // ================================================================
    /**
     * Logs in via POST /api/client/v1/auth/login and stores the bearer token.
     * The token has a sliding 30-day expiry; every authenticated call renews it.
     */
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

        ApiModels.LoginResponse res = parseJson(body, ApiModels.LoginResponse.class);
        this.token = res.token();
        if (this.token == null || this.token.isBlank()) {
            throw new IOException("Login succeeded but no token was returned.");
        }
        return this.token;
    }

    /**
     * Signs in with a token the user created on the web (Account page) instead of an
     * email and password. Validates it immediately via GET /auth/me so a bad paste
     * fails here at the login window rather than at the first submission.
     * Returns the user object on success, null when the server rejects the token.
     */
    public ApiModels.User loginWithToken(String tokenValue) throws IOException {
        this.token = tokenValue;
        // checkToken clears this.token on a 401, so a rejected token leaves the
        // client unauthenticated rather than holding a value the server refuses.
        return checkToken();
    }

    /** What sign-in methods the server offers; see {@link #getAuthMethods()}. */
    public record AuthMethods(boolean oidcEnabled, String oidcButtonLabel) {}

    /**
     * Asks the server what sign-in methods it offers (GET /auth/methods,
     * unauthenticated). The client and server release separately, so a 404 means an
     * older server, not a broken one: report password-only and carry on. Next
     * serves HTML for an unknown route, which is why the JSON parse failure is the
     * branch that catches it; that fallback is the compatibility contract between
     * the two products.
     */
    public AuthMethods getAuthMethods() throws IOException {
        URL url = new URL(baseUrl + API_PREFIX + "/auth/methods");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("GET");

        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status == 404) {
            return new AuthMethods(false, null);
        }
        if (status != 200) {
            throw httpError("GET " + API_PREFIX + "/auth/methods", status, body);
        }
        try {
            JsonObject json = stringToJson(body);
            JsonObject oidc = json.has("oidc") ? json.getAsJsonObject("oidc") : null;
            boolean enabled = oidc != null && oidc.has("enabled") && oidc.get("enabled").getAsBoolean();
            String label = oidc != null && oidc.has("buttonLabel") && !oidc.get("buttonLabel").isJsonNull()
                    ? oidc.get("buttonLabel").getAsString() : null;
            return new AuthMethods(enabled, label);
        } catch (JsonSyntaxException | IllegalStateException ex) {
            // An older server behind a proxy can 200 with an HTML page too.
            return new AuthMethods(false, null);
        }
    }

    /**
     * Redeems a browser sign-in code (POST /auth/exchange) and stores the bearer
     * token, completing the loopback flow that BrowserSignIn starts.
     * Returns the user object.
     */
    public ApiModels.User exchangeCode(String code, String codeVerifier, String redirectUri) throws IOException {
        URL url = new URL(baseUrl + API_PREFIX + "/auth/exchange");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("code", code);
        payload.put("codeVerifier", codeVerifier);
        payload.put("redirectUri", redirectUri);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(MAPPER.writeValueAsBytes(payload));
        }

        int status = conn.getResponseCode();
        String body = readBody(conn);
        if (status == 401) {
            throw new IOException("The sign-in could not be completed. Try signing in again.");
        }
        if (status == 429) {
            String retryAfter = conn.getHeaderField("Retry-After");
            throw new IOException("Too many attempts. Try again in "
                    + (retryAfter != null ? retryAfter + " seconds." : "a moment."));
        }
        if (status != 200) {
            throw httpError("POST " + API_PREFIX + "/auth/exchange", status, body);
        }

        ApiModels.LoginResponse res = parseJson(body, ApiModels.LoginResponse.class);
        this.token = res.token();
        if (this.token == null || this.token.isBlank()) {
            throw new IOException("Sign-in succeeded but no token was returned.");
        }
        return res.user();
    }

    /** The bearer token currently held, so "stay signed in" can store it. */
    String currentToken() {
        return token;
    }

    /** This machine's name, sent as the token label so its owner can tell devices apart. */
    static String defaultDeviceName() {
        return deviceName();
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
    public ApiModels.User checkToken() throws IOException {
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
        return parseJson(body, ApiModels.MeResponse.class).user();
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
    // Course tree
    // ================================================================
    /**
     * The caller's entire course tree in one call: every visible course, each with
     * its assignments, each assignment with its problems. Everything is already
     * resolved per student server-side (published/visible only, effective dates
     * with extensions applied, grant-adjusted submission caps). Also caches the
     * server clock for "is this upcoming" checks.
     */
    public ApiModels.Tree getTree() throws IOException {
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

        ApiModels.Tree tree = parseJson(body, ApiModels.Tree.class);
        this.lastServerTime = ApiModels.parseIsoOrNull(tree.serverTime());
        return tree;
    }

    /**
     * The server's clock as of the last getTree() call, or null if not yet
     * fetched. Prefer this over Instant.now() for "is this due date upcoming"
     * checks, so the answer does not depend on the local machine's clock.
     */
    public Instant getLastServerTime() {
        return lastServerTime;
    }

    // ================================================================
    // Submissions (multipart/form-data)
    // ================================================================
    /**
     * Uploads a solution via POST /api/client/v1/submissions (multipart).
     * Returns { submissionId, status: "PENDING" } on 202 — poll {@link #getSubmission}
     * for the result. courseId is derived server-side from the assignment.
     */
    public ApiModels.CreateResult createSubmission(String courseId, String assignmentId, String problemId, File file) throws IOException {
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
        return parseJson(body, ApiModels.CreateResult.class);
    }

    /**
     * Fetches one submission's result. status moves PENDING -> PROCESSING ->
     * COMPLETED | FAILED; correct/grade/feedback are null until evaluation finishes.
     */
    public ApiModels.Submission getSubmission(String submissionId) throws IOException {
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
        return parseJson(body, ApiModels.Submission.class);
    }

    /**
     * The submission history for one problem, newest first. On a group problem this
     * is the group's shared attempts, with submittedBy naming who made each one.
     * `feedback` (the evaluator witness) is null while queued/processing.
     */
    public List<ApiModels.Submission> getSubmissions(String assignmentId, String problemId) throws IOException {
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
        List<ApiModels.Submission> subs = parseJson(body, ApiModels.SubmissionList.class).submissions();
        return subs != null ? subs : new java.util.ArrayList<>();
    }

    /**
     * Polls {@link #getSubmission} until the submission reaches COMPLETED or FAILED,
     * or the timeout elapses. Returns the last submission state seen.
     * Blocking — call from a background thread.
     */
    public ApiModels.Submission waitForResult(String submissionId, Duration timeout) throws IOException {
        return waitForResult(submissionId, timeout, null);
    }

    /**
     * Same as {@link #waitForResult(String, Duration)} but reports every polled state
     * to {@code onUpdate} (called on the polling thread).
     */
    public ApiModels.Submission waitForResult(String submissionId, Duration timeout,
                                              java.util.function.Consumer<ApiModels.Submission> onUpdate) throws IOException {
        Instant deadline = Instant.now().plus(timeout);
        long delayMs = 1000;
        ApiModels.Submission sub = getSubmission(submissionId);
        while (Instant.now().isBefore(deadline)) {
            if (onUpdate != null) onUpdate.accept(sub);
            String status = sub.status();
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

        // Trust-on-first-use, per connection. A bearer token travels over this
        // connection, so there is no accept-everything mode; an untrusted chain
        // fails the handshake and the login window decides whether to pin it.
        // The hostname verifier is scoped to pin acceptance: a self-signed CN
        // rarely matches how the student typed the address, and the answer is
        // "the fingerprint matched for this origin", never a blanket yes.
        if (conn instanceof HttpsURLConnection https) {
            try {
                PinningTrustManager trust = PinningTrustManager.forOrigin(baseUrl);
                this.lastTrust = trust;
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
                sc.init(null, new javax.net.ssl.TrustManager[]{trust}, null);
                https.setSSLSocketFactory(sc.getSocketFactory());
                https.setHostnameVerifier((hostname, session) ->
                        trust.acceptedByPin()
                                || HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session));
            } catch (Exception e) {
                // Leave the JVM defaults in place: strict validation, never weaker.
                System.err.println("[AFCTClient] Failed to apply pinned trust: " + e.getMessage());
            }
        }

        return conn;
    }

    /**
     * Why the most recent HTTPS handshake was refused (UNTRUSTED or CHANGED) plus
     * the offered chain, or null when the last connection was not refused by the
     * pinning trust manager. Ask this after catching an SSLHandshakeException.
     */
    public PinningTrustManager trustFailure() {
        PinningTrustManager trust = this.lastTrust;
        return trust != null && trust.failure() != null ? trust : null;
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