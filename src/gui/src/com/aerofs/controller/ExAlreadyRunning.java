/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.labeling.L;

public class ExAlreadyRunning extends ExLaunchAborted
{
    private static final long serialVersionUID = 1L;

    public ExAlreadyRunning()
    {
        super(L.product() + " is already running.");
    }
}
