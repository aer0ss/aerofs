/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first_launch;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.cfg.RootDatabase;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Map.Entry;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

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

    private final CfgStorageType _cfgStorageType;
    private final CfgDatabase _cfgDB;
    private final CfgAbsRoots _cfgAbsRoots;
    private final AccessibleStores _as;
    private final ScanProgressReporter _spr;
    private final ILinker _linker;
    private final CoreScheduler _sched;
    private final StoreCreator _sc;
    private final TransManager _tm;

    /**
     * We have to use an intermediate class to avoid introducing a circular dep:
     * FirstLaunch -> Linker -> MightCreate -> SharedFolderTagFileIcon -> FirstLaunch
     */
    public static class AccessibleStores
    {
        private ImmutableMap<SID, Boolean> _accessibleStores;

        public boolean contains(SID sid)
        {
            Boolean ext = _accessibleStores.get(sid);
            return ext != null && !ext;
        }

        void onFirstLaunchCompletion_()
        {
            _accessibleStores = ImmutableMap.of();
        }
    }

    @Inject
    public FirstLaunch(CfgDatabase cfgDB, ILinker linker, CoreScheduler sched, AccessibleStores as,
            ScanProgressReporter spr, CfgAbsRoots absRoots, CfgStorageType storageType,
            StoreCreator sc, TransManager tm)
    {
        _as = as;
        _spr = spr;
        _linker = linker;
        _sched = sched;
        _cfgDB = cfgDB;
        _cfgAbsRoots = absRoots;
        _cfgStorageType = storageType;
        _sc = sc;
        _tm = tm;
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
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                ScanCompletionCallback cb = new ScanCompletionCallback() {
                    @Override
                    public void done_()
                    {
                        l.info("done indexing");
                        onFirstLaunchCompletion_();

                        callback.done_();

                        try {
                            // RitualService responds with ExIndexing as long as FIRST_START is set
                            // so resetting it needs to be the *last* step of the FirstLaunch
                            _cfgDB.set(Key.FIRST_START, false);
                        } catch (SQLException e) {
                            SystemUtil.fatal(e);
                        }
                    }
                };

                l.info("start indexing");

                // first scan may not be necessary on some clients (most notably non-linked storage)
                // in which case we want to avoid the ACL fetch as the round-trip to SP introduces
                // latency that could degrade user's first impression
                if (_cfgStorageType.get() == StorageType.LINKED) {
                    fetchAccessibleStores_();
                    restoreRoots_();
                    _linker.scan(cb);
                } else {
                    cb.done_();
                }
            }
        }, 0);

        return true;
    }

    private void onFirstLaunchCompletion_()
    {
        _as.onFirstLaunchCompletion_();
        _spr.onFirstLaunchCompletion_();
    }

    private void restoreRoots_()
    {
        try {
            Trans t = _tm.begin_();
            try {
                for (Entry<SID, String> e : RootDatabase.loadSeed_().entrySet()) {
                    restoreRoot_(e.getKey(), e.getValue(), t);
                }
                t.commit_();
            } finally {
                t.end_();
            }
            RootDatabase.cleaupSeed_();
        } catch (Exception e) {
            l.info("failed to restore roots", e);
        }
    }

    private void restoreRoot_(SID sid, String absPath, Trans t) throws Exception
    {
        if (!SharedFolderTagFileAndIcon.isStoreRoot(sid, absPath)
                || _cfgAbsRoots.getNullable(sid) != null
                || _as._accessibleStores.get(sid) == null) {
            return;
        }
        l.info("restore ext root {} {}", sid, absPath);
        _linker.restoreRoot_(sid, absPath, t);
        _sc.createRootStore_(sid, "", t);
    }

    private void fetchAccessibleStores_()
    {
        try {
            SPBlockingClient sp = newMutualAuthClientFactory().create()
                    .signInRemote();
            ImmutableMap.Builder<SID, Boolean> stores = ImmutableMap.builder();
            for (PBStoreACL sacl : sp.getACL(0L).getStoreAclList()) {
                // the external flag is meaningless on TS
                stores.put(new SID(sacl.getStoreId()), sacl.getExternal() && !L.isMultiuser());
            }
            _as._accessibleStores = stores.build();
        } catch (Exception e) {
            _as._accessibleStores = ImmutableMap.of();
            l.warn("Unable to fetch accessible stores: " + Util.e(e));
        }
    }
}
