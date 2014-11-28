/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.ssl;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static com.google.common.base.Preconditions.checkArgument;

public class CRLBasedTrustManager implements X509TrustManager
{
    private final X509TrustManager _realTrustManager;
    private final CRL _crl;

    CRLBasedTrustManager(X509TrustManager realTrustManager, CRL crl)
    {
        _realTrustManager = realTrustManager;
        _crl = crl;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
        X509Certificate[] acceptedIssuers = _realTrustManager.getAcceptedIssuers();
        checkArgument(acceptedIssuers.length != 0, "trying to create a trust manager without a CA");
        return acceptedIssuers;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException
    {
        _realTrustManager.checkClientTrusted(chain, authType);
        checkRevoked(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException
    {
        _realTrustManager.checkServerTrusted(chain, authType);
        checkRevoked(chain);
    }

    private void checkRevoked(X509Certificate[] chain) throws CertificateException
    {
        if (chain.length != 1) {
            throw new CertificateException("expect length 1 chain, actual length: " + chain.length);
        }

        long serial = chain[0].getSerialNumber().longValue();
        if (_crl.contains(serial)) {
            throw new CertificateException("cert has been revoked, serial#: " + serial);
        }
    }
}
