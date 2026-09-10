package submission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The trust-on-first-use decision table. Each case is checked against the store
 * state it leaves behind, since the pin store is what future connections trust.
 */
class PinningTrustManagerTest {

    private static final String ORIGIN = "https://afct.test.example";

    private Preferences node;
    private CertificatePins pins;

    @BeforeEach
    void setUp() throws Exception {
        node = Preferences.userRoot().node("afct-client-test-pins");
        node.clear();
        pins = new CertificatePins(node);
    }

    @AfterEach
    void tearDown() throws Exception {
        node.clear();
    }

    /** A fake certificate whose DER encoding is the given bytes. */
    private static X509Certificate cert(byte[] encoded) throws Exception {
        X509Certificate c = Mockito.mock(X509Certificate.class);
        Mockito.when(c.getEncoded()).thenReturn(encoded);
        return c;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void fingerprintIsSha256OfEncoding() throws Exception {
        byte[] der = {1, 2, 3, 4};
        assertEquals(sha256Hex(der), CertificatePins.fingerprintOf(cert(der)));
    }

    @Test
    void platformTrustedChainIsAcceptedAndDropsStalePin() throws Exception {
        pins.pin(ORIGIN, "stale-fingerprint");
        var tm = new PinningTrustManager(ORIGIN, pins, (chain, authType) -> true);

        assertDoesNotThrow(() -> tm.checkServerTrusted(new X509Certificate[]{cert(new byte[]{9})}, "RSA"));
        // The upgrade path: a server that graduated to a real certificate loses its pin.
        assertNull(pins.get(ORIGIN), "Stale pin must be dropped once the platform trusts the chain");
        assertFalse(tm.acceptedByPin());
    }

    @Test
    void unknownUntrustedChainIsRefusedAsUntrustedAndKeepsTheChain() throws Exception {
        var tm = new PinningTrustManager(ORIGIN, pins, (chain, authType) -> false);
        X509Certificate leaf = cert(new byte[]{5, 6});

        assertThrows(CertificateException.class,
                () -> tm.checkServerTrusted(new X509Certificate[]{leaf}, "RSA"));
        assertEquals(PinningTrustManager.Failure.UNTRUSTED, tm.failure());
        assertSame(leaf, tm.rejectedChain()[0]);
        assertNull(pins.get(ORIGIN), "Refusal must not pin anything by itself");
    }

    @Test
    void pinnedFingerprintIsAcceptedByPin() throws Exception {
        byte[] der = {7, 7, 7};
        pins.pin(ORIGIN, sha256Hex(der));
        var tm = new PinningTrustManager(ORIGIN, pins, (chain, authType) -> false);

        assertDoesNotThrow(() -> tm.checkServerTrusted(new X509Certificate[]{cert(der)}, "RSA"));
        assertTrue(tm.acceptedByPin(), "Pin acceptance is what scopes the hostname verifier");
    }

    @Test
    void changedFingerprintIsRefusedAsChangedAndKeepsThePin() throws Exception {
        pins.pin(ORIGIN, sha256Hex(new byte[]{1}));
        var tm = new PinningTrustManager(ORIGIN, pins, (chain, authType) -> false);

        assertThrows(CertificateException.class,
                () -> tm.checkServerTrusted(new X509Certificate[]{cert(new byte[]{2})}, "RSA"));
        assertEquals(PinningTrustManager.Failure.CHANGED, tm.failure());
        // Never silently re-pin: the old pin stays until the user chooses to forget it.
        assertEquals(sha256Hex(new byte[]{1}), pins.get(ORIGIN));
    }

    @Test
    void changedFingerprintNowPlatformTrustedIsAcceptedSilently() throws Exception {
        // The self-signed-to-real-certificate upgrade must never prompt or fail.
        pins.pin(ORIGIN, sha256Hex(new byte[]{1}));
        var tm = new PinningTrustManager(ORIGIN, pins, (chain, authType) -> true);

        assertDoesNotThrow(() -> tm.checkServerTrusted(new X509Certificate[]{cert(new byte[]{2})}, "RSA"));
        assertNull(tm.failure());
        assertNull(pins.get(ORIGIN));
    }

    @Test
    void emptyChainIsRefused() {
        var tm = new PinningTrustManager(ORIGIN, pins, (chain, authType) -> true);
        assertThrows(CertificateException.class, () -> tm.checkServerTrusted(new X509Certificate[0], "RSA"));
    }
}
