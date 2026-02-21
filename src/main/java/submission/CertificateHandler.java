package submission;

import gui.Globals;

import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.io.FileInputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.KeyStore;

public class CertificateHandler {
    CertificateException cause;
    X509Certificate[] chain;
    String authType;

    public static void enableCustomCertificateValidation() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        // Source - https://stackoverflow.com/a/24561444
        // Posted by Bruno, modified by community. See post 'Timeline' for change history
        // Retrieved 2026-02-13, License - CC BY-SA 3.0
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // Using null here initialises the TMF with the default trust store.
        tmf.init((KeyStore) null);

        // Get hold of the default trust manager
        X509TrustManager defaultTm = null;
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                defaultTm = (X509TrustManager) tm;
                break;
            }
        }

//        FileInputStream myKeys = new FileInputStream("truststore.jks");
//
//        // Do the same with your trust store this time
//        // Adapt how you load the keystore to your needs
//        KeyStore myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType());
//        myTrustStore.load(myKeys, "password".toCharArray());
//
//        myKeys.close();
//
//        tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//        tmf.init(myTrustStore);

        // Get hold of the default trust manager
        X509TrustManager myTm = null;
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                myTm = (X509TrustManager) tm;
                break;
            }
        }

        // Wrap it in your own class.
        final X509TrustManager finalDefaultTm = defaultTm;
        final X509TrustManager finalMyTm = myTm;
        X509TrustManager customTm = new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                // If you're planning to use client-cert auth,
                // merge results from "defaultTm" and "myTm".
                return finalDefaultTm.getAcceptedIssuers();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain,
                                           String authType) throws CertificateException {
                try {
                    // Check default TrustManager (TM) first, then fallback to custom TM on failure
                    finalDefaultTm.checkServerTrusted(chain, authType);
                } catch (CertificateException e) {
                    // This will throw another CertificateException if this fails too.
                    try {
                        finalMyTm.checkServerTrusted(chain, authType);
                    } catch (CertificateException ex) {
                        Globals.sessionHandler.certificateHandler.setUnverifiedCertificate(ex, chain, authType);
                        throw ex;
                        //throw new AFCTCertificateException(ex, chain, authType);
                    }
                }
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain,
                                           String authType) throws CertificateException {
                // If you're planning to use client-cert auth,
                // do the same as checking the server.
                finalDefaultTm.checkClientTrusted(chain, authType);
            }
        };


        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[]{customTm}, null);

        // You don't have to set this as the default context,
        // it depends on the library you're using.
        SSLContext.setDefault(sslContext);

        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
    }

    private void setUnverifiedCertificate(CertificateException cause, X509Certificate[] chain, String authType) {
        this.cause = cause;
        this.chain = chain;
        this.authType = authType;
    }

    public X509Certificate[] getCertificateChain() {
        return chain;
    }

    public String getAuthType() {
        return authType;
    }

    public void test() {
        for (X509Certificate c : chain) {
            X500Principal principal = c.getSubjectX500Principal();
            System.out.println(principal);
            System.out.println(principal.getName());
        }
    }
}
