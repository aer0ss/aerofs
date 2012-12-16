/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling.impl;

import com.aerofs.labeling.AbstractDefaultLabeling;

/**
 * the labeling for regular (i.e. non-team-server) AeroFS clients
 */
public class AeroFSMultiuser extends AbstractDefaultLabeling
{
    @Override
    public boolean isMultiuser()
    {
        return true;
    }

    @Override
    public String product()
    {
        return super.product() + " Team Server";
    }

    @Override
    public String productUnixName()
    {
        return super.productUnixName() + "ts";
    }

    @Override
    public int defaultPortbase()
    {
        return 60193;
    }
}
