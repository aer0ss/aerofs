package com.aerofs.testlib;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Generate a temporary cert suitable for use by the RestService
 *
 * Also export it into a temporary keystore for use by HTTPClient
 */
public class TempCert implements IPrivateKeyProvider, ICertificateProvider
{
    // for some reason that eludes me HTTPClient barfs when given
    // a keystore with an empty password
    public static final String KS_PASSWD = "foo";

    public final PrivateKey key;
    public final X509Certificate cert;
    public final String keyStore;

    private TempCert(PrivateKey k, Certificate c, String ks)
    {
        key = k;
        cert = (X509Certificate)c;
        keyStore = ks;
    }

    public void cleanup()
    {
        if (keyStore != null) new File(keyStore).delete();
    }

    @Override
    public @Nonnull PrivateKey getPrivateKey() throws IOException
    {
        return key;
    }

    @Override
    public @Nonnull Certificate getCert() throws CertificateException, IOException
    {
        return cert;
    }

    public static TempCert generateCA()
    {
        return generate("ca", null);
    }

    public static TempCert generateDaemon(UserID user, DID did, TempCert ca)
    {
        return generate(BaseSecUtil.getCertificateCName(user, did), ca);
    }

    public static TempCert generate(String subject, TempCert issuer)
    {
        try {
            SecureRandom rand = new SecureRandom();
            KeyPair k = SecTestUtil.generateKeyPairNoCheckedThrow(rand);

            Certificate cert = SecTestUtil.generateCertificate(
                    issuer == null ? subject : issuer.cert.getSubjectDN().toString().replace("CN=", ""),
                    subject,
                    k.getPublic(),
                    issuer == null ? k.getPrivate() : issuer.key,
                    rand,
                    issuer == null);

            String keyStore = null;
            // need a keystore for the CA
            if (issuer == null) {
                KeyStore ks = KeyStore.getInstance("JKS");
                ks.load(null, KS_PASSWD.toCharArray());
                ks.setCertificateEntry("rest_cert", cert);

                // sigh, HTTPClient (used by rest-assured) only accepts a path...
                File tmp = File.createTempFile("tmpkeystore", null);
                ks.store(new FileOutputStream(tmp), KS_PASSWD.toCharArray());
                keyStore = tmp.getAbsolutePath();
            }

            return new TempCert(k.getPrivate(), cert, keyStore);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
