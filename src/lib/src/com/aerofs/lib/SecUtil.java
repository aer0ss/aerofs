package com.aerofs.lib;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.os.OSUtil;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

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

    // This method uses OpenSSL to generate a PKCS10-compatible certificate signing request.
    // It is intended for use within client code, where we already ship the aerofsd library.
    public static OpenSslPkcs10 newCSR(PublicKey pubKey, PrivateKey privKey, UserID userId, DID did)
            throws GeneralSecurityException
    {
        OSUtil.get().loadLibrary("aerofsd");
        return OpenSslPkcs10.create(pubKey, privKey, userId, did);
    }
}
