package com.aerofs.daemon.core;

import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.daemon.core.linker.IDeletionBuffer;
import com.aerofs.daemon.core.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.lib.db.ACLDatabase;
import com.aerofs.daemon.lib.db.ActivityLogDatabase;
import com.aerofs.daemon.lib.db.AliasDatabase;
import com.aerofs.daemon.lib.db.CollectorFilterDatabase;
import com.aerofs.daemon.lib.db.CollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.DID2UserDatabase;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IAliasDatabase;
import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.IExpulsionDatabase;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.ISenderFilterDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.IUserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.PulledDeviceDatabase;
import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.SenderFilterDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.daemon.lib.db.UserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.ver.IImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.IPrefixVersionDatabase;
import com.aerofs.daemon.lib.db.ver.ImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.NativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.PrefixVersionDatabase;
import com.aerofs.lib.guice.GuiceUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.internal.Scoping;
import com.google.inject.multibindings.Multibinder;

public class CoreModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        GuiceUtil.multiBind(binder(), ICoreEventHandlerRegistrar.class,
                CoreEventHandlerRegistrar.class);

        bind(IMapSIndex2SID.class).to(SIDMap.class);
        bind(IMapSID2SIndex.class).to(SIDMap.class);
        bind(IDeletionBuffer.class).to(TimeoutDeletionBuffer.class);
        bind(INativeVersionDatabase.class).to(NativeVersionDatabase.class);
        bind(IImmigrantVersionDatabase.class).to(ImmigrantVersionDatabase.class);
        bind(IMetaDatabase.class).to(MetaDatabase.class);
        bind(IPulledDeviceDatabase.class).to(PulledDeviceDatabase.class);
        bind(IAliasDatabase.class).to(AliasDatabase.class);
        bind(IPrefixVersionDatabase.class).to(PrefixVersionDatabase.class);
        bind(IExpulsionDatabase.class).to(ExpulsionDatabase.class);
        bind(ICollectorSequenceDatabase.class).to(CollectorSequenceDatabase.class);
        bind(ICollectorFilterDatabase.class).to(CollectorFilterDatabase.class);
        bind(ISenderFilterDatabase.class).to(SenderFilterDatabase.class);
        bind(ISIDDatabase.class).to(SIDDatabase.class);
        bind(IStoreDatabase.class).to(StoreDatabase.class);
        bind(IACLDatabase.class).to(ACLDatabase.class);
        bind(IActivityLogDatabase.class).to(ActivityLogDatabase.class);
        bind(IDID2UserDatabase.class).to(DID2UserDatabase.class);
        bind(IUserAndDeviceNameDatabase.class).to(UserAndDeviceNameDatabase.class);
        bind(ISyncStatusDatabase.class).to(SyncStatusDatabase.class);

        // we use multibindings to allow splitting DB schemas cleanly, only setting up
        // exactly as much as required depending on Module instantiation and preventing
        // schemas from leaking outside of the packages that actually use them
        Multibinder.newSetBinder(binder(), ISchema.class).addBinding().to(CoreSchema.class);
    }

    @Provides
    public CoreIMCExecutor provideCoreIMCExecutor(CoreQueue q)
    {
        return new CoreIMCExecutor(new QueueBasedIMCExecutor(q));
    }
}
