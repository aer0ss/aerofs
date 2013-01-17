/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class CRLBasedTrustManager implements X509TrustManager
{
    private final Logger l = Loggers.getLogger(CRLBasedTrustManager.class);
    private CRL _crl;

    CRLBasedTrustManager(CRL crl)
    {
        this._crl = crl;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
        return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException
    {
        if (chain.length != 1) {
            throw new CertificateException("expect length 1 chain, " +
                    "actual length: " + chain.length);
        }

        long serial = chain[0].getSerialNumber().longValue();
        l.info("check status of cert, serial#: " + serial);

        if (_crl.contains(serial)) {
            throw new CertificateException("cert has been revoked, serial#: " + serial);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException
    {
        assert false : ("should never be used for server->server communication");
    }
}
