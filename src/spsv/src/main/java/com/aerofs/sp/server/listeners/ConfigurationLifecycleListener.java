package com.aerofs.sp.server.listeners;

import com.aerofs.base.ContainerUtil;
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
            ContainerUtil.waitPort("config.service", "5434");
            ServerConfigurationLoader.initialize("sp");
        } catch (Exception e) {
            throw new RuntimeException("Configuration server init error", e);
        }
        ContainerUtil.barrier();
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        // Nothing to do.
    }
}
