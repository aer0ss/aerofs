/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.properties;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

// TODO (MP) rename this class to ServerConfigurationLoader or something.
public final class Configuration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    // The URL that we must GET to obtain configuration properties.
    private static final String CONFIGURATION_URL = "http://localhost:5436/";

    // Flag created by puppet that tells us we are in private deployment mode.
    public static final String PRIVATE_DEPLOYMENT_FLAG_FILE = "/etc/aerofs/private-deployment-flag";

    /**
     * Provides the initialization logic for the various AeroFS services.
     */
    public static class Server
    {
        public static void initialize() throws Exception
        {
            initialize(new Properties());
        }

        public static void initialize(Properties extra) throws Exception
        {
            PropertiesHelper helper = new PropertiesHelper();
            Properties properties = System.getProperties();

            File flagfile = new File(PRIVATE_DEPLOYMENT_FLAG_FILE);
            if (flagfile.exists()) {
                properties = helper.unionOfThreeProperties(properties, extra, getHttpProperties());
            } else {
                properties = helper.unionProperties(properties, extra);
            }

            // Initialize ConfigurationProperties. Perform variable resolution.
            ConfigurationProperties.setProperties(helper.parseProperties(properties));

            helper.logProperties(LOGGER, "Server configuration initialized", properties);
        }
    }

    /**
     * @throws ExHttpConfig If a URL was provided but the HTTP service GET failed.
     */
    private static Properties getHttpProperties()
            throws ExHttpConfig
    {
        Properties httpProperties = new Properties();

        try {
            InputStream is = new URL(CONFIGURATION_URL).openConnection().getInputStream();
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
