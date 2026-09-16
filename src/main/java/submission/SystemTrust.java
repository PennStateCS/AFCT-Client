package submission;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers "does the platform already trust this chain". The OS trust store is asked
 * before the JVM's cacerts on purpose: a campus that pushes a private CA by GPO or
 * MDM puts it there, where Java's own store cannot see it, and finding it means a
 * student at that campus is never prompted at all.
 */
public class SystemTrust {

    private static volatile List<X509TrustManager> managers;

    /** True when any platform or JVM trust manager accepts the chain. */
    public static boolean isTrusted(X509Certificate[] chain, String authType) {
        for (X509TrustManager tm : trustManagers()) {
            try {
                tm.checkServerTrusted(chain, authType);
                return true;
            } catch (Exception ignored) {
                // Not trusted by this store; try the next.
            }
        }
        return false;
    }

    private static List<X509TrustManager> trustManagers() {
        List<X509TrustManager> loaded = managers;
        if (loaded != null) return loaded;
        synchronized (SystemTrust.class) {
            if (managers == null) {
                managers = load();
            }
            return managers;
        }
    }

    private static List<X509TrustManager> load() {
        List<X509TrustManager> result = new ArrayList<>();
        // OS stores exist only on their own platform; a missing type is expected.
        for (String type : new String[]{"Windows-ROOT", "KeychainStore"}) {
            try {
                KeyStore store = KeyStore.getInstance(type);
                store.load(null, null);
                addManagerFor(result, store);
            } catch (Exception ignored) {
            }
        }
        try {
            // null KeyStore = the JVM default (cacerts).
            addManagerFor(result, null);
        } catch (Exception ex) {
            System.err.println("[SystemTrust] Default trust store unavailable: " + ex.getMessage());
        }
        return result;
    }

    private static void addManagerFor(List<X509TrustManager> result, KeyStore store) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (var manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                result.add(x509);
            }
        }
    }
}
