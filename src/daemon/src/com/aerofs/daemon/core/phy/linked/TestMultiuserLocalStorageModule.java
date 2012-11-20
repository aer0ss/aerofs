/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.linker.ILinker;
import com.aerofs.daemon.core.linker.ILinker.NullLinker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * TODO (WW) remove it after LocalBlockStorage is implemented
 */
public class TestMultiuserLocalStorageModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(IPhysicalStorage.class).to(LinkedStorage.class).in(Scopes.SINGLETON);
        bind(ILinker.class).to(NullLinker.class).in(Scopes.SINGLETON);
    }
}
