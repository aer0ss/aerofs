/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.configuration;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesRenderer;
import com.aerofs.base.ex.ExBadArgs;
import com.google.common.collect.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkState;

public final class ServerConfigurationLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigurationLoader.class);

    private static final String SERVER_CONFIG_URL = ConfigurationUtils.CONFIGURATION_URL + "/server";

    // used to do sanity check on remote http config
    private static final String PROPERTY_BASE_HOST = "base.host.unified";

    public static void initialize(String serviceName) throws Exception
    {
        initialize(serviceName, new Properties());
    }

    public static void initialize(String serviceName, Properties extra) throws Exception
    {
        // Compute effective properties
        Properties merged = effectiveProperties(serviceName, extra);
        // Set properties in global registry.
        ConfigurationProperties.setProperties(merged);
        // Log that we have loaded config.
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            merged.store(stream, "Configuration initialized");
            LOGGER.debug(stream.toString("UTF-8"));
        } catch (Exception e) {
            LOGGER.error("Failed to log server configuration with exception " + e.toString());
        }
    }

    private static Properties effectiveProperties(String serviceName, Properties extra)
            throws ConfigurationUtils.ExHttpConfig, ExBadArgs
    {
        Properties properties = new Properties();
        properties.putAll(getHttpProperties(serviceName));
        properties.putAll(extra);
        properties.putAll(System.getProperties());
        return new PropertiesRenderer().renderProperties(properties);
    }

    /**
     * @throws ConfigurationUtils.ExHttpConfig If a URL was provided but the HTTP GET failed.
     */
    private static Properties getHttpProperties(String serviceName)
            throws ConfigurationUtils.ExHttpConfig
    {
        Properties httpProperties = new Properties();

        try {
            HttpURLConnection conn = (HttpURLConnection)(new URL(SERVER_CONFIG_URL).openConnection());
            String authHeaderValue = AeroService.getHeaderValue(serviceName, AeroService.loadDeploymentSecret());
            conn.setRequestProperty("Authorization", authHeaderValue);

            try {
                conn.connect();

                if (!Range.closedOpen(200, 300).contains(conn.getResponseCode())) {
                    throw new IOException("Failed to load configuration from the config " +
                            "server: " + conn.getResponseCode());
                }

                try (InputStream is = conn.getInputStream()) {
                    httpProperties.load(is);
                }

                checkState(httpProperties.containsKey(PROPERTY_BASE_HOST));
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            throw new ConfigurationUtils.ExHttpConfig("Couldn't load configuration from config server " + SERVER_CONFIG_URL + ".");
        }

        return httpProperties;
    }
}
