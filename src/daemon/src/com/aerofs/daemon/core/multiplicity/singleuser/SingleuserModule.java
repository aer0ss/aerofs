/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.IContentVersionListener;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.acl.EffectiveUserList;
import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.fs.IListLinkedAndExpelledSharedFolders;
import com.aerofs.daemon.core.notification.ISyncNotificationSubscriber;
import com.aerofs.daemon.core.notification.SyncNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.IShareListener;
import com.aerofs.daemon.core.polaris.submit.IContentAvailabilityListener;
import com.aerofs.daemon.core.quota.IQuotaEnforcement;
import com.aerofs.daemon.core.quota.NullQuotaEnforcement;
import com.aerofs.daemon.core.status.*;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.CfgSyncStatusEnabled;
import com.aerofs.lib.id.SIndex;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.aerofs.lib.guice.GuiceUtil.multibind;
import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class SingleuserModule extends AbstractModule
{
    private static final Logger l = Loggers.getLogger(SingleuserModule.class);

    @Override
    protected void configure()
    {
        l.info("single user mode");

        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(StoreHierarchy.class).to(SingleuserStoreHierarchy.class);
        bind(AbstractPathResolver.Factory.class).to(SingleuserPathResolver.Factory.class);

        bind(IStoreJoiner.class).to(SingleuserStoreJoiner.class);

        bind(IQuotaEnforcement.class).to(NullQuotaEnforcement.class);

        bind(IListLinkedAndExpelledSharedFolders.class)
                .to(SingleUserLinkedAndAdmittedSharedFolders.class);

        // single-user mode has no need to track effective users
        bind(EffectiveUserList.class)
                .toInstance(new EffectiveUserList(null, null) {
                    @Override
                    public List<UserID> getEffectiveList() { return null; }
                    @Override
                    public void storeAdded_(SIndex sidx) throws SQLException { }
                    @Override
                    public void storeRemoved_(SIndex sidx) throws SQLException { }
                });

        bind(IContentAvailabilityListener.class).toInstance(new IContentAvailabilityListener(){});
        if (new CfgSyncStatusEnabled().get()) {
            bind(ISyncStatusPropagator.class).to(SyncStatusPropagator.class);
            bind(ISyncNotificationSubscriber.class).to(SyncNotificationSubscriber.class);
            bind(SyncStatusChangeHandler.class).asEagerSingleton();
            bind(SyncStatusVerifier.class).asEagerSingleton();
            bind(SyncStatusPresenceListener.class).asEagerSingleton();
            multibind(binder(), IContentVersionListener.class, SyncStatusContentVersionListener.class);
            multibind(binder(), IShareListener.class, SyncStatusVerifier.class);
        } else {
            bind(ISyncNotificationSubscriber.class).toInstance(new ISyncNotificationSubscriber() {});
            bind(ISyncStatusPropagator.class).toInstance(new ISyncStatusPropagator() {});
            newSetBinder(binder(), IContentVersionListener.class);
            newSetBinder(binder(), IShareListener.class);
        }

        multibind(binder(), ICoreEventHandlerRegistrar.class, SingleuserEventHandlerRegistar.class);
    }
}