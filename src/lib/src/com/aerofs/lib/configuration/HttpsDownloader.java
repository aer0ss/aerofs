package com.aerofs.lib.configuration;

import com.aerofs.base.C;
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
    // Due to the logic in Main.main(), we won't be able to show any GUI while we are downloading
    // http config.
    //
    // From the users' perspective, the app will show no activities until the downloader either
    // succeed or timeout. This will be mistaken as a no launch, that's why the timeout is set to
    // a short period of time.
    private static final int SOCKET_TIMEOUT = (int) (3 * C.SEC);

    /**
     * Given the URL and the SSL certificate of the server, download the resource at {@paramref url}
     *   to {@paramref file} using the certificate provided by {@paramref certificateProvider}.
     *
     * This method blocks until the download is complete or the timeout is reached.
     *
     * @param url - the URL to the resource on the server
     * @param certificateProvider - provides the SSL certificate of the server to download from
     * @param file - the destination to save the resource on local filesystem
     * @throws GeneralSecurityException - SSL handshake failed
     * @throws IOException - socket timeout reached, failure to connect, and failure to read
     */
    public void download(String url, @Nullable ICertificateProvider certificateProvider, File file)
            throws GeneralSecurityException, IOException
    {
        SSLEngineFactory factory = new SSLEngineFactory(Mode.Client, Platform.Desktop, null,
                certificateProvider, null);

        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

        conn.setSSLSocketFactory(factory.getSSLContext().getSocketFactory());
        conn.setConnectTimeout(SOCKET_TIMEOUT);
        conn.setReadTimeout(SOCKET_TIMEOUT);

        ByteStreams.copy(conn.getInputStream(), new FileOutputStream(file));
    }
}
