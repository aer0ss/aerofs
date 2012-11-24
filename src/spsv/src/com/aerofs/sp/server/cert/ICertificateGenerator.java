/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.cert;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UserID;
import sun.security.pkcs.PKCS10;

import java.io.IOException;
import java.security.SignatureException;
import java.security.cert.CertificateException;

/**
 * This interface is only necessary to enable mocking in SPService.
 */
public interface ICertificateGenerator
{
    /**
     * Create a new certificate for the given user, device and CSR bytes.
     *
     * @param userId the user ID of this session for this certificate.
     * @param did the device ID for this certificate.
     * @param csr the certificate signing request bytes.
     * @return the newly generated certificate.
     * @throws IOException when there is an internal error creating the certificate.
     * @throws SignatureException when there is a problem with the given CSR.
     */
    public Certificate createCertificate(UserID userId, DID did, PKCS10 csr)
        throws IOException, SignatureException, CertificateException;
}
