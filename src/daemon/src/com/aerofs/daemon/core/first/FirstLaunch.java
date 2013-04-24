/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

/**
 * This class is in charge of all logic that needs to be run only once: on the first launch of the
 * daemon after a fresh install.
 *
 * In particular it is in charge of triggering a first scan that will restore shared folders if any
 * valid tag files are found under the root anchor and the SID they point to is accessible to the
 * local user.
 */
public class FirstLaunch
{
    private static final Logger l = Loggers.getLogger(FirstLaunch.class);

    private final CfgDatabase _cfgDB;
    private final OIDGenerator _og;
    private final AccessibleStores _as;
    private final ScanProgressReporter _spr;
    private final ILinker _linker;
    private final CoreScheduler _sched;

    /**
     * We have to use an intermediate class to avoid introducing a circular dep:
     * FirstLaunch -> Linker -> MightCreate -> SharedFolderTagFileIcon -> FirstLaunch
     */
    public static class AccessibleStores
    {
        private ImmutableSet<SID> _accessibleStores;

        public boolean contains(SID sid)
        {
            return _accessibleStores.contains(sid);
        }

        void onFirstLaunchCompletion_()
        {
            _accessibleStores = ImmutableSet.of();
        }
    }

    @Inject
    public FirstLaunch(CfgDatabase cfgDB, ILinker linker, CoreScheduler sched, AccessibleStores as,
            OIDGenerator og, ScanProgressReporter spr)
    {
        _as = as;
        _og = og;
        _spr = spr;
        _linker = linker;
        _sched = sched;
        _cfgDB = cfgDB;
    }

    /**
     * Perform any first-launch-specific task, if necessary
     *
     * NB: the callback is NOT called if the method returns false
     *
     * @param callback a scan completion callback to finalize Core startup
     * @return whether first launch will be performed
     */
    public boolean onFirstLaunch_(final ScanCompletionCallback callback)
    {
        if (!_cfgDB.getBoolean(Key.FIRST_START)) {
            // need to make sure all helper classes that abstract away various aspects of first
            // launch logic perform their "regular" (i.e. non-first launch) behavior
            onFirstLaunchCompletion_();
            return false;
        }

        // NB: fetcAccessibleStore needs to be performed from a Core thread in case the SP sign-in
        // fails as it causes a Ritual notification to be sent
        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                fetchAccessibleStores_();

                l.info("start indexing");
                _linker.scan(new ScanCompletionCallback()
                {
                    @Override
                    public void done_()
                    {
                        l.info("done indexing");
                        onFirstLaunchCompletion_();

                        callback.done_();

                        try {
                            // reset FIRST_START flag last, as it is used to reject Ritual calls
                            _cfgDB.set(Key.FIRST_START, false);
                        } catch (SQLException e) {
                            SystemUtil.fatal(e);
                        }
                    }
                });
            }
        }, 0);

        return true;
    }

    private void onFirstLaunchCompletion_()
    {
        _as.onFirstLaunchCompletion_();
        _og.onFirstLaunchCompletion_();
        _spr.onFirstLaunchCompletion_();
    }

    private void fetchAccessibleStores_()
    {
        try {
            SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
            SPBlockingClient sp = fact.create_(Cfg.user());
            sp.signInRemote();
            ImmutableSet.Builder<SID> stores = ImmutableSet.builder();
            for (PBStoreACL sacl : sp.getACLExcludeExternal(0L).getStoreAclList()) {
                stores.add(new SID(sacl.getStoreId()));
            }
            _as._accessibleStores = stores.build();
        } catch (Exception e) {
            SystemUtil.fatal("Unable to fetch accessible stores: " + Util.e(e));
        }
    }
}
