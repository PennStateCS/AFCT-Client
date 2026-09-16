package submission;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One browser sign-in attempt: a loopback listener, a PKCE pair and a state value
 * (RFC 8252). The listener binds 127.0.0.1 explicitly, never localhost, because a
 * hosts file can point localhost off this machine, and the redirect URI must name
 * exactly what is bound. Plain http on the loopback is the RFC's own design: the
 * hop never leaves the machine and no certificate can exist for it.
 */
public class BrowserSignIn implements AutoCloseable {

    /** Institutional login plus MFA is minutes, not seconds. This wait is for the
     *  person; the server-side code TTL only starts once they click Allow. */
    public static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(10);

    private final HttpServer server;
    private final String redirectUri;
    private final String state;
    private final String verifier;
    private final String challenge;
    private final CompletableFuture<String> code = new CompletableFuture<>();

    public BrowserSignIn() throws IOException {
        // Port 0: the OS picks a free ephemeral port; the redirect URI repeats it.
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/callback";

        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        random.nextBytes(bytes);
        verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenge = s256(verifier);

        server.createContext("/callback", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String response;
            Exception exceptionOnComplete = null;
            if (!state.equals(params.get("state"))) {
                // Anything a local process throws at this port with the wrong state
                // is ignored outright; the flow keeps waiting for the real redirect.
                response = "This sign-in response was not expected. Return to AFCT and try again.";
            } else if (params.containsKey("error")) {
                response = "Sign-in was not completed. You can close this tab.";
                exceptionOnComplete = new IOException("The sign-in was declined in the browser.");
            } else if (params.get("code") != null && !params.get("code").isBlank()) {
                response = "Signed in. You can close this tab and return to AFCT.";
                code.complete(params.get("code"));
            } else {
                response = "This sign-in response was incomplete. Return to AFCT and try again.";
            }
            byte[] body = ("<!doctype html><meta charset=\"utf-8\"><title>AFCT</title><p>"
                    + response + "</p>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            if (exceptionOnComplete != null) {
                code.completeExceptionally(exceptionOnComplete);
            }
        });
        server.start();
    }

    /** The consent-page URL to open in the system browser. */
    public String authorizeUrl(String serverBaseUrl, String deviceName) {
        StringBuilder url = new StringBuilder(serverBaseUrl)
                .append("/client-auth?redirect_uri=").append(encode(redirectUri))
                .append("&state=").append(encode(state))
                .append("&code_challenge=").append(encode(challenge))
                .append("&code_challenge_method=S256");
        if (deviceName != null && !deviceName.isBlank()) {
            url.append("&device_name=").append(encode(deviceName));
        }
        return url.toString();
    }

    /**
     * Blocks until the browser redirect delivers a code. Throws on decline, on
     * timeout, and on {@link #cancel()}.
     */
    public String awaitCode(Duration timeout) throws IOException {
        try {
            return code.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new IOException("The browser sign-in was not completed in time. Try again.");
        } catch (CancellationException ex) {
            throw new IOException("Sign-in was cancelled.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Sign-in was interrupted.");
        } catch (ExecutionException ex) {
            throw ex.getCause() instanceof IOException io
                    ? io : new IOException(ex.getCause().getMessage());
        }
    }

    public void cancel() {
        code.cancel(true);
    }

    public String verifier() {
        return verifier;
    }

    public String redirectUri() {
        return redirectUri;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null) return params;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            params.put(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return params;
    }
}
