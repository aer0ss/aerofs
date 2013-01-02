/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling.excluded;

import com.aerofs.labeling.AbstractDefaultLabeling;

/**
 * the labeling for regular (i.e. non-team-server) AeroFS clients
 */
public class AeroFSSingleuser extends AbstractDefaultLabeling
{
    @Override
    public boolean isMultiuser()
    {
        return false;
    }

    @Override
    public String product()
    {
        return "AeroFS";
    }

    @Override
    public String productSpaceFreeName()
    {
        return "AeroFS";
    }

    @Override
    public String productUnixName()
    {
        return "aerofs";
    }

    @Override
    public int defaultPortbase()
    {
        return 50193;
    }

    @Override
    public String rootAnchorName()
    {
        return product();
    }
}
