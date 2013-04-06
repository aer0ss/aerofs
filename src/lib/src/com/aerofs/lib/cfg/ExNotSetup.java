package com.aerofs.lib.cfg;

import com.aerofs.labeling.L;

/**
 * The application hasn't been set up yet.
 */
public class ExNotSetup extends Exception
{
    private static final long serialVersionUID = 1L;

    @Override
    public String getMessage()
    {
        return L.product() + " hasn't set up yet";
    }
}
