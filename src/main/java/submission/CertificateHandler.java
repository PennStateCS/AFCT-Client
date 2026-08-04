package submission;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

public class CertificateHandler {
    X509Certificate[] chain;
    String authType;

    /**
     * Installs a trust-all SSL context so self-signed server certificates are
     * accepted without throwing. Also disables hostname verification.
     */
    public static void enableCustomCertificateValidation() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // Accept all client certificates
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // Accept all server certificates (including self-signed)
                    System.out.println("[CertificateHandler] Accepting cert: "
                            + (chain != null && chain.length > 0 ? chain[0].getSubjectDN() : "unknown"));
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());

        SSLContext.setDefault(sslContext);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }

    public void setChain(X509Certificate[] chain, String authType) {
        this.chain = chain;
        this.authType = authType;
    }

    public X509Certificate[] getCertificateChain() {
        return chain;
    }
    // auth lol
    public String getAuthType() {
        return authType;
    }

    public void test() {
        if (chain == null) return;
        for (X509Certificate c : chain) {
            X500Principal principal = c.getSubjectX500Principal();
            System.out.println(principal);
            System.out.println(principal.getName());
        }
    }
}
