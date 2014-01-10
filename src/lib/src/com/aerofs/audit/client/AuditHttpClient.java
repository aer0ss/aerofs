/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.audit.client;

import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static java.net.HttpURLConnection.HTTP_OK;

class AuditHttpClient implements IAuditorClient
{
    private static final Logger l = Loggers.getLogger(AuditHttpClient.class);

    /**
     * Create an HTTP client for the auditor service. This client is configured by BaseParam.Audit.
     * TODO: expose internal and external versions of this (public URL versus internal URL)
     */
    public static IAuditorClient create()
    {
        if (Audit.AUDIT_ENABLED) {
            try {
                l.info("Enabling connection to audit server at {}:{}/{}",
                        Audit.SERVICE_HOST, Audit.SERVICE_PORT, Audit.SERVICE_EVENT_PATH);
                return new AuditHttpClient(new URL(
                        "http", Audit.SERVICE_HOST, Audit.SERVICE_PORT, Audit.SERVICE_EVENT_PATH));
            } catch (MalformedURLException mue) {
                l.error("Misconfigured audit service URL. Auditing is DISABLED.");
            }
        }
        l.info("Audit service is disabled.");
        return null;
    }

    /**
     * Instantiate a client that can submit audit events to the service, using the configuration
     * information from LibParam.Audit.
     * If the configuration is broken (does not form a valid URL) this constructor
     * may throw an unchecked exception.
     * Success does not guarantee the client can connect to an audit service.
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
            l.warn("audit submit err", LogUtil.suppress(e));
            throw e;
        } finally {
            if (conn != null) {
                l.debug("audit client disconnect");
                conn.disconnect();
            }
        }
    }

    private static HttpURLConnection getConnection(URL url) throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setUseCaches(true);
        conn.setConnectTimeout(Audit.CONN_TIMEOUT);
        conn.setReadTimeout(Audit.READ_TIMEOUT);
        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        conn.setDoOutput(true);
        conn.connect();

        return conn;
    }

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
            l.warn("event submission failed: " + code);
            throw new IOException("event submission failed:" + code);
        }

        // TODO: we currently don't even bother checking for response body, because we don't care
        // Two things to think about:
        // - make sure this doesn't leak resources when readFully() is not invoked
        // - error response returns a json doc that we just drop on the floor.
    }

    private static void closeSilently(InputStream stream)
    {
        try {
            stream.close();
        } catch (IOException e) {
            // ignored
        }
    }

    private URL _svcUrl;
}
