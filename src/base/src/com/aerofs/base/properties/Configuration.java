/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.properties;

import com.aerofs.config.sources.PropertiesConfiguration;
import com.aerofs.labeling.L;
import com.aerofs.config.DynamicConfiguration;
import com.aerofs.config.sources.SystemConfiguration;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.netflix.config.ConcurrentMapConfiguration;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public final class Configuration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    private static final String STRINGS_RESOURCE = "resources/strings.properties";
    private static final String CONFIGURATION_RESOURCE = "resources/configuration.properties";
    private static final String STAGING_CONFIGURATION_RESOURCE = "resources/configuration-stg.properties";
    private static final String CONFIGURATION_SERVICE_URL_FILE = "/etc/aerofs/configuration.url";

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
    public static class Server implements IDefaultConfigurationURLProvider
    {
        public static void initialize()
                throws ConfigurationException, MalformedURLException
        {
            final AbstractConfiguration systemConfiguration =
                    SystemConfiguration.newInstance();
            final AbstractConfiguration staticPropertiesConfiguration =
                    PropertiesConfiguration.newInstance(getStaticPropertyPaths());
            final AbstractConfiguration httpConfiguration = getHttpConfiguration(
                    ImmutableList.of(systemConfiguration, staticPropertiesConfiguration),
                    new Server());

            DynamicConfiguration.initialize(DynamicConfiguration.builder()
                    .addConfiguration(systemConfiguration, "system")
                    .addConfiguration(staticPropertiesConfiguration, "static")
                    .addConfiguration(httpConfiguration, "http")
                    .build());
            LOGGER.info("Server configuration initialized: " + DynamicConfiguration.getInstance());
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
                br = new BufferedReader(new FileReader(CONFIGURATION_SERVICE_URL_FILE));
                urlString = br.readLine();
            } catch (Exception e) {
                LOGGER.warn("Unable to get config from " + CONFIGURATION_SERVICE_URL_FILE);
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
        public static void initialize(@Nullable final String configurationURL)
                throws ConfigurationException, MalformedURLException
        {
            final AbstractConfiguration systemConfiguration =
                    SystemConfiguration.newInstance();
            final AbstractConfiguration staticPropertiesConfiguration =
                    PropertiesConfiguration.newInstance(getStaticPropertyPaths());
            final AbstractConfiguration httpConfiguration = getHttpConfiguration(
                    ImmutableList.of(systemConfiguration, staticPropertiesConfiguration),
                    new IDefaultConfigurationURLProvider()
                    {
                        @Override
                        public String getDefaultConfigurationURL()
                        {
                            return configurationURL;
                        }
                    });

            DynamicConfiguration.Builder dynamicConfigurationBuilder =
                    DynamicConfiguration.builder()
                            .addConfiguration(systemConfiguration, "system")
                            .addConfiguration(staticPropertiesConfiguration, "static")
                            .addConfiguration(httpConfiguration, "http");

            DynamicConfiguration.initialize(dynamicConfigurationBuilder.build());
            LOGGER.debug("Client configuration initialized");
        }
    }

    private static List<String> getStaticPropertyPaths() {
        final List<String> staticPropertyPaths = newArrayList(
                STRINGS_RESOURCE,
                CONFIGURATION_RESOURCE);

        if (L.isStaging()) {
            // I KNOW! I'm introducing a new use of the exact thing that I am trying to kill,
            // L.isStaging(). Consistency is important and I don't want to introduce a new mechanism
            // for identifying the current environment until L.isStaging is killed completely.
            staticPropertyPaths.add(STAGING_CONFIGURATION_RESOURCE);
        }

        return staticPropertyPaths;
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
