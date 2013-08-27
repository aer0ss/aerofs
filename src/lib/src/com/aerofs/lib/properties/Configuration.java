/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.properties;

import com.aerofs.config.ConfigurationProperties;
import com.aerofs.lib.configuration.ConfigurationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

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
     *     <li>Runtime Configuration (for testing only)</li>
     *     <li>System Configuration (system, -D JVM parameters)</li>
     *     <li>Classpath .properties Resources (static)</li>
     *     <li>Configuration Service (HTTP)</li>
     * </ol>
     * </p>
     */
    public static class Server
    {
        private static final String CONFIGURATION_SERVICE_URL_FILE = "/etc/aerofs/configuration.url";
        private static final String CONFIGURATION_RESOURCE = "configuration.properties";

        /**
         * Loads static properties from the CONFIGURATION_RESOURCE file, first looking in the
         * current folder, and if CONFIGURATION_RESOURCE isn't there, looking in the classpath.
         *
         * @return Properties object parsed from the contents of CONFIGURATION_RESOURCE.
         */
        private static Properties getStaticProperties()
        {
            Properties staticProperties = new Properties();
            InputStream propertyStream = null;
            try {
                try {
                    propertyStream = new File(CONFIGURATION_RESOURCE).toURI().toURL().openStream();
                } catch (Exception e) {
                    propertyStream = Server.class.getClassLoader().getResourceAsStream(CONFIGURATION_RESOURCE);
                }

                staticProperties.load(propertyStream);
            } catch (Exception e) {
                throw new IllegalStateException("Couldn't read config file: " + CONFIGURATION_RESOURCE, e);
            } finally {
                if (propertyStream != null) {
                    try {
                        propertyStream.close();
                    } catch (IOException e) {
                        throw new IllegalStateException("fail access: " + CONFIGURATION_RESOURCE);
                    }
                }
            }

            return staticProperties;
        }

        public static void initialize()
                throws Exception
        {
            Properties systemProperties = System.getProperties();
            Properties staticProperties = getStaticProperties();

            Properties preHttpProperties = new Properties();
            preHttpProperties.putAll(systemProperties);
            preHttpProperties.putAll(staticProperties);
            Properties httpProperties = getHttpProperties(preHttpProperties,
                    new FileBasedConfigurationURLProvider(CONFIGURATION_SERVICE_URL_FILE));

            // Join all properties, throwing if a key appears twice.
            ConfigurationHelper helper = new ConfigurationHelper();
            Properties compositeProperties = helper.disjointUnionOfThreeProperties(systemProperties,
                    staticProperties, httpProperties);

            // Initialize ConfigurationProperties.
            ConfigurationProperties.setProperties(compositeProperties);

            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                compositeProperties.store(byteArrayOutputStream, "Configuration Properties");
                LOGGER.info("Server configuration initialized: " + byteArrayOutputStream.toString("UTF-8"));
            } catch (Exception e) {
                LOGGER.warn("Failed to log server configuration with exception " + e.toString());
            }
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
            Properties compositeProperties,
            IDefaultConfigurationURLProvider provider)
            throws ExHttpConfig
    {
        // We allow the configuration service URL to be overridden by construction of a temporary
        // configuration source from all other configuration sources. Then we lookup the
        // "config.service.url" key if any value has been defined it will be used. If not value has
        // been defined in other configuration sources then lookup a default (may be null).
        String configurationServiceUrl = compositeProperties.getProperty("config.service.url",
                provider.getDefaultConfigurationURL());

        // Return empty configuration source if configurationServiceUrl is not present.
        if (configurationServiceUrl == null) {
            return new Properties();
        }

        Properties httpProperties = new Properties();
        try {
            // NOTE(mh): This uses trust store. Fine to leave like this until removal of config server.
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
