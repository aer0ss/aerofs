package com.aerofs.lib.configuration;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.google.common.io.ByteStreams;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;

public class HttpsDownloader
{
    /**
     * Given the URL and the SSL certificate of the server, download the resource at _url_
     *   to {@paramref file} using the certificate provided by {@paramref certificateProvider}.
     *
     * This method blocks until the download is complete.
     *
     * @param url - the URL to the resource on the server
     * @param certificateProvider - provides the SSL certificate of the server to download from
     * @param file - the destination to save the resource on local filesystem
     * @throws GeneralSecurityException
     * @throws IOException
     */
    public void download(String url, @Nullable ICertificateProvider certificateProvider, File file)
            throws GeneralSecurityException, IOException
    {
        SSLEngineFactory factory = new SSLEngineFactory(Mode.Client, Platform.Desktop, null,
                certificateProvider, null);

        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

        conn.setSSLSocketFactory(factory.getSSLContext().getSocketFactory());

        ByteStreams.copy(conn.getInputStream(), new FileOutputStream(file));
    }
}
