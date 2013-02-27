/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.linker.ILinker;
import com.aerofs.daemon.core.linker.scanner.ScanCompletionCallback;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
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
    private static final Logger l = Util.l(FirstLaunch.class);

    private final AccessibleStores _as;
    private final ILinker _linker;
    private final CoreScheduler _sched;

    /**
     * We have to use an intermediate class to avoid introducing a circular dep:
     * FirstLaunch -> Linker -> MightCreate -> SharedFolderTagFileIcon -> FirstLaunch
     */
    public static class AccessibleStores
    {
        private ImmutableSet<SID> _accessibleStores = ImmutableSet.of();

        public boolean contains(SID sid)
        {
            return _accessibleStores.contains(sid);
        }
    }

    @Inject
    public FirstLaunch(ILinker linker, CoreScheduler sched, AccessibleStores as)
    {
        _as = as;
        _linker = linker;
        _sched = sched;
    }

    public boolean isFirstLaunch_()
    {
        return Cfg.db().getBoolean(Key.FIRST_START);
    }

    /**
     * Perform any first-launch-specific task
     * @param callback a scan completion callback that finalizes Core startup
     */
    void onFirstLaunch_(final ScanCompletionCallback callback)
    {
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
                        _as._accessibleStores = ImmutableSet.of();

                        callback.done_();

                        try {
                            // reset FIRST_START flag last, as it is used to reject Ritual calls
                            Cfg.db().set(Key.FIRST_START, false);
                        } catch (SQLException e) {
                            SystemUtil.fatal(e);
                        }
                    }
                });
            }
        }, 0);
    }

    private void fetchAccessibleStores_()
    {
        try {
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            ImmutableSet.Builder<SID> stores = ImmutableSet.builder();
            for (PBStoreACL sacl : sp.getACL(0L).getStoreAclList()) {
                stores.add(new SID(sacl.getStoreId()));
            }
            _as._accessibleStores = stores.build();
        } catch (Exception e) {
            SystemUtil.fatal("Unable to fetch accessible stores: " + Util.e(e));
        }
    }
}
