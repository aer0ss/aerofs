/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.ui.defect;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.google.common.collect.Range;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.MediaType.OCTET_STREAM;

/**
 * The intended usage of this class is to call uploadFiles() with output from DryadClientUtil.
 *
 * For example:
 *
 * new DryadClient(
 *      "https://dryad.aerofs.com",
 *      createPublicDryadSSLContext(),
 * ).uploadFiles(
 *      createDefectLogsResource("DefectID", Cfg.user(), Cfg.did()),
 *      new File(Cfg.absRTRoot()).listFiles()
 * );
 *
 * Optionally:
 *   setListener(), so we can kill the daemon while uploading the database files
 */
public class DryadClient
{
    private static final int CHUNK_SIZE = 4 * C.MB;

    private final Logger l = Loggers.getLogger(DryadClient.class);

    private final String _hostname;
    private final int _port;
    private final SSLContext _ssl;

    private FileUploadListener _listener;

    public DryadClient(String hostname, int port, SSLContext ssl)
    {
        _hostname = hostname;
        _port = port;
        _ssl = ssl;
    }

    public void setListener(@Nullable FileUploadListener listener)
    {
        _listener = listener;
    }

    // N.B. if files.length == 0, the PUT request will still be made.
    // the caller is responsible for deciding whether the call should be made if files.length == 0
    public void uploadFiles(String resourceUrl, File[] files)
            throws GeneralSecurityException, IOException
    {
        URL url = new URL("https", _hostname, _port, resourceUrl);
        l.info("Connecting to {}", url.toExternalForm());
        HttpURLConnection conn = openConnection(url, _ssl);
        uploadFiles(conn, files);
        handleResponse(conn);
    }

    private HttpURLConnection openConnection(URL url, SSLContext ssl)
            throws IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        conn.setSSLSocketFactory(ssl.getSocketFactory());
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestMethod("PUT");
        conn.setRequestProperty(CONTENT_TYPE, OCTET_STREAM.toString());
        conn.setChunkedStreamingMode(CHUNK_SIZE);
        conn.connect();

        return conn;
    }

    private void uploadFiles(URLConnection conn, File[] files)
            throws IOException
    {
        ZipOutputStream os = null;
        try {
            os = new ZipOutputStream(conn.getOutputStream());

            for (File file : files) {
                try {
                    if (_listener != null) {
                        _listener.onFileUpload(file);
                    }

                    os.putNextEntry(new ZipEntry(file.getName()));

                    InputStream is = null;
                    try {
                        is = new FileInputStream(file);

                        /**
                         * N.B. there's subtle techno-magic at work here.
                         *
                         * Compression, by nature, is cpu-intensive. However, since we stream the
                         * output directly to the network, the system's throughput is throttled by
                         * the network capacity.
                         *
                         * This applies back pressure to the compression layer. Consequently, the
                         * CPU usage is reduced and we don't need to explicitly yield threads.
                         */
                        ByteStreams.copy(is, os);
                    } finally {
                        if (is != null) is.close();
                    }

                    if (_listener != null) {
                        _listener.onFileUploaded(file);
                    }
                } catch (IOException e) {
                    l.warn("Failed to upload file {}", file.getName());
                    // continue to upload other files anyway
                }
            }
        } finally {
            if (os != null) { os.close(); }
        }
    }

    private void handleResponse(HttpURLConnection connection)
            throws IOException
    {
        int response = connection.getResponseCode();
        // we don't support redirects for the time being
        if (!Range.closedOpen(200, 300).contains(response)) {
            throw new IOException("Unexpected response code: " + response);
        }
    }

    public interface FileUploadListener
    {
        void onFileUpload(File file);
        void onFileUploaded(File file);
    }
}
