/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import java.net.URLConnection;

public class NullURLConnectionConfigurator implements IURLConnectionConfigurator
{
    public static final IURLConnectionConfigurator NULL_URL_CONNECTION_CONFIGURATOR =
            new NullURLConnectionConfigurator();

    private NullURLConnectionConfigurator()
    {
        // Do nothing.
    }

    @Override
    public void configure(URLConnection connection)
    {
        // Do nothing.
    }
}