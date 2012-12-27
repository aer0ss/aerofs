package com.aerofs.lib;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.os.OSUtil;
import sun.security.pkcs.PKCS10;
import sun.security.x509.X500Name;
import sun.security.x509.X500Signer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class SecUtil extends BaseSecUtil
{
    private SecUtil()
    {
        // private to enforce uninstantiability
    }

    public static byte[] scrypt(char[] passwd, UserID user)
    {
        OSUtil.get().loadLibrary("aerofsd");
        return scryptImpl(passwd, user);
    }

    public static PKCS10 newCSR(PublicKey pubKey, PrivateKey privKey, UserID userId, DID did)
            throws GeneralSecurityException, IOException
    {
        PKCS10 request = new PKCS10(pubKey);

        Signature signature = Signature.getInstance(SHA1_WITH_RSA);
        signature.initSign(privKey);

        X500Name subject = new X500Name(getCertificateCName(userId, did),
                ORGANIZATION_UNIT,
                ORGANIZATION_NAME,
                LOCALITY_NAME,
                STATE_NAME,
                COUNTRY_NAME);

        // In JDK 1.7 class X500Signer doesn't exists and encodeAndSign() method
        // has changed that takes Signature and X500Name as params instead of
        // X500Signer. So catch the error and use reflection method to invoke
        // the new encodeAndSign() method.
        try {
            X500Signer signer = new X500Signer(signature, subject);

            request.encodeAndSign(signer);

        } catch (NoClassDefFoundError noClassErr) {
            try {
                Class<?> requestClass = request.getClass();

                Class<?> params[] = new Class[2];
                params[0] = X500Name.class;
                params[1] = Signature.class;

                Method encodeAndSignMethod = requestClass.getDeclaredMethod(
                        "encodeAndSign", params);
                encodeAndSignMethod.invoke(request, subject, signature);
            } catch(Exception e) {
                throw new GeneralSecurityException(e);
            }
        }

        return request;
    }
}
