/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.audit.client;

import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static java.net.HttpURLConnection.HTTP_OK;

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

        HttpURLConnection conn = null;
        try {
            l.debug("audit submit {} bytes", contentLength);

            conn = getConnection(_svcUrl);
            submitEvent(conn, content);
            readResponse(conn);
        } catch (IOException e) {
            l.warn("audit submit err {}", LogUtil.suppress(e));
            throw e;
        } finally {
            if (conn != null) {
                l.debug("audit client disconnect");
                conn.disconnect();
            }
        }
    }

    /**
     * Return an appropriate connection instance for the requested type.
     */
    abstract HttpURLConnection getConnection(URL url) throws IOException;

    private static void submitEvent(HttpURLConnection conn, String content) throws IOException
    {
        OutputStream httpStream = null;
        try {
            httpStream = conn.getOutputStream();
            httpStream.write(content.getBytes("UTF-8"));
        } finally {
            if (httpStream != null) httpStream.close();
        }
    }

    private static void readResponse(HttpURLConnection conn) throws IOException
    {
        l.debug("read response");

        int code = conn.getResponseCode();
        if (code != HTTP_OK) {
            l.warn("event submission response " + code);
            throw new IOException("event submission failed:" + code);
        }
        // TODO: we currently don't even bother checking for response body, because we don't care
    }

    private URL _svcUrl;
}
