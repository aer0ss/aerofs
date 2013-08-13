/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.properties;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.config.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

// TODO (MP) rename this class to ServerConfigurationLoader or something.
public final class Configuration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private static interface IDefaultConfigurationURLProvider
    {
        public String getDefaultConfigurationURL();
    }

    /**
     * Provides the initialization logic for the various AeroFS services
     *
     * <p>
     * The following configuration sources are used (in order of preference):
     * <ol>
     *     <li>System Configuration (system, -D JVM parameters)</li>
     *     <li>/opt/config property resources (static)</li>
     *     <li>Configuration Service (HTTP)</li>
     * </ol>
     * </p>
     */
    public static class Server
    {
        private static final String CONFIGURATION_SERVICE_URL_FILE = "/etc/aerofs/configuration.url";
        private static final String CONFIGURATION_RESOURCE_COMMON = "/opt/config/properties/common.properties";
        private static final String CONFIGURATION_RESOURCE_SERVER = "/opt/config/properties/server.properties";

        public static void initialize()
                throws Exception
        {
            PropertiesHelper helper = new PropertiesHelper();
            Properties properties = System.getProperties();

            // Only load /opt/config properties if they exist, i.e. if we are on the persistent box.
            if (new File(CONFIGURATION_RESOURCE_COMMON).exists()) {
                properties = helper.unionProperties(
                        properties,
                        helper.readPropertiesFromFile(CONFIGURATION_RESOURCE_COMMON));
            }
            if (new File(CONFIGURATION_RESOURCE_SERVER).exists()) {
                properties = helper.unionProperties(
                        properties,
                        helper.readPropertiesFromFile(CONFIGURATION_RESOURCE_SERVER));
            }

            properties = helper.unionProperties(properties, getHttpProperties(
                    new FileBasedConfigurationURLProvider(CONFIGURATION_SERVICE_URL_FILE)));

            // Initialize ConfigurationProperties. Perform variable resolution.
            ConfigurationProperties.setProperties(helper.parseProperties(properties));

            helper.logProperties(LOGGER, "Server configuration initialized", properties);
        }
    }

    private static class FileBasedConfigurationURLProvider implements IDefaultConfigurationURLProvider
    {
        private String _urlFile;

        public FileBasedConfigurationURLProvider(String urlFile)
        {
            _urlFile = urlFile;
        }

        /**
         * Get the default configuration service URL from a file place on the file system. This file
         * will either be generated manually by the AeroFS deployment engineer or by openstack
         * scripts.
         */
        @Override
        public String getDefaultConfigurationURL()
        {
            String urlString = null;
            BufferedReader br = null;

            try {
                br = new BufferedReader(new FileReader(_urlFile));
                urlString = br.readLine();
            } catch (Exception e) {
                /**
                 * N.B. This class is used by both server and clients to initialize configurations.
                 *   Most of the times, the url file is missing on the client. This is expected
                 *   and the client will fall back to use the default values.
                 */
                LOGGER.debug("Unable to get config from " + _urlFile);
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException e) {
                        LOGGER.error("Could not close");
                    }
                }
            }

            return urlString;
        }
    }

    /**
     * @throws ExHttpConfig If a URL was provided but the HTTP service GET failed.
     */
    private static Properties getHttpProperties(
            IDefaultConfigurationURLProvider provider)
            throws ExHttpConfig
    {
        String configurationServiceUrl = provider.getDefaultConfigurationURL();

        if (configurationServiceUrl == null) {
            return new Properties();
        }

        Properties httpProperties = new Properties();
        try {
            InputStream is = new URL(configurationServiceUrl).openConnection().getInputStream();
            httpProperties.load(is);
        } catch (IOException e) {
            throw new ExHttpConfig(
                    "Couldn't load configuration from config server " + configurationServiceUrl);
        }
        return httpProperties;
    }

    public static class ExHttpConfig extends Exception
    {
        private static final long serialVersionUID = 1L;
        public ExHttpConfig(String msg) { super(msg); }
    }
}
