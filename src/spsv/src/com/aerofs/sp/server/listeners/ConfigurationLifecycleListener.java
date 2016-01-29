package com.aerofs.sp.server.listeners;

import com.aerofs.lib.configuration.ServerConfigurationLoader;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ConfigurationLifecycleListener
        implements ServletContextListener
{
    public ConfigurationLifecycleListener() {}

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        // This Stackoverflow answer describes why it is most appropriate to do global/application
        // wide initialization within contextInitialized http://stackoverflow.com/a/2364451/3957
        //
        // Initialize Configuration Properties.
        try {
            ServerConfigurationLoader.initialize("sp");
        } catch (Exception e) {
            throw new RuntimeException("Configuration server init error", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        // Nothing to do.
    }
}
