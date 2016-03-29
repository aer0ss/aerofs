package com.aerofs.servlets.lib.analytics;

import static com.aerofs.base.config.ConfigurationProperties.*;
import static java.net.HttpURLConnection.HTTP_OK;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.C;
import com.aerofs.ids.UserID;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnalyticsClient implements IAnalyticsClient {
    private static Logger l = LoggerFactory.getLogger(AnalyticsClient.class);

    private static final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();

    private static final String ServiceName = "analytics";

    private final ExecutorService _executor;
    private final boolean _enabled;
    private final URL _url;

    public AnalyticsClient()
    {
        _enabled = getBooleanProperty("analytics.enabled", false);

        if (_enabled) {
            URL temp;
            try{
                temp = new URL("http://analytics.service:9400/events");
            } catch (MalformedURLException e) {
                temp = null;
                l.error("Misconfigured analytics service URL. Analytics is DISABLED.");
            }
            _url = temp;

            _executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "aerofsanalytics");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        } else {
            _executor = null;
            _url = null;
        }

    }

    public void track(AnalyticsEvent event, UserID user_id, Long value) {
        if (!_enabled) {
            return;
        }

        Map<String, Object> map = Maps.newHashMap();
        map.put("event", event.name());
        map.put("value", 1);
        if (user_id != null) {
            map.put("user_id", user_id.getString());
        }
        if (value != null) {
            map.put("value", value);
        }

        _executor.submit(() -> {
            try {
                submit(_gson.toJson(map));
            } catch (IOException e) {
                l.warn("analytics event submission failed: " + e.toString());
            }
        });
    }

    public void track(AnalyticsEvent event, UserID user_id)
    {
        track(event, user_id, null);
    }

    public void track(AnalyticsEvent event, Long value)
    {
        track(event, null, value);
    }

    public void track(AnalyticsEvent event)
    {
        track(event, null, null);
    }

    // adapted from AuditHttpClient and AuditorFactory
    private void submit(String content) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)_url.openConnection();
        conn.setUseCaches(true);
        conn.setConnectTimeout(10 * (int) C.SEC);
        conn.setReadTimeout(10 * (int)C.SEC);
        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        String authHeader = AeroService.getHeaderValue(ServiceName, AeroService.loadDeploymentSecret());
        conn.addRequestProperty(HttpHeaders.AUTHORIZATION, authHeader);
        conn.setDoOutput(true);
        conn.connect();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(content.getBytes("UTF-8"));

            int code = conn.getResponseCode();
            if (code != HTTP_OK) {
                l.warn("event submission response " + code);
                throw new IOException("event submission failed:" + code);
            }
        } catch (IOException e) {
            if (conn.getErrorStream() != null) { conn.getErrorStream().close(); }
            throw e;
        } finally {
            if (conn.getInputStream() != null) { conn.getInputStream().close(); }
        }
    }
}
