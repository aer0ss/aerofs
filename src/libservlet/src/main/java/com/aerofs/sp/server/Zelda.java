package com.aerofs.sp.server;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.LazyChecked;
import com.aerofs.oauth.Scope;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
    private static final String CLIENT_ID = "aerofs-zelda";
    private static final String CLIENT_NAME = "AeroFS Link Sharing";
    private static final String REDIRECT_URI = "aerofs://redirect";
    private static final String RESOURCE_SERVER_KEY = "oauth-havre";
    public static final String CHARSET = StandardCharsets.UTF_8.name();

    private final URL _bifrostUrl;
    private final String _auth;

    private final LazyChecked<String, IOException> _secret
            = new LazyChecked<>(this::getSecretImpl);

    public Zelda(URL bifrostUrl, String auth)
    {
        _bifrostUrl = bifrostUrl;
        _auth = auth;
    }

    public static Zelda create(String bifrostUrl, String serviceID, String deploymentSecret)
            throws MalformedURLException
    {
        return new Zelda(
                new URL(bifrostUrl),
                String.format("Aero-Service-Shared-Secret %s %s", serviceID, deploymentSecret)
        );
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

    // protected so we can override it in unit tests
    protected HttpURLConnection openBifrostConnection(String route)
            throws IOException
    {
        return (HttpURLConnection)new URL(_bifrostUrl, route).openConnection();
    }

    @SuppressWarnings("try")
    private String getClientInfo() throws IOException
    {
        HttpURLConnection conn = openBifrostConnection("/clients/aerofs-zelda");
        try (Closeable ignored = conn::disconnect) {
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.setDoOutput(false);
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
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Authorization", _auth);

            ImmutableMap<String, String> params = ImmutableMap.<String, String>builder()
                    .put("client_id", CLIENT_ID)
                    .put("client_name", CLIENT_NAME)
                    .put("redirect_uri", REDIRECT_URI)
                    .put("resource_server_key", RESOURCE_SERVER_KEY)
                    .put("expires_in", "0")
                    .build();

            return BaseUtil.httpRequest(conn, createFormParams(params));
        }
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
                .put("client_id", CLIENT_ID)
                .put("client_secret", getSecret())
                .put("scope", scope)
                .put("expires_in", Long.toString(expires))
                .build();

        HttpURLConnection conn = openBifrostConnection("/token");
        try (Closeable ignored = conn::disconnect) {
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
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
            conn.setDoInput(true);
            conn.setDoOutput(false);

            BaseUtil.httpRequest(conn, null);
        }
    }

    private static String extractJsonField(String json, String field)
    {
        return new JsonParser()
                .parse(json)
                .getAsJsonObject()
                .get(field)
                .getAsString();
    }

    private static String createFormParams(Map<String, String> params)
    {
        return params.entrySet().stream()
                .map(param -> {
                    try {
                        return param.getKey() + '=' + URLEncoder.encode(param.getValue(), CHARSET);
                    } catch (UnsupportedEncodingException e) {
                        // checked exception does not play well with lambdas.
                        // realistically, this should not happen and is indicative of a programming
                        // error if it does.
                        throw new RuntimeException(e);
                    }
                })
                .reduce((entry1, entry2) -> entry1 + '&' + entry2)
                .orElse("");
    }
}
