package com.aerofs.daemon.core.phy.linked;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import com.aerofs.daemon.core.linker.ILinker;
import com.aerofs.daemon.core.linker.Linker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;

public class LinkedStorageModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(IPhysicalStorage.class).to(LinkedStorage.class).in(Scopes.SINGLETON);
        bind(ILinker.class).to(Linker.class).in(Scopes.SINGLETON);
    }
}
