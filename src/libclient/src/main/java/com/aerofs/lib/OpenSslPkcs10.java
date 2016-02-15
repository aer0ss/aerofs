/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.swig.pkcs10.PKCS10;
import com.aerofs.swig.pkcs10.SWIGTYPE_p__req_context;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * A shallow reimplementation of a class to create PKCS10 requests.
 *
 * Calls the (already-shipped) OpenSSL library routines to produce a request, rather
 * than depending on the implementation detail of sun.security.
 */
public class OpenSslPkcs10
{
    private final byte[] _derEncodedReq;
    private static final int LINE_LENGTH = 76;
    public static OpenSslPkcs10 create(PublicKey pubKey, PrivateKey privKey,
            UserID id, DID did)
            throws GeneralSecurityException
    {
        return new OpenSslPkcs10(privKey, pubKey, id, did);
    }

    private OpenSslPkcs10(PrivateKey privKey, PublicKey pubKey, UserID id, DID did)
            throws GeneralSecurityException
    {
        _derEncodedReq = getDerEncodedPKCS10Request(pubKey, privKey, id, did);
    }

    public byte[] getEncoded()
    {
        return _derEncodedReq;
    }

    public void print(PrintStream ps)
            throws IOException
    {
        String b64EncodedDer = Base64.encodeBytes(_derEncodedReq);
        ps.print("-----BEGIN NEW CERTIFICATE REQUEST-----\n");
        for (int i = 0; i < b64EncodedDer.length(); i += LINE_LENGTH)
        {
            int endIndex = Math.min(b64EncodedDer.length(), i + LINE_LENGTH);
            ps.print(b64EncodedDer.substring(i, endIndex));
            ps.print("\n");
        }
        ps.print("-----END NEW CERTIFICATE REQUEST-----\n");
    }

    private static byte[] getDerEncodedPKCS10Request(PublicKey pubKey, PrivateKey privKey,
            UserID id, DID did)
            throws GeneralSecurityException
    {
        // I'd like to refactor this to be more OO, but since I don't have destructors,
        // I'm not sure how I can actually avoid leaking resources in C land without wrapping
        // everything in a try/finally that frees the context as below.

        // I'd prefer not to change the interface to require RSA everywhere when other
        // implementations could be fine with other types of keys, but we currently use
        // RSA for all our certs, so this is sufficient.
        // Additional backends could be worthwhile in the future, if we want to support
        // elliptic curve signatures or something more future-proof.
        Preconditions.checkArgument(privKey instanceof RSAPrivateKey);
        Preconditions.checkArgument(pubKey instanceof RSAPublicKey);

        // Prepare all arguments
        RSAPrivateKey rsaPrivKey = ((RSAPrivateKey)privKey);
        byte[] modulusBytes = rsaPrivKey.getModulus().toByteArray();
        byte[] privKeyBytes = rsaPrivKey.getPrivateExponent().toByteArray();
        byte[] pubKeyBytes = ((RSAPublicKey)pubKey).getPublicExponent().toByteArray();
        byte[] cnameBytes = BaseUtil.string2utf(BaseSecUtil.getCertificateCName(id, did));

        SWIGTYPE_p__req_context context = PKCS10.req_init();
        if (context == null) {
            throw new GeneralSecurityException("PKCS10: Couldn't initialize request context");
        }
        try {
            int res;
            res = PKCS10.req_set_modulus(context, modulusBytes, modulusBytes.length);
            checkResult(res, "set modulus");
            PKCS10.req_set_private_exponent(context, privKeyBytes, privKeyBytes.length);
            checkResult(res, "set private exponent");
            PKCS10.req_set_public_exponent(context, pubKeyBytes, pubKeyBytes.length);
            checkResult(res, "set public exponent");
            PKCS10.req_set_cname(context, cnameBytes, cnameBytes.length);
            checkResult(res, "set cname");
            int reqSize = PKCS10.req_sign(context);
            if (reqSize < 0) throw new GeneralSecurityException("PKCS10: Failed to sign CSR");
            byte[] derEncodedRequest = new byte[reqSize];
            PKCS10.req_get_csr_bytes(context, derEncodedRequest, derEncodedRequest.length);
            return derEncodedRequest;
        } finally {
            PKCS10.req_free(context);
        }
    }

    private static void checkResult(int res, String check)
            throws GeneralSecurityException
    {
        if (res != 0) throw new GeneralSecurityException("PKCS10: Couldn't " + check);
    }
}
