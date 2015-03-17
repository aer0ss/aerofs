/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.configuration;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesHelper;
import com.aerofs.base.ex.ExBadArgs;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkState;

public final class ServerConfigurationLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConfigurationLoader.class);

    // The URL that we must GET to obtain configuration properties.
    private static final String CONFIGURATION_URL = "http://localhost:5434/server";

    // Flag created by puppet that tells us we are in private deployment mode.
    private static final String PRIVATE_DEPLOYMENT_FLAG_FILE = "/etc/aerofs/private-deployment-flag";

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
            throws ExHttpConfig, ExBadArgs {
        PropertiesHelper helper = new PropertiesHelper();

        Properties systemProperties = System.getProperties();
        Properties merged;
        File flagFile = new File(PRIVATE_DEPLOYMENT_FLAG_FILE);
        if (flagFile.exists()) {
            merged = helper.mergeProperties(getHttpProperties(serviceName), extra, systemProperties);
        } else {
            merged = helper.mergeProperties(extra, systemProperties);
        }
        return helper.parseProperties(merged);
    }

    /**
     * @throws ExHttpConfig If a URL was provided but the HTTP service GET failed.
     */
    private static Properties getHttpProperties(String serviceName)
            throws ExHttpConfig
    {
        Properties httpProperties = new Properties();

        try {
            HttpURLConnection conn = (HttpURLConnection)(new URL(CONFIGURATION_URL).openConnection());
            String authHeaderValue = AeroService.getHeaderValue(serviceName, AeroService.loadDeploymentSecret());
            conn.setRequestProperty("Authorization", authHeaderValue);
            InputStream is = conn.getInputStream();
            httpProperties.load(is);
        } catch (IOException e) {
            throw new ExHttpConfig("Couldn't load configuration from config server " + CONFIGURATION_URL + ".");
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
