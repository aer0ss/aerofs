/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.base.analytics;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.google.common.base.Preconditions.checkNotNull;

/**
* A simple Mixpanel track API for Java
*
* Based on https://github.com/eranation/mixpanel-java from Eran Medan - licensed under MIT License
*/
public class MixpanelAPI
{
    private static final Logger l = Loggers.getLogger(MixpanelAPI.class);
    private static final int SOCKET_TIMEOUT = (int) (10 * C.SEC);

    private final String _token;
    private final String _apiEndpoint;

    /**
     * @param token the MixPanel API token
     */
    public MixpanelAPI(String token)
    {
        this(token, getApiEndpointFromConfiguration());
    }

    /**
     * Allow specifying the URL of the API endpoint
     * For testing only.
     */
    protected MixpanelAPI(String token, String apiEndpoint)
    {
        _token = checkNotNull(token);
        _apiEndpoint = checkNotNull(apiEndpoint);
    }

    private static String getApiEndpointFromConfiguration()
    {
        return getStringProperty("base.mixpanel.url", "https://api.mixpanel.com/track/?data=");
    }

    /**
     * Tracks an event
     *
     * @param event
     *          the (required) event name
     * @param distinctId
     *          (required) the user's distinct mixpanel ID (usually stored in a
     *          cookie) or any string that uniquely can identify a user. e.g. the
     *          user id.
     * @param additionalProperties
     *          additional custom properties in a name-value map
     */
    public void track(final String event, @Nullable final String distinctId,
            @Nullable final Map<String, String> additionalProperties)
                throws IOException
    {

            JsonObject json = new JsonObject();
            json.addProperty("event", checkNotNull(event));

            JsonObject properties = new JsonObject();
            json.add("properties", properties);

            // Add the properties
            properties.addProperty("token", _token);
            if (distinctId != null) properties.addProperty("distinct_id", distinctId);
            if (additionalProperties != null) {
                for (Entry<String, String> entry : additionalProperties.entrySet()) {
                    properties.addProperty(entry.getKey(), entry.getValue());
                }
            }

            final String message = json.toString();
            final String url = _apiEndpoint + Base64.encodeBytes(message.getBytes());

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(SOCKET_TIMEOUT);
            conn.setReadTimeout(SOCKET_TIMEOUT);

            String reply = BaseUtil.httpRequest(conn, null);
            if (!reply.equals("1")) {
                throw new IOException("mixpanel failed. Reply: " + reply + " for url: " + url);
            }

            l.debug("mixpanel send ok: {} ", url);

    }
}
