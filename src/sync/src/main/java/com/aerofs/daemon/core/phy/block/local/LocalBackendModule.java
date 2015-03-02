/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.local;

import com.aerofs.daemon.core.phy.block.BlockStorageModules.AbstractBackendModule;
import com.google.inject.Scopes;

public class LocalBackendModule extends AbstractBackendModule
{
    @Override
    protected void configure()
    {
        bindBackend().to(LocalBackend.class).in(Scopes.SINGLETON);
    }
}
