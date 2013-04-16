/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.properties;

import com.aerofs.labeling.L;
import org.arrowfs.config.ArrowConfiguration;
import org.arrowfs.config.sources.DynamicPropertiesConfiguration;
import org.arrowfs.config.sources.PropertiesConfiguration;
import org.arrowfs.config.sources.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

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
            ArrowConfiguration.initialize(
                    ArrowConfiguration.builder()
                            .addConfiguration(
                                    PropertiesConfiguration.newInstance(getStaticPropertyPaths()),
                                    "static-properties")
                            .build());
            LOGGER.debug("Server configuration initialized");
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
            ArrowConfiguration.initialize(
                    ArrowConfiguration.builder()
                            .addConfiguration(SystemConfiguration.newInstance(), "system")
                            .addConfiguration(
                                    DynamicPropertiesConfiguration.newInstance(
                                            getDynamicPropertyPaths(absoluteRuntimeRoot), 60000),
                                    "dynamic-properties")
                            .addConfiguration(
                                    PropertiesConfiguration.newInstance(getStaticPropertyPaths()),
                                    "static-properties")
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
