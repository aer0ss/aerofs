package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.phy.linked.linker.IDeletionBuffer;
import com.aerofs.daemon.core.phy.linked.linker.LinkerEventHandlerRegistar;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer;
import com.aerofs.lib.guice.GuiceUtil;
import com.google.inject.AbstractModule;

import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.linked.linker.Linker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.google.inject.internal.Scoping;

public class LinkedStorageModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        GuiceUtil.multibind(binder(), ICoreEventHandlerRegistrar.class,
                LinkerEventHandlerRegistar.class);

        bind(IPhysicalStorage.class).to(LinkedStorage.class);
        bind(ILinker.class).to(Linker.class);
        bind(IDeletionBuffer.class).to(TimeoutDeletionBuffer.class);
    }
}
