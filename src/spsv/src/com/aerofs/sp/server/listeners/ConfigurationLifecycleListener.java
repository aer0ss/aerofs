package com.aerofs.sp.server.listeners;

import com.aerofs.lib.properties.Configuration;
import com.aerofs.lib.Util;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ConfigurationLifecycleListener
        implements ServletContextListener
{
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        try {
            Configuration.Server.initialize();
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
