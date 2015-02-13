/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class SecUtilHelper extends BaseSecUtil
{
    /**
     * This method exists because we don't want to depend on the sun JRE's implementation
     * detail sun.security.pkcs.PKCS10, because doing so triggers a warning and compiling
     * with -Werror is good for the soul.
     *
     * On the client, we can use a thin wrapper around OpenSSL, for which we already ship the native
     * library, so we might as well use a 40KB wrapper instead of shipping 2MB of crypto
     * implementation for a PKCS10 generator.
     *
     * On servers, we ship two bouncycastle jars, since we don't want to depend on the aerofsd
     * native library.
     */
    public static PKCS10CertificationRequest serverOnlyNewCSR(PublicKey pubKey, PrivateKey privKey,
            UserID userId, DID did)
            throws GeneralSecurityException, IOException
    {
        ContentSigner cs;
        try {
            cs = new JcaContentSignerBuilder(SHA1_WITH_RSA).build(privKey);
        } catch (OperatorCreationException e) {
            throw new GeneralSecurityException(e);
        }
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.CN, getCertificateCName(userId, did))
                .addRDN(BCStyle.OU, ORGANIZATION_UNIT)
                .addRDN(BCStyle.O, ORGANIZATION_NAME)
                .addRDN(BCStyle.L, LOCALITY_NAME)
                .addRDN(BCStyle.ST, STATE_NAME)
                .addRDN(BCStyle.C, COUNTRY_NAME)
                .build();

        PKCS10CertificationRequestBuilder bd = new JcaPKCS10CertificationRequestBuilder(subject, pubKey);
        return bd.build(cs);
    }
}
