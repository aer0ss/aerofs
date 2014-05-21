package com.aerofs.sp.server.authentication;

import java.io.OutputStream;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;

public class SystemAuthEndpoint implements ISystemAuthEndpoint
{
    private static final Logger l = Loggers.getLogger(SystemAuthEndpoint.class);

    private final String _host;
    private final int _port;
    private final boolean _useSSL;
    private final String _certificate;

    public SystemAuthEndpoint()
    {
        _host = SystemAuthParam.SYSTEM_AUTH_ENDPOINT_HOST;
        _port = SystemAuthParam.SYSTEM_AUTH_ENDPOINT_PORT;
        _useSSL = SystemAuthParam.SYSTEM_AUTH_ENDPOINT_USE_SSL;
        _certificate = SystemAuthParam.SYSTEM_AUTH_ENDPOINT_CERTIFICATE;
    }

    private URL createResourceURL(UserID userID)
            throws MalformedURLException
    {
        return new URL(String.format("%s://%s%s/system/v1.0/%s/authorized",
                _useSSL ? "https" : "http",
                _host,
                _port == 0 ? "" : String.format(":%d", _port),
                userID));
    }

    private SSLContext createSSLContext()
            throws IOException, GeneralSecurityException
    {
        ICertificateProvider certProvider = new StringBasedCertificateProvider(_certificate);
        return new SSLEngineFactory(Mode.Client, Platform.Desktop, null, certProvider, null)
                .getSSLContext();
    }

    @Override
    public boolean isSystemAuthorized(UserID userID, JSONObject body)
            throws IOException, GeneralSecurityException
    {
        URL url = createResourceURL(userID);
        HttpURLConnection connection;

        // In the case when we are using SSL, cast to an HTTPS connection and optionally specify
        // the endpoint certificate.
        if (_useSSL) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) url.openConnection();

            if (_certificate.length() > 0) {
                httpsConnection.setSSLSocketFactory(createSSLContext().getSocketFactory());
            }

            connection = httpsConnection;
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        connection.setRequestMethod("POST");
        connection.setReadTimeout(5000);

        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        OutputStream os = connection.getOutputStream();
        os.write(body.toJSONString().getBytes());
        os.close();

        connection.connect();
        int code = connection.getResponseCode();
        connection.disconnect();

        switch (code) {
        case 204:
            return true;
        case 401:
            return false;
        default:
            l.warn("invalid code {} from endpoint {}", code, url);
            throw new IOException("invalid code");
        }
    }
}