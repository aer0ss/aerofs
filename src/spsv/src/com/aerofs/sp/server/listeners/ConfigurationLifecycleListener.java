package com.aerofs.sp.server.listeners;

import com.aerofs.base.config.PropertiesHelper;
import com.aerofs.lib.properties.Configuration;
import com.aerofs.lib.Util;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ConfigurationLifecycleListener
        implements ServletContextListener
{
    private final String _pathToPropertiesFile;

    public ConfigurationLifecycleListener()
    {
        _pathToPropertiesFile = null;
    }

    protected ConfigurationLifecycleListener(String pathToPropertiesFile)
    {
        _pathToPropertiesFile = pathToPropertiesFile;
    }

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        // This Stackoverflow answer describes why it is most appropriate to do global/application
        // wide initialization within contextInitialized http://stackoverflow.com/a/2364451/3957
        //
        // Initialize Configuration Properties.
        try {
            if (_pathToPropertiesFile != null) {
                Configuration.Server.initialize(
                        PropertiesHelper.readPropertiesFromFile(_pathToPropertiesFile));
            } else {
                Configuration.Server.initialize();
            }
        } catch (Exception e) {
            throw new RuntimeException("Configuration server init error: " + Util.e(e));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        // Nothing to do.
    }
}
