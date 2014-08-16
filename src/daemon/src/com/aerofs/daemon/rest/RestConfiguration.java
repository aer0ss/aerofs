/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest;

import com.aerofs.restless.Version;
import com.aerofs.rest.AbstractRestConfiguration;

public class RestConfiguration extends AbstractRestConfiguration
{
    @Override
    public boolean isSupportedVersion(Version version)
    {
        return version.compareTo(RestService.HIGHEST_SUPPORTED_VERSION) <= 0;
    }
}
