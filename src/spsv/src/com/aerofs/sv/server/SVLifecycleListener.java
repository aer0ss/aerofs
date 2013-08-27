/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sv.server;

import com.aerofs.lib.Util;
import com.aerofs.lib.properties.Configuration;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class SVLifecycleListener implements ServletContextListener
{
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        // This Stackoverflow answer describes why it is most appropriate to do global/application
        // wide initialization within contextInitialized http://stackoverflow.com/a/2364451/3957
        // initialize ConfigurationProperties.
        try {
            // NB: We must explicitly initialize configuration properties before accessing them (ie
            // through BaseParams, etc). This provides the Properties object that configuration
            // properties draw from. We need this because we access BaseParam in an intializer.
            Configuration.Server.initialize();
        } catch (Exception e) {
            throw new RuntimeException("Configuration server init error: " + Util.e(e));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        // empty
    }
}
