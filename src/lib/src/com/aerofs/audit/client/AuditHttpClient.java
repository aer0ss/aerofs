/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.audit.client;

import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static java.net.HttpURLConnection.HTTP_OK;

/**
 * This client submits auditable events to the Auditor server (the AeroFS component) using an HttpURLConnection.
 */
abstract class AuditHttpClient implements IAuditorClient
{
    private static final Logger l = Loggers.getLogger(AuditHttpClient.class);

    /**
     * Instantiate a client that can submit audit events to the service.
     *
     * Success does not guarantee the client will be able to connect.
     */
    AuditHttpClient(URL url) { _svcUrl = url; }

    /**
     * Submit an event to the auditor.
     *
     * <strong>IMPORTANT:</strong> you can do multiple {@code submit} calls simultaneously
     */
    @Override
    public void submit(String content) throws IOException
    {
        int contentLength = content.length();
        l.debug("audit submit {} bytes", contentLength);

        HttpURLConnection conn = getConnection(_svcUrl);
        try {
            submitEvent(conn, content);
        } catch (IOException e) {
            l.warn("audit submit err {}", LogUtil.suppress(e));
            throw e;
        }
    }

    /**
     * Subtypes must return an appropriate connection instance (https/http).
     */
    abstract HttpURLConnection getConnection(URL url) throws IOException;

    /**
     * Submit an event, block for the response, and then close the streams so the HTTP(s) socket
     * can be reused by the underlying implementation.
     */
    private static void submitEvent(HttpURLConnection conn, String content) throws IOException
    {
        OutputStream os = null;
        InputStream is = null;
        try {
            os = conn.getOutputStream();
            os.write(content.getBytes("UTF-8"));

            int code = conn.getResponseCode();
            if (code != HTTP_OK) {
                l.warn("event submission response " + code);
                throw new IOException("event submission failed:" + code);
            }

            // close the input / error stream explicitly to allow socket reuse...
            // Dear HttpURLconnection: you SUCK.
            is = conn.getInputStream();
        } catch (IOException e) {
            if (conn.getErrorStream() != null) { conn.getErrorStream().close(); }
            throw e;
        } finally {
            if (os != null) os.close();
            if (is != null) is.close();
        }
    }

    private URL _svcUrl;
}
