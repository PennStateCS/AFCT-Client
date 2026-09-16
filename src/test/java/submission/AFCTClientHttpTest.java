package submission;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The client's wire contract, exercised against a real loopback HTTP server: what
 * the multipart upload actually contains, how error statuses map to messages, and
 * the 404-means-older-server fallback on /auth/methods, which is the compatibility
 * contract between two separately released products.
 */
class AFCTClientHttpTest {

    private HttpServer server;
    private AFCTClient client;
    /** The last request per path: method, content type, raw body. */
    private final Map<String, Recorded> requests = new ConcurrentHashMap<>();

    private record Recorded(String method, String contentType, byte[] body) {}

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.start();
        client = new AFCTClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void respond(String path, int status, String contentType, String body) {
        server.createContext(path, exchange -> {
            requests.put(path, new Recorded(
                    exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestBody().readAllBytes()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private void respondWithRetryAfter(String path, int status, String seconds, String body) {
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Retry-After", seconds);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private void authenticate() throws IOException {
        respond("/api/client/v1/auth/login", 200, "application/json",
                "{\"token\":\"tok-1\",\"expiresAt\":\"2026-10-01T00:00:00Z\","
                        + "\"user\":{\"id\":\"u1\",\"email\":\"a@b.c\"}}");
        client.login("a@b.c", "pw");
    }

    // ── uploads ──────────────────────────────────────────────────────────────

    @Test
    void createSubmissionSendsAMultipartBodyWithFieldsAndFileBytes() throws Exception {
        authenticate();
        respond("/api/client/v1/submissions", 202, "application/json",
                "{\"submissionId\":\"s1\",\"status\":\"PENDING\"}");

        File jff = Files.createTempFile("afct-http-test", ".jff").toFile();
        try {
            Files.writeString(jff.toPath(), "<structure>dfa-bytes</structure>");
            ApiModels.CreateResult result = client.createSubmission("c1", "a1", "p1", jff);

            assertEquals("s1", result.submissionId());
            assertEquals("PENDING", result.status());

            Recorded req = requests.get("/api/client/v1/submissions");
            assertEquals("POST", req.method());
            assertTrue(req.contentType().startsWith("multipart/form-data; boundary="), req.contentType());
            String body = new String(req.body(), StandardCharsets.UTF_8);
            assertTrue(body.contains("name=\"assignmentId\"") && body.contains("a1"), "assignmentId field");
            assertTrue(body.contains("name=\"problemId\"") && body.contains("p1"), "problemId field");
            assertTrue(body.contains("filename=\"" + jff.getName() + "\""), "file part with its name");
            assertTrue(body.contains("<structure>dfa-bytes</structure>"), "the file's bytes");
        } finally {
            Files.deleteIfExists(jff.toPath());
        }
    }

    @Test
    void cooldownAndLimitResponsesBecomeReadableMessages() throws Exception {
        authenticate();
        respondWithRetryAfter("/api/client/v1/submissions", 429, "45", "{}");
        IOException cooldown = assertThrows(IOException.class,
                () -> client.createSubmission("c1", "a1", "p1", null));
        assertTrue(cooldown.getMessage().contains("45 seconds"), cooldown.getMessage());
    }

    @Test
    void submissionLimitErrorBodyIsSurfaced() throws Exception {
        authenticate();
        respond("/api/client/v1/submissions", 409, "application/json",
                "{\"error\":\"Submission limit reached for this problem.\"}");
        IOException limit = assertThrows(IOException.class,
                () -> client.createSubmission("c1", "a1", "p1", null));
        assertTrue(limit.getMessage().contains("Submission limit reached"), limit.getMessage());
    }

    // ── history ──────────────────────────────────────────────────────────────

    @Test
    void groupHistoryRowsParseWithSubmittedBy() throws Exception {
        authenticate();
        respond("/api/client/v1/submissions", 200, "application/json",
                "{\"submissions\":[{\"id\":\"s1\",\"status\":\"COMPLETED\",\"correct\":true,"
                        + "\"submittedAt\":\"2026-09-10T12:00:00Z\",\"fileName\":\"dfa.jff\","
                        + "\"feedback\":null,\"submittedBy\":\"Ada Lovelace\"}]}");

        List<ApiModels.Submission> subs = client.getSubmissions("a1", "p1");
        assertEquals(1, subs.size());
        assertEquals("Ada Lovelace", subs.get(0).submittedBy());
        assertEquals(Boolean.TRUE, subs.get(0).correct());
    }

    @Test
    void unknownFieldsFromANewerServerAreIgnored() throws Exception {
        // The server releases separately and may add fields first.
        authenticate();
        respond("/api/client/v1/submissions", 200, "application/json",
                "{\"submissions\":[{\"id\":\"s1\",\"status\":\"COMPLETED\","
                        + "\"brandNewField\":{\"nested\":1}}],\"alsoNew\":true}");
        assertEquals("s1", client.getSubmissions("a1", "p1").get(0).id());
    }

    // ── discovery fallback ───────────────────────────────────────────────────

    @Test
    void authMethods404MeansAnOlderServerNotAnError() throws Exception {
        respond("/api/client/v1/auth/methods", 404, "text/html",
                "<!doctype html><h1>404 - This page could not be found</h1>");
        AFCTClient.AuthMethods methods = client.getAuthMethods();
        assertFalse(methods.oidcEnabled());
        assertNull(methods.oidcButtonLabel());
    }

    @Test
    void authMethodsParsesAnOidcOffer() throws Exception {
        respond("/api/client/v1/auth/methods", 200, "application/json",
                "{\"oidc\":{\"enabled\":true,\"buttonLabel\":\"Sign in with PSU\"}}");
        AFCTClient.AuthMethods methods = client.getAuthMethods();
        assertTrue(methods.oidcEnabled());
        assertEquals("Sign in with PSU", methods.oidcButtonLabel());
    }

    // ── auth errors ──────────────────────────────────────────────────────────

    @Test
    void badCredentialsReadAsInvalidEmailOrPassword() {
        respond("/api/client/v1/auth/login", 401, "application/json", "{\"error\":\"nope\"}");
        IOException ex = assertThrows(IOException.class, () -> client.login("a@b.c", "wrong"));
        assertTrue(ex.getMessage().contains("Invalid email or password"), ex.getMessage());
    }

    @Test
    void aRejectedTokenLeavesTheClientUnauthenticated() throws Exception {
        respond("/api/client/v1/auth/me", 401, "application/json", "{\"error\":\"unknown token\"}");
        assertNull(client.loginWithToken("dead-token"));
        assertFalse(client.isAuthenticated(),
                "A refused token must be dropped, not resent on later calls");
    }
}
