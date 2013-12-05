package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.admin.HdRelocateRootAnchor.CrossFSRelocator;
import com.aerofs.daemon.core.admin.HdRelocateRootAnchor.SameFSRelocator;
import com.aerofs.daemon.core.notification.ISnapshotableNotificationEmitter;
import com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema;
import com.aerofs.daemon.core.phy.linked.linker.IDeletionBuffer;
import com.aerofs.daemon.core.phy.linked.linker.ILinkerFilter;
import com.aerofs.daemon.core.phy.linked.linker.LinkerEventHandlerRegistar;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.lib.db.ISchema;
import com.google.inject.AbstractModule;

import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.linked.linker.Linker;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.google.inject.internal.Scoping;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

public class LinkedStorageModule extends AbstractModule
{
    private boolean _flat;

    public LinkedStorageModule(boolean flat)
    {
        _flat = flat;
    }

    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        multibind(binder(), ICoreEventHandlerRegistrar.class, LinkerEventHandlerRegistar.class);

        multibind(binder(), ISchema.class, LinkedStorageSchema.class);

        multibind(binder(), ISnapshotableNotificationEmitter.class, RepresentabilityHelper.class);

        bind(IPhysicalStorage.class).to(_flat
                ? FlatLinkedStorage.class
                : LinkedStorage.class);
        bind(ILinkerFilter.class).to(_flat
                ? ILinkerFilter.FilterUnderAnchor.class
                : ILinkerFilter.AcceptAll.class);

        bind(ILinker.class).to(Linker.class);
        bind(IDeletionBuffer.class).to(TimeoutDeletionBuffer.class);

        bind(SameFSRelocator.class).to(LinkedSameFSRelocator.class);
        bind(CrossFSRelocator.class).to(LinkedCrossFSRelocator.class);
    }
}
