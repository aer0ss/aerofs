/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.resources;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

import javax.inject.Inject;

public class AbstractResource
{
    @Inject
    protected IIMCExecutor _imce;
}
