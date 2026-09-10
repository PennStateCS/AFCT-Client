package submission;

import java.time.Duration;

/**
 * Builder for creating AFCTClient instances with custom configuration.
 */
public final class AFCTClientBuilder {

    private final String baseUrl;

    private Duration connectTimeout = Duration.ofSeconds(15);
    private Duration readTimeout = Duration.ofSeconds(30);
    private int maxRetries = 3;
    private long baseBackoffMs = 300;

    /** @throws IllegalArgumentException when the address cannot be parsed. */
    public AFCTClientBuilder(String baseUrl) {
        this.baseUrl = ServerAddress.parse(baseUrl).baseUrl();
    }

    public AFCTClientBuilder connectTimeoutSeconds(int seconds) {
        this.connectTimeout = Duration.ofSeconds(seconds);
        return this;
    }

    public AFCTClientBuilder readTimeoutSeconds(int seconds) {
        this.readTimeout = Duration.ofSeconds(seconds);
        return this;
    }

    public AFCTClientBuilder maxRetries(int retries) {
        this.maxRetries = retries;
        return this;
    }

    public AFCTClientBuilder baseBackoffMs(long ms) {
        this.baseBackoffMs = ms;
        return this;
    }

    public AFCTClient build() {
        return new AFCTClient(
                baseUrl,
                connectTimeout,
                readTimeout,
                maxRetries,
                baseBackoffMs
        );
    }

    // Package-private getters for AFCTClient constructor
    String getBaseUrl() {
        return baseUrl;
    }

    Duration getConnectTimeout() {
        return connectTimeout;
    }

    Duration getReadTimeout() {
        return readTimeout;
    }

    int getMaxRetries() {
        return maxRetries;
    }

    long getBaseBackoffMs() {
        return baseBackoffMs;
    }
}

