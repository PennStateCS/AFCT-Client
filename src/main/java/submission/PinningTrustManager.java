package submission;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Per-connection trust-on-first-use. Decision order, which is load-bearing:
 *
 * 1. Platform trust (OS store, then JVM default): accept, and silently drop any
 *    stale pin. This is the documented upgrade path: a professor who replaces the
 *    self-signed certificate with a real one must not brick every client.
 * 2. A pin for this origin matching the leaf's SHA-256: accept. The hostname
 *    verifier is scoped to this case, because a self-signed CN rarely matches how
 *    a student typed the address.
 * 3. No pin: refuse as UNTRUSTED, keeping the chain so the login window can ask
 *    the student once, then pin.
 * 4. A pin that does not match: refuse as CHANGED. Never silently re-pin: a changed
 *    fingerprint that the platform does not vouch for is the one situation pinning
 *    exists to catch.
 *
 * Never installed JVM-wide (no SSLContext.setDefault): one instance serves one
 * connection to one origin.
 */
public class PinningTrustManager implements X509TrustManager {

    public enum Failure { UNTRUSTED, CHANGED }

    /** Injectable for tests; production uses SystemTrust. */
    public interface PlatformTrust {
        boolean isTrusted(X509Certificate[] chain, String authType);
    }

    private final String origin;
    private final CertificatePins pins;
    private final PlatformTrust platformTrust;

    private volatile boolean acceptedByPin = false;
    private volatile Failure failure = null;
    private volatile X509Certificate[] rejectedChain = null;

    public PinningTrustManager(String origin, CertificatePins pins, PlatformTrust platformTrust) {
        this.origin = origin;
        this.pins = pins;
        this.platformTrust = platformTrust;
    }

    public static PinningTrustManager forOrigin(String origin) {
        return new PinningTrustManager(origin, CertificatePins.defaultStore(), SystemTrust::isTrusted);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException("Empty certificate chain.");
        }

        if (platformTrust.isTrusted(chain, authType)) {
            if (pins.get(origin) != null) {
                // The server graduated to a certificate the platform trusts.
                pins.forget(origin);
            }
            return;
        }

        String fingerprint = CertificatePins.fingerprintOf(chain[0]);
        String pinned = pins.get(origin);
        if (pinned == null) {
            failure = Failure.UNTRUSTED;
            rejectedChain = chain;
            throw new CertificateException("Certificate is not trusted for " + origin);
        }
        if (pinned.equalsIgnoreCase(fingerprint)) {
            acceptedByPin = true;
            return;
        }
        failure = Failure.CHANGED;
        rejectedChain = chain;
        throw new CertificateException("Certificate changed for " + origin);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("Client certificates are not used.");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    /** True when this connection was accepted by pin rather than platform trust. */
    public boolean acceptedByPin() {
        return acceptedByPin;
    }

    /** Why the handshake was refused, or null when it was not. */
    public Failure failure() {
        return failure;
    }

    /** The chain that was refused, kept so a dialog can show and pin it. */
    public X509Certificate[] rejectedChain() {
        return rejectedChain;
    }
}
