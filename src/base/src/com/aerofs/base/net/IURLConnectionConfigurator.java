/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import java.net.URLConnection;

/**
 * Interface for an object that adds custom configuration to a URL connection.
 */
public interface IURLConnectionConfigurator
{
    public void configure(URLConnection connection) throws Throwable;
}