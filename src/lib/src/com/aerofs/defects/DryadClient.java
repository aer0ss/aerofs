/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

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
 * Optionally:
 *   setFileUploadListener(), so we can kill the daemon while uploading the database files
 */
public class DryadClient
{
    private static final Logger l = Loggers.getLogger(DryadClient.class);

    private static final int CHUNK_SIZE = 4 * C.MB;

    private final String _serverUrl;
    private final SSLContext _ssl;

    private FileUploadListener _listener;

    public DryadClient(String serverUrl, SSLContext ssl)
    {
        _serverUrl = serverUrl;
        _ssl = ssl;
    }

    public DryadClient setFileUploadListener(@Nullable FileUploadListener listener)
    {
        _listener = listener;
        return this;
    }

    // N.B. if files.length == 0, the PUT request will still be made.
    // the caller is responsible for deciding whether the call should be made if files.length == 0
    public void uploadFiles(String resourceURL, File[] files)
            throws GeneralSecurityException, IOException
    {
        HttpURLConnection conn = openConnection(_serverUrl + resourceURL, _ssl);

        uploadFiles(conn, files);
        handleResponse(conn);
    }

    private HttpURLConnection openConnection(String url, SSLContext ssl)
            throws IOException
    {
        HttpsURLConnection conn = (HttpsURLConnection) new URL(url).openConnection();

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

    public static class Noop extends DryadClient
    {
        public Noop()
        {
            super("", null);
        }

        @Override
        public void uploadFiles(String resourceUrl, File[] files)
        {
            // noop
        }
    }
}
