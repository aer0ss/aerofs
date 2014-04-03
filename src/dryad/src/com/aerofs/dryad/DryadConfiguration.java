/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.aerofs.base.Version;
import com.aerofs.rest.AbstractRestConfiguration;

public class DryadConfiguration extends AbstractRestConfiguration
{
    @Override
    public boolean isSupportedVersion(Version version)
    {
        // Internal service, don't care about versions.
        return true;
    }
}