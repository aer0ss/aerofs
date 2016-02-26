/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.core.acl.EffectiveUserList;
import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.fs.IListLinkedAndExpelledSharedFolders;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullImmigrantDetector;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullEmigrantDetector;
import com.aerofs.daemon.core.multiplicity.multiuser.migration.NullEmigrantTargetSIDLister;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.EmigrantDetector;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.EmigrantTargetSIDLister;
import com.aerofs.daemon.core.multiplicity.singleuser.migration.SingleuserImmigrantDetector;
import com.aerofs.daemon.core.quota.IQuotaEnforcement;
import com.aerofs.daemon.core.quota.NullQuotaEnforcement;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SIndex;
import com.google.inject.AbstractModule;
import com.google.inject.internal.Scoping;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.List;

import static com.aerofs.lib.guice.GuiceUtil.multibind;

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

        if (new CfgUsePolaris().get()) {
            bind(IEmigrantTargetSIDLister.class).to(NullEmigrantTargetSIDLister.class);
            bind(IEmigrantDetector.class).to(NullEmigrantDetector.class);
            bind(ImmigrantDetector.class).to(NullImmigrantDetector.class);
        } else {
            bind(IEmigrantTargetSIDLister.class).to(EmigrantTargetSIDLister.class);
            bind(IEmigrantDetector.class).to(EmigrantDetector.class);
            bind(ImmigrantDetector.class).to(SingleuserImmigrantDetector.class);
        }
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

        multibind(binder(), ICoreEventHandlerRegistrar.class, SingleuserEventHandlerRegistar.class);
    }
}