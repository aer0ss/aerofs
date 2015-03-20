/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.audit.client;

import com.aerofs.auth.client.cert.AeroDeviceCert;
import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.base.LazyChecked;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;

/**
 * Factory to create IAuditorClient instances.
 * TODO: implement test auditor client?
 */
public class AuditorFactory
{
    private static Logger l = LoggerFactory.getLogger(AuditorFactory.class);

    /**
     * Create an unauthenticated client - can only be used by trusted server-side processes (SP).
     */
    public static IAuditorClient createAuthenticatedWithSharedSecret(String serviceName, String deploymentSecret)
    {
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT && Audit.AUDIT_ENABLED) {
            try {
                URL url = new URL(
                        "http", Audit.SERVICE_HOST, Audit.SERVICE_PORT, Audit.SERVICE_EVENT_PATH);
                l.info("Unauthenticated connection to private audit endpoint {}", url);

                return new AuditHttpClient(url) {
                    @Override
                    HttpURLConnection getConnection(URL url) throws IOException
                    {
                        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                        conn.setUseCaches(true);
                        conn.setConnectTimeout(Audit.CONN_TIMEOUT);
                        conn.setReadTimeout(Audit.READ_TIMEOUT);
                        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                        String authHeader = AeroService.getHeaderValue(serviceName, deploymentSecret);
                        conn.addRequestProperty(HttpHeaders.AUTHORIZATION, authHeader);
                        conn.setDoOutput(true);
                        conn.connect();
                        return conn;
                    }
                };
            } catch (MalformedURLException mue) {
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
    public static IAuditorClient createAuthenticatedWithDeviceCert()
    {
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT && Audit.AUDIT_ENABLED) {
            l.info("Secure connection to public audit server at {}", Audit.PUBLIC_EVENT_URL);

            return new AuditHttpClient(Audit.PUBLIC_EVENT_URL) {
                @Override
                HttpURLConnection getConnection(URL url) throws IOException
                {
                    HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();

                    conn.setUseCaches(false);
                    conn.setConnectTimeout(Audit.CONN_TIMEOUT);
                    conn.setReadTimeout(Audit.READ_TIMEOUT);
                    conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
                    conn.setSSLSocketFactory(socketFactory.get());
                    conn.addRequestProperty(HttpHeaders.AUTHORIZATION, AeroDeviceCert.getHeaderValue(Cfg.user().getString(), Cfg.did().toStringFormal()));
                    conn.setDoOutput(true);
                    conn.connect();

                    return conn;
                }

                private final LazyChecked<SSLSocketFactory, IOException> socketFactory =
                        new LazyChecked<>(AuditorFactory::createSocketFactory);
            };
        }

        return createNoopClient();
    }

    public static IAuditorClient createNoopClient()
    {
        l.info("Audit service is disabled");
        return content -> { };
    }

    private static SSLSocketFactory createSocketFactory() throws IOException
    {
        SSLEngineFactory factory = new SSLEngineFactory(Mode.Client,
                Platform.Desktop, new CfgKeyManagersProvider(),
                new CfgCACertificateProvider(), null);
        try {
            return factory.getSSLContext().getSocketFactory();
        } catch (GeneralSecurityException e) {
            l.warn("General security exception creating SSL client socket factory", e);
            throw new IOException("Security exception establishing SSL session", e);
        }
    }
}
