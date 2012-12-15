/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.lib.L;

public class ExAlreadyRunning extends ExAborted
{
    private static final long serialVersionUID = 1L;

    public ExAlreadyRunning()
    {
        super(L.PRODUCT + " is already running.");
    }
}
