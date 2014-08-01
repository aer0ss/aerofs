/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseUtil;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import static com.aerofs.lib.LibParam.Bifrost.INTERNAL_BIFROST_HOST;
import static com.aerofs.lib.LibParam.Bifrost.INTERNAL_BIFROST_PORT;
import static com.aerofs.sp.server.lib.SPParam.ZELDA_ID;
import static com.aerofs.sp.server.lib.SPParam.ZELDA_SECRET;

public class BifrostClient
{
    /**
     * Gets bifrost token needed for link sharing. Bifrost expects:
     * Grant type
     * Mobile access code
     * Code type
     * Client-ID
     * Client-secret
     * Token expiry time.
     */
    public String getBifrostToken(String mobileAccessCode, long expires)
            throws IOException, ExecutionException, InterruptedException
    {
        String formParams =
                "grant_type=authorization_code&code=" + mobileAccessCode +
                        "&code_type=device_authorization&client_id=" + ZELDA_ID +
                        "&client_secret=" + ZELDA_SECRET + "&expires_in=" + expires;

        URL bifrostBaseUrl = new URL("http", INTERNAL_BIFROST_HOST, INTERNAL_BIFROST_PORT, "");
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
        URL bifrostBaseUrl = new URL("http", INTERNAL_BIFROST_HOST, INTERNAL_BIFROST_PORT, "");
        URL bifrostTokensUrl = new URL(bifrostBaseUrl, "/token/" + oldToken);

        HttpURLConnection conn = (HttpURLConnection)bifrostTokensUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("DELETE");
    }
}
