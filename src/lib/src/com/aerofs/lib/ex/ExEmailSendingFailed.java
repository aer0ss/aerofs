/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

public class ExEmailSendingFailed extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExEmailSendingFailed(Throwable t)
    {
        super(t);
    }
}