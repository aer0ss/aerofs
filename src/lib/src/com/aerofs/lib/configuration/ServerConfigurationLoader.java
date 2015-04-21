/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.configuration;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesHelper;
import com.aerofs.base.ex.ExBadArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;


public final class ServerConfigurationLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigurationLoader.class);

    private static final String SERVER_CONFIG_URL = ConfigurationUtils.CONFIGURATION_URL + "/server";

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
        new PropertiesHelper().logProperties(LOGGER, "Configuration initialized", merged);

    }

    private static Properties effectiveProperties(String serviceName, Properties extra)
            throws ConfigurationUtils.ExHttpConfig, ExBadArgs
    {
        PropertiesHelper helper = new PropertiesHelper();

        Properties systemProperties = System.getProperties();
        Properties merged;
        if (ConfigurationUtils.isPrivateDeployment()) {
            merged = helper.mergeProperties(getHttpProperties(serviceName), extra, systemProperties);
        } else {
            merged = helper.mergeProperties(extra, systemProperties);
        }
        return helper.parseProperties(merged);
    }

    /**
     * @throws ConfigurationUtils.ExHttpConfig If a URL was provided but the HTTP service GET failed.
     */
    private static Properties getHttpProperties(String serviceName)
            throws ConfigurationUtils.ExHttpConfig
    {
        Properties httpProperties = new Properties();

        try {
            HttpURLConnection conn = (HttpURLConnection)(new URL(SERVER_CONFIG_URL).openConnection());
            String authHeaderValue = AeroService.getHeaderValue(serviceName, AeroService.loadDeploymentSecret());
            conn.setRequestProperty("Authorization", authHeaderValue);
            try (InputStream is = conn.getInputStream()) {
                httpProperties.load(is);
            }
        } catch (IOException e) {
            throw new ConfigurationUtils.ExHttpConfig("Couldn't load configuration from config server " + SERVER_CONFIG_URL + ".");
        }

        return httpProperties;
    }
}
