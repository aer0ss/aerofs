/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.LazyChecked;
import com.aerofs.oauth.Scope;
import com.aerofs.sp.authentication.DeploymentSecret;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


// FIXME: move URL sharing API to sparta to get rid of sp->sparta dep
public class BifrostClient
{
    private static final LazyChecked<String, IOException> ZELDA_SECRET
            = new LazyChecked<>(BifrostClient::getZeldaSecret);

    private static String getZeldaSecret() throws IOException {
        URL bifrost= new URL("http://sparta.service:8700");
        String auth = "Aero-Service-Shared-Secret sp " + DeploymentSecret.getSecret();

        String response;
        HttpURLConnection conn = (HttpURLConnection)new URL(bifrost, "/clients/aerofs-zelda").openConnection();
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Authorization", auth);

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
            try (InputStream is = conn.getInputStream()) {
                response = BaseUtil.streamToString(is, StandardCharsets.UTF_8.name());
            }
        } else if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            conn = (HttpURLConnection)new URL(bifrost, "/clients").openConnection();
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Authorization", auth);
            String formParams = "client_id=aerofs-zelda"
                        + "&client_name=" + URLEncoder.encode("AeroFS Link Sharing", "UTF-8")
                        + "&redirect_uri=" + URLEncoder.encode("aerofs://redirect", "UTF-8")
                        + "&resource_server_key=oauth-havre"
                        + "&expires_in=0";

            response = BaseUtil.httpRequest(conn, formParams);
        } else {
            throw new IOException("HTTP request failed. Code: " + status);
        }
        return new JsonParser()
                .parse(response)
                .getAsJsonObject()
                .get("secret")
                .getAsString();
    }

    /**
     * Gets bifrost token needed for link sharing. Bifrost expects:
     * Grant type
     * Mobile access code
     * Code type
     * Client-ID
     * Client-secret
     * Token expiry time.
     */
    public String getBifrostToken(String soid, String mobileAccessCode, long expires)
            throws IOException
    {
        String formParams = "grant_type=authorization_code"
                + "&code=" + mobileAccessCode
                + "&code_type=device_authorization"
                + "&client_id=aerofs-zelda"
                + "&client_secret=" + ZELDA_SECRET.get()
                + "&scope=" + Scope.LINKSHARE.name + "," + Scope.READ_FILES.name + ":" + soid
                + "&expires_in=" + expires;

        URL bifrostBaseUrl = new URL("http://sparta.service:8700");
        URL bifrostTokensUrl = new URL(bifrostBaseUrl, "/token");

        HttpURLConnection conn = (HttpURLConnection)bifrostTokensUrl.openConnection();
        conn.setDoOutput(true);

        String response = BaseUtil.httpRequest(conn, formParams);
        return new JsonParser()
                .parse(response)
                .getAsJsonObject()
                .get("access_token")
                .getAsString();
    }

    public void deleteToken(String oldToken)
            throws IOException
    {
        URL bifrostBaseUrl = new URL("http://sparta.service:8700");
        URL bifrostTokensUrl = new URL(bifrostBaseUrl, "/token/" + oldToken);

        HttpURLConnection conn = (HttpURLConnection)bifrostTokensUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("DELETE");
        BaseUtil.httpRequest(conn, null);
    }
}
