/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.cert;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.IOException;
import java.security.SignatureException;
import java.security.cert.CertificateException;

/**
 * This interface is only necessary to enable mocking in SPService.
 * TODO (WW) use concrete object mocking and remove this interface
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
    public CertificationResult generateCertificate(UserID userId, DID did,
            PKCS10CertificationRequest csr)
        throws IOException, SignatureException, CertificateException;
}
