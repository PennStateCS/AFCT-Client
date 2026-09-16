package submission;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.prefs.Preferences;

/**
 * Certificate pins for trust-on-first-use: origin (scheme+host+port) mapped to the
 * SHA-256 fingerprint of the server's leaf certificate. A pin is only consulted for
 * a chain the platform does not already trust, so an institution with a real
 * certificate never creates one.
 */
public class CertificatePins {

    private static final String KEY_PREFIX = "cert_pin_";

    private final Preferences preferences;

    public CertificatePins(Preferences preferences) {
        this.preferences = preferences;
    }

    public static CertificatePins defaultStore() {
        return new CertificatePins(Preferences.userNodeForPackage(CertificatePins.class));
    }

    /** The pinned fingerprint for this origin, or null when none is stored. */
    public String get(String origin) {
        return preferences.get(KEY_PREFIX + origin, null);
    }

    public void pin(String origin, String fingerprint) {
        preferences.put(KEY_PREFIX + origin, fingerprint);
    }

    public void forget(String origin) {
        preferences.remove(KEY_PREFIX + origin);
    }

    /** Lowercase hex SHA-256 of the certificate's DER encoding. */
    public static String fingerprintOf(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | CertificateEncodingException ex) {
            // SHA-256 always exists; an unencodable certificate cannot be pinned.
            throw new IllegalStateException("Unable to fingerprint certificate", ex);
        }
    }

    /** The fingerprint formatted for showing to a person: uppercase colon pairs. */
    public static String displayFingerprint(String fingerprint) {
        StringBuilder out = new StringBuilder(fingerprint.length() + fingerprint.length() / 2);
        for (int i = 0; i < fingerprint.length(); i += 2) {
            if (i > 0) out.append(':');
            out.append(Character.toUpperCase(fingerprint.charAt(i)));
            out.append(Character.toUpperCase(fingerprint.charAt(i + 1)));
        }
        return out.toString();
    }
}
