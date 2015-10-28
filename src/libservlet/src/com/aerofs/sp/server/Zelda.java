package com.aerofs.sp.server;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.LazyChecked;
import com.aerofs.oauth.Scope;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.net.URLEncoder.encode;

/**
 * The requirements for link sharing complicates identity management, and Zelda was created as a
 * stopgap solution.
 *
 * The current solution, to support signin from mobile devices, uses the following workflow:
 *      user logs into web -> web asks sp to create an access code -> web relay the access code
 *          back to user.
 *      user agent presents the access code to bifrost -> bifrost verifies the access code against
 *          SP database (by amalgamating with sparta) and grants an access token.
 *
 * For link sharing, SP needs to obtain fresh new access tokens. Thus Zelda was created to pretend
 * to be an user agent using the above workflow. Zelda's workflow is as follows:
 *      SP needs an access token for whatever reason -> SP asks Zelda to create an access code ->
 *          SP asks Zelda to exchange the access code for an access token against sparta.
 */
public class Zelda
{
    // protected so unit tests can read it
    protected static final String CHARSET = StandardCharsets.UTF_8.name();

    private final String _bifrostUrl;
    private final String _auth;

    private final LazyChecked<String, IOException> _secret
            = new LazyChecked<>(this::getSecretImpl);

    public Zelda(String bifrostUrl, String serviceId, String deploymentSecret)
    {
        _bifrostUrl = bifrostUrl;
        _auth = String.format("Aero-Service-Shared-Secret %s %s", serviceId, deploymentSecret);
    }

    // this method is created and made protected so we can override it in unit tests
    // note that lambda references cannot be replaced by stubbing so we added yet another layer
    // of redirection.
    protected String getSecret() throws IOException
    {
        return _secret.get();
    }

    private String getSecretImpl() throws IOException
    {
        return extractJsonField(getClientInfo(), "secret");
    }

    private String extractJsonField(String json, String field)
    {
        return new JsonParser()
                .parse(json)
                .getAsJsonObject()
                .get(field)
                .getAsString();
    }

    // protected so we can override it in unit tests
    protected HttpURLConnection openBifrostConnection(String route)
            throws IOException
    {
        return (HttpURLConnection)new URL(new URL(_bifrostUrl), route).openConnection();
    }

    @SuppressWarnings("try")
    private String getClientInfo() throws IOException
    {
        HttpURLConnection conn = openBifrostConnection("/clients/aerofs-zelda");
        try (Closeable ignored = conn::disconnect) {
            conn.setRequestMethod("GET");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Authorization", _auth);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream()) {
                    return BaseUtil.streamToString(is, CHARSET);
                }
            } else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return createClientInfo();
            } else {
                throw new IOException("HTTP request failed. Code : " + status);
            }
        }
    }

    @SuppressWarnings("try")
    private String createClientInfo() throws IOException
    {
        HttpURLConnection conn = openBifrostConnection("/clients");
        try (Closeable ignored = conn::disconnect) {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Authorization", _auth);

            ImmutableMap<String, String> params = ImmutableMap.<String, String>builder()
                    .put("client_id", "aerofs-zelda")
                    .put("client_name", encode("AeroFS Link Sharing", CHARSET))
                    .put("redirect_uri", encode("aerofs://redirect", CHARSET))
                    .put("resource_server_key", "oauth-havre")
                    .put("expires_in", "0")
                    .build();

            return BaseUtil.httpRequest(conn, createFormParams(params));
        }
    }

    private String createFormParams(Map<String, String> params)
    {
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + '=' + entry.getValue())
                .reduce((entry1, entry2) -> entry1 + '&' + entry2)
                .orElse("");
    }

    @SuppressWarnings("try")
    public String createAccessToken(String soid, String accessCode, long expires)
            throws IOException
    {
        String scope = Scope.LINKSHARE.name + "," + Scope.READ_FILES.name + ":" + soid;

        ImmutableMap<String, String> params = ImmutableMap.<String, String>builder()
                .put("grant_type", "authorization_code")
                .put("code", accessCode)
                .put("code_type", "device_authorization")
                .put("client_id", "aerofs-zelda")
                .put("client_secret", getSecret())
                .put("scope", encode(scope, CHARSET))
                .put("expires_in", Long.toString(expires))
                .build();

        HttpURLConnection conn = openBifrostConnection("/token");
        try (Closeable ignored = conn::disconnect) {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String response = BaseUtil.httpRequest(conn, createFormParams(params));
            return extractJsonField(response, "access_token");
        }
    }

    @SuppressWarnings("try")
    public void deleteToken(String oldToken)
            throws IOException
    {
        HttpURLConnection conn = openBifrostConnection("/token/" + oldToken);
        try (Closeable ignored = conn::disconnect) {
            conn.setRequestMethod("DELETE");

            BaseUtil.httpRequest(conn, null);
        }
    }
}
