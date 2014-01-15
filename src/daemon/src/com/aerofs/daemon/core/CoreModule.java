package com.aerofs.daemon.core;

import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.audit.client.IAuditorClient;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.analytics.IAnalyticsPlatformProperties;
import com.aerofs.daemon.core.db.TamperingDetectionSchema;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryServiceImpl;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.launch_tasks.DaemonLaunchTasks;
import com.aerofs.daemon.core.notification.ISnapshotableNotificationEmitter;
import com.aerofs.daemon.core.notification.PathStatusNotifier;
import com.aerofs.daemon.core.online_status.OnlineStatusNotifier;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.lib.db.ACLDatabase;
import com.aerofs.daemon.lib.db.ActivityLogDatabase;
import com.aerofs.daemon.lib.db.AliasDatabase;
import com.aerofs.daemon.lib.db.AuditDatabase;
import com.aerofs.daemon.lib.db.CollectorFilterDatabase;
import com.aerofs.daemon.lib.db.CollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.CoreSchema;
import com.aerofs.daemon.lib.db.DID2UserDatabase;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.daemon.lib.db.IACLDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IAliasDatabase;
import com.aerofs.daemon.lib.db.IAuditDatabase;
import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.IExpulsionDatabase;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IMetaDatabaseWalker;
import com.aerofs.daemon.lib.db.IPulledDeviceDatabase;
import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.daemon.lib.db.ISenderFilterDatabase;
import com.aerofs.daemon.lib.db.IStoreContributorsDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.IUserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.PulledDeviceDatabase;
import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.SenderFilterDatabase;
import com.aerofs.daemon.lib.db.StoreContributorsDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.daemon.lib.db.SyncStatusDatabase;
import com.aerofs.daemon.lib.db.UserAndDeviceNameDatabase;
import com.aerofs.daemon.lib.db.ver.IImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.INativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.IPrefixVersionDatabase;
import com.aerofs.daemon.lib.db.ver.ImmigrantVersionDatabase;
import com.aerofs.daemon.lib.db.ver.NativeVersionDatabase;
import com.aerofs.daemon.lib.db.ver.PrefixVersionDatabase;
import com.aerofs.lib.ChannelFactories;
import com.aerofs.lib.analytics.DesktopAnalyticsProperties;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.internal.Scoping;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

public class CoreModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        multibind(binder(), ICoreEventHandlerRegistrar.class, CoreEventHandlerRegistrar.class);

        bind(DirectoryService.class).to(DirectoryServiceImpl.class);
        bind(ObjectSurgeon.class).to(DirectoryServiceImpl.class);

        bind(IMapSIndex2SID.class).to(SIDMap.class);
        bind(IMapSID2SIndex.class).to(SIDMap.class);
        bind(INativeVersionDatabase.class).to(NativeVersionDatabase.class);
        bind(IImmigrantVersionDatabase.class).to(ImmigrantVersionDatabase.class);
        bind(IMetaDatabase.class).to(MetaDatabase.class);
        bind(IMetaDatabaseWalker.class).to(MetaDatabase.class);
        bind(IPulledDeviceDatabase.class).to(PulledDeviceDatabase.class);
        bind(IAliasDatabase.class).to(AliasDatabase.class);
        bind(IPrefixVersionDatabase.class).to(PrefixVersionDatabase.class);
        bind(IExpulsionDatabase.class).to(ExpulsionDatabase.class);
        bind(ICollectorSequenceDatabase.class).to(CollectorSequenceDatabase.class);
        bind(ICollectorFilterDatabase.class).to(CollectorFilterDatabase.class);
        bind(ISenderFilterDatabase.class).to(SenderFilterDatabase.class);
        bind(ISIDDatabase.class).to(SIDDatabase.class);
        bind(IStoreDatabase.class).to(StoreDatabase.class);
        bind(IStoreContributorsDatabase.class).to(StoreContributorsDatabase.class);
        bind(IACLDatabase.class).to(ACLDatabase.class);
        bind(IActivityLogDatabase.class).to(ActivityLogDatabase.class);
        bind(IDID2UserDatabase.class).to(DID2UserDatabase.class);
        bind(IUserAndDeviceNameDatabase.class).to(UserAndDeviceNameDatabase.class);
        bind(ISyncStatusDatabase.class).to(SyncStatusDatabase.class);
        bind(IAuditDatabase.class).to(AuditDatabase.class);
        bind(IAnalyticsPlatformProperties.class).to(DesktopAnalyticsProperties.class);

        // we use multibindings to allow splitting DB schemas cleanly, only setting up
        // exactly as much as required depending on Module instantiation and preventing
        // schemas from leaking outside of the packages that actually use them
        multibind(binder(), ISchema.class, CoreSchema.class);
        multibind(binder(), ISchema.class, TamperingDetectionSchema.class);

        multibind(binder(), ISnapshotableNotificationEmitter.class, PathStatusNotifier.class);
        multibind(binder(), ISnapshotableNotificationEmitter.class, OnlineStatusNotifier.class);

        // RunAtLeastOnce tasks can be run in any order so we use a set binder to simplify their
        // instanciation. However we don't want to leak the specific classes outside the package
        // hence the use of a static method
        DaemonLaunchTasks.bindTasks(binder());
    }

    @Provides
    public CoreIMCExecutor provideCoreIMCExecutor(CoreQueue q)
    {
        return new CoreIMCExecutor(new QueueBasedIMCExecutor(q));
    }

    @Provides
    public ServerSocketChannelFactory provideServerSocketChannelFactory()
    {
        return ChannelFactories.getServerChannelFactory();
    }

    @Provides
    public ClientSocketChannelFactory provideClientSocketChannelFactory()
    {
        return ChannelFactories.getClientChannelFactory();
    }

    @Provides
    public Timer provideTimer()
    {
        return TimerUtil.getGlobalTimer();
    }

    @Provides
    public IOSUtil provideIOSUtil()
    {
        return OSUtil.get();
    }

    @Provides
    public IAuditorClient provideAuditorClient()
    {
        return AuditorFactory.createAuthenticated();
    }
}
