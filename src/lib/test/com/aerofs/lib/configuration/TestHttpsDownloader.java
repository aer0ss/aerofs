package com.aerofs.lib.configuration;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;

import static com.aerofs.lib.configuration.ConfigurationTestConstants.*;

/**
 * N.B. this test depends on the local production environment. If the SSL handshakes are
 *   failing when it shouldn't or succeeding when it shouldn't, then verify that LP_CERT
 *   is the SSL certificate used by the server available at URL
 */
public class TestHttpsDownloader
{
    @Rule public TemporaryFolder _approot;

    HttpsDownloader _downloader;

    @Before
    public void setup()
            throws Exception
    {
        _downloader = new HttpsDownloader();

        _approot = new TemporaryFolder();
        _approot.create();
    }

    @Test
    public void shouldSucceed()
            throws Throwable
    {
        File file = _approot.newFile();
        ICertificateProvider certificateProvider = new StringBasedCertificateProvider(CERT);

        _downloader.download(URL, certificateProvider, file);

        assert file.exists();
    }

    @Test(expected = SSLHandshakeException.class)
    public void shouldThrowExceptionOnInvalidCertificate()
            throws Throwable
    {
        ICertificateProvider certificateProvider = new StringBasedCertificateProvider(WRONG_CERT);
        _downloader.download(URL, certificateProvider, _approot.newFile());
    }

    @Test(expected = IOException.class)
    public void shouldThrowExceptionOnBadUrl()
            throws Throwable
    {
        ICertificateProvider certificateProvider = new StringBasedCertificateProvider(CERT);
        _downloader.download(BAD_URL, certificateProvider, _approot.newFile());
    }
}
