/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.properties;

import com.aerofs.labeling.L;
import com.aerofs.config.DynamicConfiguration;
import com.aerofs.config.sources.DynamicPropertiesConfiguration;
import com.aerofs.config.sources.PropertiesConfiguration;
import com.aerofs.config.sources.SystemConfiguration;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.netflix.config.ConcurrentMapConfiguration;
import com.netflix.config.DynamicURLConfiguration;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang.StringUtils.isBlank;

/** author: Eric Schoonover <eric@aerofs.com> */
public final class Configuration
{
    /**
     * Provides the initialization logic for the various AeroFS services
     *
     * <p>
     * The following configuration sources are used (in order of preference):
     * <ol>
     *     <li>Runtime Configuration (for testing only)</li>
     *     <li>System Configuration (static, -D JVM parameters)</li>
     *     <li>TODO (eric) - Configuration Service (dynamic, HTTP)</li>
     *     <li>Classpath .properties Resources (static)</li>
     * </ol>
     * </p>
     */
    public static class Server
    {
        public static void initialize() {
            final AbstractConfiguration systemConfiguration = SystemConfiguration.newInstance();
            final AbstractConfiguration staticPropertiesConfiguration = PropertiesConfiguration.newInstance(
                    getStaticPropertyPaths());

            final AbstractConfiguration httpConfiguration = getHttpConfiguration(
                    ImmutableList.of(systemConfiguration, staticPropertiesConfiguration));

            DynamicConfiguration.initialize(DynamicConfiguration.builder()
                    .addConfiguration(SystemConfiguration.newInstance(), "system")
                    .addConfiguration(httpConfiguration, "http")
                    .addConfiguration(staticPropertiesConfiguration, "static-properties")
                    .build());
            LOGGER.info("Server configuration initialized: " + DynamicConfiguration.getInstance());
        }
    }

    private static List<String> getStaticPropertyPaths() {
        final List<String> staticPropertyPaths = newArrayList(STRINGS_RESOURCE, CONFIGURATION_RESOURCE);

        if (L.isStaging()) {
            // I KNOW! I'm introducing a new use of the exact thing that I am trying to kill,
            // L.isStaging(). Consistency is important and I don't want to introduce a new mechanism
            // for identifying the current environment until L.isStaging is killed completely
            staticPropertyPaths.add(STAGING_CONFIGURATION_RESOURCE);
        }

        return staticPropertyPaths;
    }

    private static AbstractConfiguration getHttpConfiguration(
            final Collection<AbstractConfiguration> configurationSources)
    {
        final Optional<String> configurationServiceUrl =
                getConfigurationServiceUrl(configurationSources);

        if (configurationServiceUrl.isPresent()) {
            return new ConcurrentMapConfiguration();
            // return empty configuration source is configurationServiceUrl is blank
        }

        return PropertiesConfiguration.newInstance(ImmutableList.of(configurationServiceUrl.get()));
        // (eric) in the future we may want to make this dynamic, here is how
        // return new DynamicURLConfiguration(0, 3600000, false, configurationServiceUrl);
        // this will reload the configuration from the HTTP service every hour
    }

    private static Optional<String> getConfigurationServiceUrl(
            final Collection<AbstractConfiguration> configurationSources)
    {
        // we allow the configuration service URL to be overridden by construction a temporary
        // configuration source from all other configuration sources. Then we lookup the
        // "config.service.url" key if any value has been defined it will be used.
        final CompositeConfiguration tempConfiguration =
                new CompositeConfiguration(configurationSources);

        // if not value has been defined in other configuration sources than lookup a default
        // value (may be null)
        final String configurationServiceUrl =
                tempConfiguration.getString("config.service.url",
                        getDefaultConfigurationServiceUrl());
        return Optional.fromNullable(configurationServiceUrl);
    }

    private static String getDefaultConfigurationServiceUrl() {
        // TODO (eric) resolve the default configuration URL somehow
        // leaving this for matt to complete as what exactly he wants is not well defined
        return null;
    }

    /**
     * Provides the initialization logic for the AeroFS/Team Server client configuration.
     *
     * <p>
     * The following configuration sources are used (in order of preference):
     * <ol>
     *     <li>Runtime Configuration (for testing only)</li>
     *     <li>System Configuration (static, -D JVM parameters)</li>
     *     <li>{RuntimeRoot}/aerofs.properties (dynamic, refresh interval)</li>
     *     <li>TODO (eric) - Configuration Database (dynamic, conf DB... JDBC)</li>
     *     <li>TODO (eric) - Configuration Service (dynamic, HTTP)</li>
     *     <li>Classpath .properties Resources (static)</li>
     * </ol>
     * </p>
     */
    public static class Client
    {
        public static void initialize(final String absoluteRuntimeRoot) {
            final AbstractConfiguration systemConfiguration = SystemConfiguration.newInstance();
            final AbstractConfiguration dynamicPropertiesConfiguration = DynamicPropertiesConfiguration.newInstance(
                    getDynamicPropertyPaths(absoluteRuntimeRoot), 60000);
            final AbstractConfiguration staticPropertiesConfiguration = PropertiesConfiguration.newInstance(
                    getStaticPropertyPaths());

            final AbstractConfiguration httpConfiguration = getHttpConfiguration(
                    ImmutableList.of(systemConfiguration, dynamicPropertiesConfiguration,
                            staticPropertiesConfiguration));

            DynamicConfiguration.initialize(DynamicConfiguration.builder()
                    .addConfiguration(systemConfiguration, "system")
                    .addConfiguration(dynamicPropertiesConfiguration, "dynamic-properties")
                    .addConfiguration(httpConfiguration, "http")
                    .addConfiguration(staticPropertiesConfiguration, "static-properties")
                    .build());
            LOGGER.debug("Client configuration initialized");
        }

        private static List<String> getDynamicPropertyPaths(final String absoluteRuntimeRoot) {
            final String aerofsPropertiesAbsolutePath =
                    new File(absoluteRuntimeRoot, "aerofs.properties").getAbsolutePath();
            return newArrayList( aerofsPropertiesAbsolutePath );
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    private static final String STRINGS_RESOURCE = "resources/strings.properties";
    private static final String CONFIGURATION_RESOURCE = "resources/configuration.properties";
    private static final String STAGING_CONFIGURATION_RESOURCE = "resources/configuration-stg.properties";
}
