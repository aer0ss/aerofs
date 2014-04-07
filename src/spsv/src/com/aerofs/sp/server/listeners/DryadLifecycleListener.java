/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.listeners;

import javax.servlet.ServletContextEvent;

public class DryadLifecycleListener extends ConfigurationLifecycleListener
{
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        super.contextInitialized(servletContextEvent);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent)
    {
        super.contextDestroyed(servletContextEvent);
    }
}
