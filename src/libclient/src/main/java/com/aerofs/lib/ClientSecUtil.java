package com.aerofs.lib;

import com.aerofs.base.BaseSecUtil.KeyDerivation;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.scrypt.Scrypt;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class ClientSecUtil
{
    private ClientSecUtil()
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
            throws GeneralSecurityException, IOException
    {
        OSUtil.get().loadLibrary("aerofsd");
        return OpenSslPkcs10.create(pubKey, privKey, userId, did);
    }

    protected static byte[] scryptImpl(char[] passwd, UserID user)
    {
        byte[] bsPass = KeyDerivation.getPasswordBytes(passwd);
        byte[] bsUser = KeyDerivation.getSaltForUser(user);

        byte[] scrypted = new byte[KeyDerivation.dkLen];
        int rc = Scrypt.crypto_scrypt(bsPass, bsPass.length, bsUser, bsUser.length, KeyDerivation.N,
                KeyDerivation.r, KeyDerivation.p, scrypted, scrypted.length);

        // sanity checks
        if (rc != 0) throw new Error("scr rc != 0");

        return scrypted;
    }

}
