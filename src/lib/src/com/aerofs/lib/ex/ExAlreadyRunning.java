/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.lib.S;

public class ExAlreadyRunning extends ExAborted
{
    private static final long serialVersionUID = 1L;

    public ExAlreadyRunning()
    {
        super(S.PRODUCT + " is already running.");
    }
}
