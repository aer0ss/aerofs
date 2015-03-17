package com.aerofs.lib.configuration;

import com.aerofs.auth.client.shared.AeroService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class ServerConfigurationSetter {
    private static final String SET_CONFIG_URL = ConfigurationUtils.CONFIGURATION_URL + "/set?";

    public static boolean setProperty(String serviceName, String propertyName, String propertyValue)
            throws ConfigurationUtils.ExHttpConfig
    {
        try {
            HttpURLConnection conn = (HttpURLConnection) (new URL(SET_CONFIG_URL).openConnection());
            String authHeaderValue = AeroService.getHeaderValue(serviceName, AeroService.loadDeploymentSecret());
            conn.setRequestProperty("Authorization", authHeaderValue);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            try (OutputStream outputStream = conn.getOutputStream()) {
                outputStream.write(String.format("key=%s&value=%s", URLEncoder.encode(propertyName, "UTF-8"), URLEncoder.encode(propertyValue, "UTF-8")).getBytes());
                return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
            } finally {
                // getResponseCode() implicitly uses the connection's inputstream, this way we ensure the stream is closed after use
                conn.getInputStream().close();
            }
        } catch (IOException e) {
            throw new ConfigurationUtils.ExHttpConfig("Couldn't set configuration at config server " + SET_CONFIG_URL + ".");
        }
    }
}
