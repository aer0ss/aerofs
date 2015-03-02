/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.config.ConfigurationProperties;
import org.junit.rules.ExternalResource;

import java.util.Properties;

/**
 * A JUnit {@link org.junit.rules.ExternalResource} that sets up
 * the AeroFS properties subsystem to use the default (i.e hardcoded) propertis.
 * This should be enabled as a {@link org.junit.ClassRule} within tests.
 */
public final class ConfigurationPropertiesResource extends ExternalResource
{
    public ConfigurationPropertiesResource()
    {
        ConfigurationProperties.setProperties(new Properties());
    }
}
