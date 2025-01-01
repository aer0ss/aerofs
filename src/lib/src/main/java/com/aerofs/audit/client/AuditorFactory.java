/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.audit.client;

import com.aerofs.auth.client.cert.AeroDeviceCert;
import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.AuditParam;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.google.common.base.Throwables;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.*;
import java.security.GeneralSecurityException;

/**
 * Factory to create IAuditorClient instances.
 * TODO: implement test auditor client?
 */
public class AuditorFactory
{
    private final static Logger l = LoggerFactory.getLogger(AuditorFactory.class);

    /**
     * Create an unauthenticated client - can only be used by trusted server-side processes (SP).
     */
    public static IAuditorClient createAuthenticatedWithSharedSecret(String host,
                                                                     String serviceName,
                                                                     String deploymentSecret)
    {
        AuditParam params = AuditParam.fromConfiguration();
        if (params._enabled) {
            try {
                URL url = new URI("http", null, host, params._servicePort, params._servicePath,
                        null, null).toURL();
                l.info("Unauthenticated connection to private audit endpoint {}", url);

                return new AuditHttpClient(url) {
                    @Override
                    HttpURLConnection getConnection(URL url) throws IOException
                    {
                        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                        conn.setUseCaches(true);
                        conn.setConnectTimeout(params._connTimeout);
                        conn.setReadTimeout(params._readTimeout);
                        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                        String authHeader = AeroService.getHeaderValue(serviceName, deploymentSecret);
                        conn.addRequestProperty(HttpHeaders.AUTHORIZATION, authHeader);
                        conn.setDoOutput(true);
                        conn.connect();
                        return conn;
                    }
                };
            } catch (URISyntaxException | MalformedURLException mue) {
                l.error("Misconfigured audit service URL. Auditing is DISABLED.");
            }
        }

        return createNoopClient();
    }

    /**
     * Create an authenticated HTTP client. This client will (a) connect to PUBLIC_EVENT_URL
     * and (b) include the required/expected HTTP header fields for authentication.
     * See HttpRequestAuthenticator.
     */
    public static IAuditorClient createAuthenticatedWithDeviceCert(UserID user, DID did,
                                                                   SSLEngineFactory ssl)
    {
        AuditParam param = AuditParam.fromConfiguration();
        if (param._enabled) {
            l.info("Secure connection to public audit server at {}", param._publicUrl);

            try {
                URL url = URI.create(param._publicUrl).toURL();
                return new AuditHttpClient(url) {
                    @Override
                    HttpURLConnection getConnection(URL url) throws IOException {
                        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

                        conn.setUseCaches(false);
                        conn.setConnectTimeout(param._connTimeout);
                        conn.setReadTimeout(param._readTimeout);
                        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE,
                                MediaType.JSON_UTF_8.toString());
                        conn.setSSLSocketFactory(socketFactory(ssl));
                        conn.addRequestProperty(HttpHeaders.AUTHORIZATION,
                                AeroDeviceCert.getHeaderValue(user.getString(),
                                        did.toStringFormal()));
                        conn.setDoOutput(true);
                        conn.connect();

                        return conn;
                    }
                };
            } catch (MalformedURLException e) {
                Throwables.propagate(e);
            }
        }

        return createNoopClient();
    }

    public static IAuditorClient createNoopClient()
    {
        l.info("Audit service is disabled");
        return content -> { };
    }

    private static SSLSocketFactory socketFactory(SSLEngineFactory factory) throws IOException
    {
        try {
            return factory.getSSLContext().getSocketFactory();
        } catch (GeneralSecurityException e) {
            l.warn("General security exception creating SSL client socket factory", e);
            throw new IOException("Security exception establishing SSL session", e);
        }
    }
}
