/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.acl.ACLFilter;
import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.fs.IListLinkedAndExpelledSharedFolders;
import com.aerofs.daemon.core.notification.ISyncNotificationSubscriber;
import com.aerofs.daemon.core.polaris.fetch.IShareListener;
import com.aerofs.daemon.core.quota.IQuotaEnforcement;
import com.aerofs.daemon.core.quota.QuotaEnforcement;
import com.aerofs.daemon.core.status.ISyncStatusPropagator;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;

import org.slf4j.Logger;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class MultiuserModule extends AbstractModule
{
    private static final Logger l = Loggers.getLogger(MultiuserModule.class);

    @Override
    protected void configure()
    {
        l.info("multi user mode");

        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);

        binder().disableCircularProxies();

        bind(AbstractPathResolver.Factory.class).to(MultiuserPathResolver.Factory.class);

        bind(ACLFilter.class).to(MultiuserACLFilter.class);
        bind(IStoreJoiner.class).to(MultiuserStoreJoiner.class);

        bind(IQuotaEnforcement.class).to(QuotaEnforcement.class);

        bind(IListLinkedAndExpelledSharedFolders.class).to(MultiUserLinkedAndExpelledSharedFolders.class);

        bind(ISyncNotificationSubscriber.class).toInstance(new ISyncNotificationSubscriber() {});
        bind(ISyncStatusPropagator.class).toInstance(new ISyncStatusPropagator() {});
        newSetBinder(binder(), IShareListener.class);
    }
}