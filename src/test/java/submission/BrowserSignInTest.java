package submission;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the real loopback listener: these tests make actual HTTP requests to
 * 127.0.0.1, because the listener's handling of state and error responses is the
 * security boundary and a mock would only prove the mock.
 */
class BrowserSignInTest {

    private static int hit(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int status = conn.getResponseCode();
        conn.getInputStream().readAllBytes();
        return status;
    }

    private static String stateFrom(BrowserSignIn flow) {
        // The state is not exposed directly; read it back out of the authorize URL
        // the way the server would.
        String url = flow.authorizeUrl("https://afct.example.edu", null);
        for (String part : url.substring(url.indexOf('?') + 1).split("&")) {
            if (part.startsWith("state=")) {
                return java.net.URLDecoder.decode(part.substring("state=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("No state in authorize URL: " + url);
    }

    @Test
    void redirectUriIsLoopbackHttp() throws Exception {
        try (BrowserSignIn flow = new BrowserSignIn()) {
            assertTrue(flow.redirectUri().matches("http://127\\.0\\.0\\.1:\\d+/callback"),
                    flow.redirectUri());
        }
    }

    @Test
    void authorizeUrlCarriesTheRequestAndEncodesTheDeviceName() throws Exception {
        try (BrowserSignIn flow = new BrowserSignIn()) {
            String url = flow.authorizeUrl("https://afct.example.edu", "Jeff's PC");
            assertTrue(url.startsWith("https://afct.example.edu/client-auth?redirect_uri="));
            assertTrue(url.contains("&code_challenge_method=S256"));
            assertTrue(url.contains("device_name=" + URLEncoder.encode("Jeff's PC", StandardCharsets.UTF_8)));
        }
    }

    @Test
    void deliversTheCodeFromARedirectWithTheRightState() throws Exception {
        try (BrowserSignIn flow = new BrowserSignIn()) {
            String state = stateFrom(flow);
            CompletableFuture<String> got = CompletableFuture.supplyAsync(() -> {
                try {
                    return flow.awaitCode(Duration.ofSeconds(10));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            assertEquals(200, hit(flow.redirectUri() + "?code=abc123&state="
                    + URLEncoder.encode(state, StandardCharsets.UTF_8)));
            assertEquals("abc123", got.get());
        }
    }

    @Test
    void ignoresARedirectWithTheWrongState() throws Exception {
        // A hostile local process spraying the port must not complete the flow.
        try (BrowserSignIn flow = new BrowserSignIn()) {
            assertEquals(200, hit(flow.redirectUri() + "?code=evil&state=wrong"));
            assertThrows(IOException.class, () -> flow.awaitCode(Duration.ofMillis(300)),
                    "A wrong-state hit must leave the flow waiting, not complete it");
        }
    }

    @Test
    void aDeclineInTheBrowserFailsTheWait() throws Exception {
        try (BrowserSignIn flow = new BrowserSignIn()) {
            String state = stateFrom(flow);
            hit(flow.redirectUri() + "?error=access_denied&state="
                    + URLEncoder.encode(state, StandardCharsets.UTF_8));
            IOException ex = assertThrows(IOException.class,
                    () -> flow.awaitCode(Duration.ofSeconds(5)));
            assertTrue(ex.getMessage().contains("declined"), ex.getMessage());
        }
    }

    @Test
    void cancelAbortsTheWait() throws Exception {
        try (BrowserSignIn flow = new BrowserSignIn()) {
            flow.cancel();
            IOException ex = assertThrows(IOException.class,
                    () -> flow.awaitCode(Duration.ofSeconds(5)));
            assertTrue(ex.getMessage().contains("cancelled"), ex.getMessage());
        }
    }

    @Test
    void verifierMatchesTheChallengeInTheUrl() throws Exception {
        try (BrowserSignIn flow = new BrowserSignIn()) {
            String url = flow.authorizeUrl("https://afct.example.edu", null);
            String challenge = null;
            for (String part : url.substring(url.indexOf('?') + 1).split("&")) {
                if (part.startsWith("code_challenge=")) {
                    challenge = java.net.URLDecoder.decode(
                            part.substring("code_challenge=".length()), StandardCharsets.UTF_8);
                }
            }
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(flow.verifier().getBytes(StandardCharsets.US_ASCII));
            assertEquals(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest),
                    challenge);
        }
    }
}
