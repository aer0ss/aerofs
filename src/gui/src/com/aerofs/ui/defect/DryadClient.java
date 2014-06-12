/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.ui.defect;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.ssl.FileBasedCertificateProvider;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.ui.IDaemonMonitor;
import com.google.common.collect.Range;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.OCTET_STREAM;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isEmpty;

public class DryadClient
{
    private static final int CHUNK_SIZE = 4 * C.MB;

    private final IDaemonMonitor _dm;

    private final String    _rtroot;
    private final String    _customerID;
    private final String    _userID;
    private final String    _deviceID;
    private final String    _serverHostname;
    private final int       _serverPort;
    private final String    _serverCert;

    private final Logger l = Loggers.getLogger(DryadClient.class);

    DryadClient(IDaemonMonitor dm, String rtroot, String customerID, String userID, String deviceID,
            String serverHostname, int serverPort, String serverCert)
    {
        _dm = dm;
        _rtroot = rtroot;
        _customerID = customerID;
        _userID = userID;
        _deviceID = deviceID;
        _serverHostname = serverHostname;
        _serverPort = serverPort;
        _serverCert = serverCert;
    }

    public String generateNewID()
    {
        return UniqueID.generate().toStringFormal();
    }

    public void reportProblem(String dryadID)
            throws Exception
    {
        l.info("reporting problem #{}", dryadID);

        URL url = createResourceURL(_serverHostname, _serverPort,
                dryadID, _customerID, _userID, _deviceID);

        SSLContext ssl = createSSLContext(_serverCert);

        l.info("opening connection to {}", url);

        HttpsURLConnection conn = openSecureConnection(url, ssl);

        File[] files = getSourceFiles(_rtroot);

        l.info("uploading {} files", files.length);

        uploadFiles(conn, files);

        int response = conn.getResponseCode();
        // we current do not support redirects
        if (!Range.closedOpen(200, 300).contains(response)) {
            throw new Exception("Unexpected response code: " + response);
        }

        l.info("complete");
    }

    private URL createResourceURL(String serverHostname, int serverPort,
            String dryadID, String customerID, String userID, String deviceID)
            throws MalformedURLException
    {
        String resource = format("/v1.0/client/%s/%s/%s/%s/logs",
                customerID, dryadID, userID, deviceID);
        return new URL("https", serverHostname, serverPort, resource);
    }

    private SSLContext createSSLContext(String certificateData)
            throws GeneralSecurityException, IOException
    {
        ICertificateProvider certificateProvider = isEmpty(certificateData) ?
                new FileBasedCertificateProvider(
                        new File(AppRoot.abs(), "dryad.pem").getAbsolutePath()) :
                new StringBasedCertificateProvider(certificateData);

        return new SSLEngineFactory(Mode.Client, Platform.Desktop, null, certificateProvider, null)
                .getSSLContext();
    }

    private void uploadFiles(URLConnection conn, File[] files)
            throws Exception
    {
        ZipOutputStream os = null;

        try {
            os = new ZipOutputStream(conn.getOutputStream());

            /**
             * TODO (AT): look into consolidating this with SVClient
             *
             * N.B. there's subtle techno-magic at work here.
             *
             * Compression, by nature, is cpu-intensive. However, since we stream the output
             * directly to network, the system's throughput is throttled by the network
             * capacity.
             *
             * This applies back pressure to the compression layer. Consequently, the CPU usage
             * is reduced and we don't need to explicitly yield threads.
             */
            compressAndUploadFiles(files, os);
        } finally {
            if (os != null) { os.close(); }
        }
    }

    /**
     * This is very similar to SVClient except for stopping daemon while we upload the core db
     */
    private void compressAndUploadFiles(File[] files, ZipOutputStream os)
            throws Exception
    {
        for (File file : files) {
            // stop the daemon to release the file lock on the database file on Windows
            if (file.getName().equals(LibParam.CORE_DATABASE)) {
                _dm.stopIgnoreException();
            }

            try {
                os.putNextEntry(new ZipEntry(file.getName()));

                InputStream is = null;
                try {
                    is = new FileInputStream(file);

                    ByteStreams.copy(is, os);
                } finally {
                    if (is != null) is.close();
                }
            } catch (IOException e) {
                l.warn("failed to compressAndUploadFiles " + file);
            }

            if (file.getName().equals(LibParam.CORE_DATABASE)) {
                _dm.start();
            }
        }
    }

    private File[] getSourceFiles(String rtroot)
    {
        return new File(rtroot).listFiles(new FileFilter()
        {
            @Override
            public boolean accept(File file)
            {
                return file.isFile();
            }
        });
    }

    private HttpsURLConnection openSecureConnection(URL url, SSLContext ssl)
            throws IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        conn.setSSLSocketFactory(ssl.getSocketFactory());
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty(CONTENT_TYPE, OCTET_STREAM.toString());
        conn.setChunkedStreamingMode(CHUNK_SIZE);
        conn.connect();

        return conn;
    }
}
