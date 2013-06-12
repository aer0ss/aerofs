/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.properties;

import com.aerofs.base.BaseParam;
import com.aerofs.config.DynamicConfiguration;
import com.aerofs.config.sources.PropertiesConfiguration;
import com.aerofs.config.sources.SystemConfiguration;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.netflix.config.ConcurrentMapConfiguration;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class Configuration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    private static final String CONFIGURATION_RESOURCE = "configuration.properties";

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

        private static void verifyStaticPropertyFilesExist()
        {
            InputStream propertyStream = null;
            for (String propertyPath : getStaticPropertyPaths()) {
                try {
                    propertyStream = Server.class.getClassLoader().getResourceAsStream(propertyPath);
                } finally {
                    // regardless of whether we got here via an exception or not,
                    // if the property file doesn't exist we'll throw saying that the
                    // property file is missing
                    if (propertyStream == null) {
                        throw new IllegalStateException("missing config: " + propertyPath);
                    } else {
                        try {
                            propertyStream.close();
                        } catch (IOException e) {
                            throw new IllegalStateException("fail access: " + propertyPath);
                        }
                    }
                }
            }
        }

        public static void initialize()
                throws ConfigurationException, MalformedURLException
        {
            final AbstractConfiguration systemConfiguration =
                    SystemConfiguration.newInstance();

            verifyStaticPropertyFilesExist();
            final AbstractConfiguration staticPropertiesConfiguration =
                    PropertiesConfiguration.newInstance(getStaticPropertyPaths());

            final AbstractConfiguration httpConfiguration = getHttpConfiguration(
                    ImmutableList.of(systemConfiguration, staticPropertiesConfiguration),
                    new FileBasedConfigurationURLProvider(CONFIGURATION_SERVICE_URL_FILE));

            DynamicConfiguration.initialize(DynamicConfiguration.builder()
                    .addConfiguration(systemConfiguration, "system")
                    .addConfiguration(staticPropertiesConfiguration, "static")
                    .addConfiguration(httpConfiguration, "http")
                    .build());

            BaseParam.setPropertySource(new DynamicPropertySource());

            LOGGER.info("Server configuration initialized: " + DynamicConfiguration.getInstance());
        }
    }

    /**
     * Provides the initialization logic for the AeroFS/Team Server client configuration.
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
    public static class Client
    {
        private static final String CONFIGURATION_SERVICE_URL_FILE = "/configuration.url";

        public static void initialize(String rtRoot)
            throws ConfigurationException, MalformedURLException
        {
            final AbstractConfiguration systemConfiguration =
                    SystemConfiguration.newInstance();
            final AbstractConfiguration staticPropertiesConfiguration =
                    PropertiesConfiguration.newInstance(getStaticPropertyPaths());
            final AbstractConfiguration httpConfiguration = getHttpConfiguration(
                    ImmutableList.of(systemConfiguration, staticPropertiesConfiguration),
                    new FileBasedConfigurationURLProvider(
                            new File(rtRoot, CONFIGURATION_SERVICE_URL_FILE).getAbsolutePath()));

            DynamicConfiguration.Builder dynamicConfigurationBuilder =
                    DynamicConfiguration.builder()
                            .addConfiguration(systemConfiguration, "system")
                            .addConfiguration(staticPropertiesConfiguration, "static")
                            .addConfiguration(httpConfiguration, "http");

            BaseParam.setPropertySource(new DynamicPropertySource());
            DynamicConfiguration.initialize(dynamicConfigurationBuilder.build());
            LOGGER.debug("Client configuration initialized");
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

    private static List<String> getStaticPropertyPaths()
    {
        return Collections.singletonList(CONFIGURATION_RESOURCE);
    }

    /**
     * @throws ConfigurationException If a URL was provided but the HTTP service GET failed.
     */
    private static AbstractConfiguration getHttpConfiguration(
            final Collection<AbstractConfiguration> configurationSources,
            IDefaultConfigurationURLProvider provider)
            throws ConfigurationException, MalformedURLException
    {
        // We allow the configuration service URL to be overridden by construction of a temporary
        // configuration source from all other configuration sources. Then we lookup the
        // "config.service.url" key if any value has been defined it will be used. If not value has
        // been defined in other configuration sources then lookup a default (may be null).
        final Optional<String> configurationServiceUrl =
                Optional.fromNullable(new CompositeConfiguration(configurationSources).getString("config.service.url",
                        provider.getDefaultConfigurationURL()));

        // Return empty configuration source if configurationServiceUrl is not present.
        if (!configurationServiceUrl.isPresent()) {
            return new ConcurrentMapConfiguration();
        }

        return new org.apache.commons.configuration.PropertiesConfiguration(new URL(configurationServiceUrl.get()));
    }
}
