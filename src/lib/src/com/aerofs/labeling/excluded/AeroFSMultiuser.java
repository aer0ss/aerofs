/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling.excluded;

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
        return "AeroFS Team Server";
    }

    @Override
    public String productSpaceFreeName()
    {
        return "AeroFSTeamServer";
    }

    @Override
    public String productUnixName()
    {
        return "aerofsts";
    }

    @Override
    public int defaultPortbase()
    {
        return 60193;
    }

    @Override
    public String rootAnchorName()
    {
        return product() + " Storage";
    }
}
