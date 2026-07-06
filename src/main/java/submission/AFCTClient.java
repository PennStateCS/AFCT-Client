package submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import gui.Globals;

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

    static {
        // Install a global CookieManager so HttpURLConnection automatically stores
        // and sends cookies across requests — same behaviour as a browser.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    }

    private final String baseUrl;
    private String token;           // JWT token, if server returns one
    private String sessionCookie;   // kept for reference; CookieManager handles actual sending
    private int connectTimeoutMs = 15000;
    private int readTimeoutMs = 30000;
    private boolean useHTTPS = false;

    /** Cache of problems per assignment, populated by getAssignments() from student-grades. */
    private final Map<String, List<Map<String, Object>>> assignmentProblemsCache = new java.util.HashMap<>();

    public AFCTClient(String baseUrl) {
        String url = fixUrl(baseUrl);

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
        this(baseUrl);
        this.connectTimeoutMs = (int) connectTimeout.toMillis();
        this.readTimeoutMs = (int) readTimeout.toMillis();
        // insecureTls, maxRetries, baseBackoffMs stored for future use
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

    public AFCTClient timeouts(int connectMs, int readMs) {
        this.connectTimeoutMs = connectMs;
        this.readTimeoutMs = readMs;
        return this;
    }

    // ================================================================
    // Auth
    // ================================================================
    public String login(String email, String password) throws IOException {
        // Step 1: Get CSRF token + its full cookie from NextAuth
        String[] csrf = fetchCsrfTokenAndCookie();
        String csrfToken = csrf[0];
        String csrfCookie = csrf[1]; // full "name=hash|signature" value
        System.out.println("[AFCTClient] CSRF token: " + csrfToken);
        System.out.println("[AFCTClient] CSRF cookie: " + csrfCookie);

        // Step 2: Sign in via NextAuth credentials provider
        String callbackUrl = baseUrl + "/";
        String formBody = "email=" + urlEncode(email)
                + "&password=" + urlEncode(password)
                + "&csrfToken=" + urlEncode(csrfToken)
                + "&callbackUrl=" + urlEncode(callbackUrl)
                + "&json=true";

        URL url = new URL(baseUrl + "/api/auth/callback/credentials");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setInstanceFollowRedirects(false); // handle redirect manually
        // CookieManager automatically sends the CSRF cookie set by /api/auth/csrf
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(formBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        System.out.println("[AFCTClient] NextAuth callback status: " + status);
        conn.getHeaderFields().forEach((k, v) -> System.out.println("  " + k + ": " + v));

        // Capture session cookies from the callback response
        captureSetCookieHeaders(conn);

        String body = readBody(conn);
        System.out.println("[AFCTClient] NextAuth callback body: " + body);

        // NextAuth returns 302 redirect on success (or 200 with json=true)
        if (status == 401 || status == 403) {
            Globals.sessionHandler.clearStartTime();
            throw new IOException("Invalid email or password.");
        }

        // 302 to an error page = wrong credentials; 302 elsewhere = success
        if (status == 302) {
            String location = conn.getHeaderField("location");
            if (location != null && location.contains("error=")) {
                Globals.sessionHandler.clearStartTime();
                throw new IOException("Invalid email or password.");
            }
        } else if (status == 401 || status == 403) {
            Globals.sessionHandler.clearStartTime();
            throw new IOException("Invalid email or password.");
        }

        // Dump cookie store so we can see what was captured
        CookieManager cm = (CookieManager) CookieHandler.getDefault();
        System.out.println("[AFCTClient] Cookie store after login:");
        for (HttpCookie c : cm.getCookieStore().getCookies()) {
            System.out.println("  " + c.getName() + "=" + c.getValue().substring(0, Math.min(20, c.getValue().length())) + "...");
        }

        Globals.sessionHandler.updateStartTime();
        // CookieManager stores the session-token cookie automatically
        this.token = "cookie-session"; // sentinel value — real auth is via cookies

        return this.token;
    }

    /** Returns [csrfToken, fullCsrfCookie] where fullCsrfCookie is "name=hash|signature". */
    private String[] fetchCsrfTokenAndCookie() throws IOException {
        URL url = new URL(baseUrl + "/api/auth/csrf");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("GET");
        int status = conn.getResponseCode();

        // Capture the full CSRF cookie from the Set-Cookie header
        String csrfCookie = null;
        List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
        System.out.println("[AFCTClient] CSRF endpoint Set-Cookie: " + setCookies);
        if (setCookies != null) {
            for (String c : setCookies) {
                String nameValue = c.split(";")[0];
                if (nameValue.contains("csrf-token")) {
                    csrfCookie = nameValue;
                    break;
                }
            }
        }

        String body = readBody(conn);
        if (status != 200) throw new IOException("Failed to fetch CSRF token: HTTP " + status);
        Map<String, Object> res = parseJson(body, Map.class);
        String csrfToken = (String) res.get("csrfToken");

        if (csrfCookie == null) {
            // Fallback: construct it from the token (may not have signature but worth trying)
            csrfCookie = "next-auth.csrf-token=" + csrfToken;
        }

        return new String[]{csrfToken, csrfCookie};
    }

    private void captureSetCookieHeaders(HttpURLConnection conn) {
        List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
        if (cookies != null && !cookies.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String c : cookies) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(c.split(";")[0]);
            }
            // Append to existing cookies if any
            if (this.sessionCookie != null && !this.sessionCookie.isBlank()) {
                this.sessionCookie += "; " + sb;
            } else {
                this.sessionCookie = sb.toString();
            }
            System.out.println("[AFCTClient] Cookies so far: " + this.sessionCookie);
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    @SuppressWarnings("unused")
    private String loginLegacy(String email, String password) throws IOException {
        // Kept for reference — /api/public/login returns user but no session cookie
        URL url = new URL(baseUrl + "/api/public/login");
        HttpURLConnection conn = openConnection(url);
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
        }

        Map<String, Object> res = parseJson(body, Map.class);
        this.token = (String) res.get("token");
        if (this.token == null && res.containsKey("user")) {
            this.token = "cookie-session"; // sentinel — real auth is via sessionCookie
        }

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
        // /api/courses/{id}/assignments is ADMIN/FACULTY only.
        // /api/courses/{id}/student-grades is accessible to enrolled students and
        // returns the same assignment fields plus embedded problems.
        URL url = new URL(baseUrl + "/api/courses/" + courseId + "/student-grades");
        HttpURLConnection conn = openGet(url);
        int status = conn.getResponseCode();
        String body = readBody(conn);

        if (status != 200) {
            throw httpError("GET /api/courses/[id]/student-grades", status, body);
        }

        // Response shape: { "assignments": [ { id, title, description, dueDate, problems: [...] } ] }
        Map<String, Object> wrapper = parseJson(body, Map.class);
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) wrapper.get("assignments");
        if (assignments == null) assignments = new java.util.ArrayList<>();

        // Cache embedded problems so getProblems() can serve them without a second network call.
        // Also add a "solved" boolean (true when the latest submission status is "CORRECT").
        assignmentProblemsCache.clear();
        for (Map<String, Object> a : assignments) {
            String assignmentId = String.valueOf(a.get("id"));
            List<Map<String, Object>> problems = (List<Map<String, Object>>) a.get("problems");
            if (problems != null) {
                List<Map<String, Object>> enriched = new java.util.ArrayList<>();
                for (Map<String, Object> p : problems) {
                    Map<String, Object> copy = new java.util.HashMap<>(p);
                    String st = String.valueOf(copy.getOrDefault("status", ""));
                    copy.put("solved", "CORRECT".equalsIgnoreCase(st));
                    enriched.add(copy);
                }
                assignmentProblemsCache.put(assignmentId, enriched);
            }
        }

        return assignments;
    }

    // ================================================================
    // Problems
    // ================================================================
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getProblems(String assignmentId) throws IOException {
        ensureAuth();

        // Return from cache populated by getAssignments() if available.
        List<Map<String, Object>> cached = assignmentProblemsCache.get(assignmentId);
        if (cached != null) {
            System.out.println("[AFCTClient] getProblems(" + assignmentId + "): serving " + cached.size() + " problems from cache");
            return cached;
        }

        // Fallback: dedicated problems endpoint (may require instructor role on some servers).
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
    public Map<String, Object> createSubmission(String courseId, String assignmentId, String problemId, File file) throws IOException {
        ensureAuth();

        String boundary = "----JavaBoundary" + System.currentTimeMillis();
        URL url = new URL(baseUrl + "/api/submissions");
        HttpURLConnection conn = openConnection(url);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        addAuthHeaders(conn);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            writeFormField(out, "courseId", courseId, boundary);
            writeFormField(out, "assignmentId", assignmentId, boundary);
            writeFormField(out, "problemId", problemId, boundary);
            if (file != null && file.exists()) {
                writeFileField(out, "file", file, boundary);
            }
            out.writeBytes("--" + boundary + "--\r\n");
        }

        int status = conn.getResponseCode();
        String body = readBody(conn);
        // Server returns 202 Accepted on success
        if (status < 200 || status >= 300) {
            throw httpError("POST /api/submissions", status, body);
        }
        return parseJson(body, Map.class);
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

        // Debug: show which cookies CookieManager will send to this URL
        try {
            CookieManager cm = (CookieManager) CookieHandler.getDefault();
            List<HttpCookie> cookies = cm.getCookieStore().get(url.toURI());
            System.out.println("[AFCTClient] GET " + url.getPath() + " — cookies: " + cookies);
        } catch (Exception ignored) {}

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

    /** Adds Authorization header for JWT auth. Cookie-based auth is handled automatically by CookieManager. */
    private void addAuthHeaders(HttpURLConnection conn) {
        if (token != null && !token.isBlank() && !token.equals("cookie-session")) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        // Session cookies (NextAuth) are sent automatically by the global CookieManager
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Apply trust-all SSL directly on each HTTPS connection so it cannot be
        // bypassed by timing or global-context ordering issues.
        if (conn instanceof HttpsURLConnection) {
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