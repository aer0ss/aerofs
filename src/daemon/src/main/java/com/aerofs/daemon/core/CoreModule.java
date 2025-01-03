package com.aerofs.daemon.core;

import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.audit.client.IAuditorClient;
import com.aerofs.base.AuditParam;
import com.aerofs.base.TimerUtil;
import com.aerofs.daemon.core.activity.ClientAuditEventReporter;
import com.aerofs.daemon.core.activity.IClientAuditEventReporter;
import com.aerofs.daemon.core.activity.LegacyOutboundEventLogger;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.db.TamperingDetectionSchema;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryServiceImpl;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.expel.AbstractLogicalStagingArea;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.health_check.CoreProgressWatcher;
import com.aerofs.daemon.core.health_check.DeadlockDetector;
import com.aerofs.daemon.core.health_check.DiagnosticsDumper;
import com.aerofs.daemon.core.health_check.HealthCheckService;
import com.aerofs.daemon.core.launch_tasks.DaemonLaunchTasks;
import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.notification.ISnapshotableNotificationEmitter;
import com.aerofs.daemon.core.notification.PathStatusNotifier;
import com.aerofs.daemon.core.online_status.OnlineStatusNotifier;
import com.aerofs.daemon.core.polaris.db.PolarisSchema;
import com.aerofs.daemon.core.polaris.fetch.ApplyChange;
import com.aerofs.daemon.core.polaris.fetch.ApplyChangeImpl;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcherIterator;
import com.aerofs.daemon.core.polaris.fetch.DefaultFetchFilter;
import com.aerofs.daemon.core.polaris.submit.ContentAvailabilitySubmitter;
import com.aerofs.daemon.core.polaris.submit.ContentSubmitConflictHandler;
import com.aerofs.daemon.core.polaris.submit.DaemonContentConflictHandler;
import com.aerofs.daemon.core.polaris.submit.IContentAvailabilityListener;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.daemon.core.store.*;
import com.aerofs.daemon.core.transfers.download.Downloads;
import com.aerofs.daemon.core.transfers.download.IContentDownloads;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.lib.imc.QueueBasedIMCExecutor;
import com.aerofs.daemon.lib.db.*;
import com.aerofs.daemon.lib.db.trans.TransBoundaryChecker;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.db.ver.*;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.RoundTripTimes;
import com.aerofs.lib.NioChannelFactories;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.db.dbcw.SQLiteDBCW;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ssmp.SSMPConnection;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
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
        multibind(binder(), ICoreEventHandlerRegistrar.class, TransportEventHandlerRegistrar.class);

        bind(ICfgStore.class).to(CfgDatabase.class);

        bind(DirectoryService.class).to(DirectoryServiceImpl.class);
        bind(ObjectSurgeon.class).to(DirectoryServiceImpl.class);

        AuditParam auditParam = AuditParam.fromConfiguration();
        bind(AuditParam.class).toInstance(auditParam);

        bind(IPathResolver.class).to(DirectoryServiceImpl.class);
        bind(ContentProvider.class).to(DaemonContentProvider.class);
        bind(OutboundEventLogger.class).to(auditParam._enabled
                ? LegacyOutboundEventLogger.class
                : LegacyOutboundEventLogger.Noop.class);
        bind(IClientAuditEventReporter.class).to(auditParam._enabled
                ? ClientAuditEventReporter.class
                : ClientAuditEventReporter.Noop.class);

        bind(NewUpdatesSender.class).asEagerSingleton();

        bind(TransBoundaryChecker.class).to(TransManager.class);
        bind(IUnicastInputLayer.class).to(CoreProtocolReactor.class);

        bind(AbstractLogicalStagingArea.class).to(LogicalStagingArea.class);
        bind(IMapSIndex2SID.class).to(SIDMap.class);
        bind(IMapSID2SIndex.class).to(SIDMap.class);
        bind(IMetaDatabase.class).to(MetaDatabase.class);
        bind(IMetaDatabaseWalker.class).to(MetaDatabase.class);
        bind(IPulledDeviceDatabase.class).to(PulledDeviceDatabase.class);
        bind(IAliasDatabase.class).to(AliasDatabase.class);
        bind(IPrefixVersionDatabase.class).to(PrefixVersionDatabase.class);
        bind(IExpulsionDatabase.class).to(ExpulsionDatabase.class);
        bind(ICollectorFilterDatabase.class).to(CollectorFilterDatabase.class);
        bind(ISenderFilterDatabase.class).to(SenderFilterDatabase.class);
        bind(ISIDDatabase.class).to(SIDDatabase.class);
        bind(IStoreDatabase.class).to(StoreDatabase.class);
        bind(ICollectorStateDatabase.class).to(StoreDatabase.class);
        bind(IACLDatabase.class).to(ACLDatabase.class);
        bind(IActivityLogDatabase.class).to(ActivityLogDatabase.class);
        bind(IDID2UserDatabase.class).to(DID2UserDatabase.class);
        bind(IUserAndDeviceNameDatabase.class).to(UserAndDeviceNameDatabase.class);
        bind(IAuditDatabase.class).to(AuditDatabase.class);
        bind(IRoundTripTimes.class).toInstance(new RoundTripTimes());
        bind(IVersionUpdater.class).to(VersionUpdater.class);

        // we use multibindings to allow splitting DB schemas cleanly, only setting up
        // exactly as much as required depending on Module instantiation and preventing
        // schemas from leaking outside of the packages that actually use them
        multibind(binder(), ISchema.class, CoreSchema.class);
        multibind(binder(), ISchema.class, TamperingDetectionSchema.class);
        multibind(binder(), ISchema.class, PolarisSchema.class);

        bind(Store.Factory.class).to(DaemonPolarisStore.Factory.class);
        bind(NewUpdates.Impl.class).to(PhoenixNewUpdates.class);
        bind(IContentVersionControl.class).to(PolarisContentVersionControl.class);
        // client/SA behavioral differences
        bind(ApplyChange.Impl.class).to(ApplyChangeImpl.class);
        bind(IContentDownloads.class).to(Downloads.class);
        bind(Causality.class).to(PolarisCausality.class);
        bind(ContentFetcherIterator.Filter.class).to(DefaultFetchFilter.class);
        bind(ContentSubmitConflictHandler.class).to(DaemonContentConflictHandler.class);

        bind(IContentAvailabilityListener.class).to(ContentAvailabilitySubmitter.class);
        multibind(binder(), IContentVersionListener.class, ContentAvailabilitySubmitter.class);

        multibind(binder(), CoreProtocolReactor.Handler.class, GetContentRequest.class);
        multibind(binder(), CoreProtocolReactor.Handler.class, GetFilterRequest.class);

        multibind(binder(), HealthCheckService.ScheduledRunnable.class, CoreProgressWatcher.class);
        multibind(binder(), HealthCheckService.ScheduledRunnable.class, DeadlockDetector.class);
        multibind(binder(), HealthCheckService.ScheduledRunnable.class, DiagnosticsDumper.class);

        multibind(binder(), ISnapshotableNotificationEmitter.class, PathStatusNotifier.class);
        multibind(binder(), ISnapshotableNotificationEmitter.class, OnlineStatusNotifier.class);

        multibind(binder(), CoreProtocolReactor.Handler.class, NewUpdates.class);
        multibind(binder(), CoreProtocolReactor.Handler.class, UpdateSenderFilter.class);
        multibind(binder(), CoreProtocolReactor.Handler.class, RPC.class);

        // RunAtLeastOnce tasks can be run in any order so we use a set binder to simplify their
        // instantiation. However we don't want to leak the specific classes outside the package
        // hence the use of a static method
        DaemonLaunchTasks.bindTasks(binder());
    }

    @Provides @Singleton
    public IDBCW provideIDBCW(CfgCoreDatabaseParams dbParams)
    {
        return new SQLiteDBCW(dbParams.url(), dbParams.autoCommit(),
                    dbParams.sqliteExclusiveLocking(), dbParams.sqliteWALMode());
    }

    @Provides @Singleton
    public IIMCExecutor provideIIMCExecutor(CoreQueue q)
    {
        return new QueueBasedIMCExecutor(q);
    }

    @Provides
    public ServerSocketChannelFactory provideServerSocketChannelFactory()
    {
        return NioChannelFactories.getServerChannelFactory();
    }

    @Provides
    public ClientSocketChannelFactory provideClientSocketChannelFactory()
    {
        return NioChannelFactories.getClientChannelFactory();
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
    public IAuditorClient provideAuditorClient(CfgLocalUser user, CfgLocalDID did,
           ClientSSLEngineFactory factory)
    {
        return AuditorFactory.createAuthenticatedWithDeviceCert(user.get(), did.get(), factory);
    }

    @Provides @Singleton
    public SSMPConnection provideSSMPConnection(CfgLocalDID did, Timer timer,
                                                ClientSocketChannelFactory channelFactory,
                                                ClientSSLEngineFactory sslEngineFactory) {
        return new SSMPConnection(did.get(), SSMPConnection.getServerAddressFromConfiguration(),
                timer, channelFactory, sslEngineFactory::newSslHandler);
    }
}
