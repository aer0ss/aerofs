/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta;

import com.aerofs.restless.Version;
import com.aerofs.rest.AbstractRestConfiguration;

public class SpartaConfiguration extends AbstractRestConfiguration
{
    @Override
    public boolean isSupportedVersion(Version version)
    {
        return version.compareTo(Sparta.HIGHEST_SUPPORTED_VERSION) <= 0;
    }
}
