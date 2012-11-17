/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.local;

import com.aerofs.daemon.core.phy.block.AbstractBlockStorageModule;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.google.inject.Scopes;

public class LocalStorageModule extends AbstractBlockStorageModule
{
    @Override
    protected void configureBackend()
    {
        bind(IBlockStorageBackend.class).to(LocalBackend.class).in(Scopes.SINGLETON);
    }
}
