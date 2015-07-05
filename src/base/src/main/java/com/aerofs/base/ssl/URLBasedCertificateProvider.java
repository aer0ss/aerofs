/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.ssl;

import com.aerofs.base.BaseSecUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class URLBasedCertificateProvider implements ICertificateProvider
{
    private X509Certificate _cert;
    private final String _url;

    // server-only
    public static URLBasedCertificateProvider server() {
        return new URLBasedCertificateProvider("http://ca.service:9002/prod/cacert.pem");
    }

    public URLBasedCertificateProvider(String url)
    {
        _url = url;
    }

    @Nonnull
    @Override
    public X509Certificate getCert()
            throws CertificateException, IOException
    {
        if (_cert == null) {
            HttpURLConnection c = (HttpURLConnection)(new URL(_url).openConnection());
            c.connect();
            if (c.getResponseCode() != 200) {
                throw new IOException("unexpected response: " + c.getResponseCode());
            }
            try (InputStream is = c.getInputStream()) {
                _cert = BaseSecUtil.newCertificateFromStream(is);
            }
        }

        return _cert;
    }
}
