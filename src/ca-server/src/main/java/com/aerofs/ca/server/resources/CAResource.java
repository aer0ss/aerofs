/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.server.resources;

import com.aerofs.auth.server.Roles;
import com.aerofs.base.Loggers;
import com.aerofs.ca.database.CADatabase;
import com.aerofs.ca.utils.CertificateSigner;
import com.aerofs.ca.utils.CertificateUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Random;

import static com.google.common.base.Strings.isNullOrEmpty;

@Path("/prod")
public class CAResource
{
    private static final Logger l = Loggers.getLogger(CAResource.class);
    private final CADatabase _db;
    private final CertificateSigner _certificateSigner;
    private final Random _rng;

    private static final int UNUSED_SERIAL_MAX_ITERATIONS = 10;

    public CAResource(@Context CADatabase db, @Context CertificateSigner certificateSigner)
    {
        this._rng = new Random();
        this._db = db;
        this._certificateSigner = certificateSigner;
    }

    @RolesAllowed(Roles.SERVICE)
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    // consumes all media-types (an unspecified one)
    public byte[] createCertificate(byte[] csr, @Context UriInfo uriInfo)
            throws InvalidCSRException, IOException, OperatorCreationException, CertificateException
    {
        String service = uriInfo.getRequestUri().getQuery();
        if (isNullOrEmpty(service)) {
            throw new InvalidCSRException("request must include service name as query parameter");
        }
        PKCS10CertificationRequest pkcs;
        try {
            pkcs = CertificateUtils.pemToCSR(csr);
        } catch (IOException e) {
            throw new InvalidCSRException();
        }

        long serialNo =  getUnusedSerialNumber();
        X509CertificateHolder signedCert = _certificateSigner.signCSR(pkcs, serialNo);
        _db.addSignedCertificate(serialNo, signedCert);
        return CertificateUtils.x509ToPEM(signedCert);
    }

    private long getUnusedSerialNumber()
            throws CertificateException
    {
        // JDBC can't insert signed longs into an unsigned BIGINT field, so we take the absolute value
        // to ensure that the serialNo isn't interpreted as a negative value
        long serialNo = Math.abs(_rng.nextLong());
        int iterations = 0;
        while (!_db.takeSerialNumberIfUnused(serialNo) && iterations < UNUSED_SERIAL_MAX_ITERATIONS) {
            serialNo = Math.abs(_rng.nextLong());
            iterations++;
        }

        if (iterations == UNUSED_SERIAL_MAX_ITERATIONS) {
            // could not find an unused serial number
            throw new CertificateException("could not provision a serial number for this csr");
        }
        return serialNo;
    }

    @GET
    @Path("/cacert.pem")
    @Produces(MediaType.TEXT_PLAIN)
    //consumes all media types
    public byte[] getCACertificate()
            throws IOException, CertificateException
    {
        return CertificateUtils.x509ToPEM(_certificateSigner.caCert());
    }

}
