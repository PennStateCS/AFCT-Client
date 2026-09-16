package submission;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * One parsed server address, backing the login window's single Server address field.
 * Replaces the scheme-stripping that used to be smeared across AFCTClient.fixUrl,
 * the AFCTClient constructor and SessionHandler.login.
 *
 * Rules: no scheme means https; the port is optional and defaults to the scheme's;
 * any path, query, fragment or trailing slash is dropped. baseUrl carries the port
 * only when it differs from the scheme's default.
 */
public record ServerAddress(String scheme, String host, int port, String baseUrl) {

    /**
     * @throws IllegalArgumentException with a message fit to show the user.
     */
    public static ServerAddress parse(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Server address is required.");
        }
        boolean hasHttp = trimmed.regionMatches(true, 0, "http://", 0, "http://".length());
        boolean hasHttps = trimmed.regionMatches(true, 0, "https://", 0, "https://".length());
        if (!hasHttp && !hasHttps) {
            trimmed = "https://" + trimmed;
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("That server address is not valid.");
        }

        String host = uri.getHost();
        // getHost() is null for addresses URI could split but not make sense of
        // (say "https://:3000"), and userinfo has no business in a server address.
        if (host == null || host.isBlank() || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("That server address is not valid.");
        }

        String scheme = uri.getScheme().toLowerCase();
        int defaultPort = "https".equals(scheme) ? 443 : 80;
        int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
        String baseUrl = scheme + "://" + host + (port == defaultPort ? "" : ":" + port);
        return new ServerAddress(scheme, host, port, baseUrl);
    }

    /**
     * Whether the host is this machine. localhost counts here: this gates whether
     * plain http is tolerable for a dev server, not where a sign-in redirect may
     * land, and banning it would break every http://localhost:3000 dev setup.
     */
    public boolean isLoopback() {
        String h = host.toLowerCase();
        return h.equals("localhost") || h.equals("127.0.0.1")
                || h.equals("::1") || h.equals("[::1]");
    }
}
