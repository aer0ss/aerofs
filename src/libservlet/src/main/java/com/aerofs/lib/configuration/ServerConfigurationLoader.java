/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesRenderer;
import com.aerofs.base.ex.ExBadArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.google.common.base.Preconditions.checkState;

public final class ServerConfigurationLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigurationLoader.class);

    // The URL that we must GET to obtain configuration properties.
    protected static final String CONFIGURATION_URL = "http://config.service:5434";
    private static final String SERVER_CONFIG_URL = CONFIGURATION_URL + "/server";

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

        // Set the log verbosity to the level as defined in config service
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
                .setLevel(Level.toLevel(getStringProperty("base.log.level"), Level.INFO));

        // Log that we have loaded config.
        if (LOGGER.isTraceEnabled()) {
            try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
                merged.store(stream, "Configuration initialized");
                LOGGER.debug(stream.toString("UTF-8"));
            } catch (Exception e) {
                LOGGER.error("Failed to log server configuration with exception " + e.toString());
            }
        }
    }

    private static Properties effectiveProperties(String serviceName, Properties extra)
            throws ExHttpConfig, ExBadArgs
    {
        Properties properties = new Properties();
        properties.putAll(getHttpProperties(serviceName));
        properties.putAll(extra);
        properties.putAll(System.getProperties());
        return new PropertiesRenderer().renderProperties(properties);
    }

    /**
     * @throws ExHttpConfig If a URL was provided but the HTTP GET failed.
     */
    private static Properties getHttpProperties(String serviceName)
            throws ExHttpConfig
    {
        Properties httpProperties = new Properties();

        try {
            HttpURLConnection conn = (HttpURLConnection)(new URL(SERVER_CONFIG_URL).openConnection());
            String authHeaderValue = AeroService.getHeaderValue(serviceName, AeroService.loadDeploymentSecret());
            conn.setRequestProperty("Authorization", authHeaderValue);

            try {
                conn.connect();

                if (!BaseUtil.isHttpSuccess(conn.getResponseCode())) {
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
            throw new ExHttpConfig("Couldn't load configuration from config server " + SERVER_CONFIG_URL + ".");
        }

        return httpProperties;
    }

    public static class ExHttpConfig extends Exception
    {
        private static final long serialVersionUID = 1L;

        public ExHttpConfig(String msg)
        {
            super(msg);
        }
    }
}
