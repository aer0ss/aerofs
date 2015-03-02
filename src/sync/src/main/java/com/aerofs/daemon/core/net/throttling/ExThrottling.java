/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.throttling;

class ExThrottling extends Exception
{
    private static final long serialVersionUID = 1;

    public ExThrottling(String reason)
    {
        super(reason);
    }

    ExThrottling(String reason, Throwable cause)
    {
        super(reason, cause);
    }
}