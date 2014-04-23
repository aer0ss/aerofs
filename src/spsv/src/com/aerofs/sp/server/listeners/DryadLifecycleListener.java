/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.listeners;

import com.aerofs.sp.server.SPVerkehrClientFactory;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

public class DryadLifecycleListener extends ConfigurationLifecycleListener
{
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent)
    {
        super.contextInitialized(servletContextEvent);

        ServletContext context = servletContextEvent.getServletContext();
        SPVerkehrClientFactory factory = new SPVerkehrClientFactory(context);

        VerkehrAdmin verkehr = factory.createVerkehrAdmin();
        verkehr.start();
        context.setAttribute(SPParam.VERKEHR_ADMIN_ATTRIBUTE, verkehr);
    }
}
