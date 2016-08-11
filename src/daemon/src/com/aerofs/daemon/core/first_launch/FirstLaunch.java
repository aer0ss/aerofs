/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first_launch;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.acl.ACLFilter;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.phy.ScanCompletionCallback;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.proto.Sp.GetACLReply.PBStoreACL;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.lib.cfg.CfgDatabase.FIRST_START;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static com.google.common.base.Preconditions.checkState;

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
    private final CfgRootSID _cfgRootSID;
    private final CfgAbsRoots _cfgAbsRoots;
    private final AccessibleStores _as;
    private final ScanProgressReporter _spr;
    private final ILinker _linker;
    private final CoreQueue _q;
    private final StoreCreator _sc;
    private final LocalACL _lacl;
    private final ACLFilter _filter;
    private final IMapSID2SIndex _sid2sidx;
    private final TransManager _tm;
    private final InjectableFile.Factory _factFile;

    /**
     * We have to use an intermediate class to avoid introducing a circular dep:
     * FirstLaunch -> Linker -> MightCreate -> SharedFolderTagFileIcon -> FirstLaunch
     */
    public static class AccessibleStores
    {
        private ImmutableMap<SID, PBStoreACL> _accessibleStores;

        public boolean contains(SID sid)
        {
            // the external flag is meaningless on TS
            PBStoreACL acl = _accessibleStores.get(sid);
            return acl != null && (L.isMultiuser() || !acl.getExternal());
        }

        void onFirstLaunchCompletion_()
        {
            _accessibleStores = ImmutableMap.of();
        }
    }

    @Inject
    public FirstLaunch(CfgDatabase cfgDB, ILinker linker, CoreQueue q, AccessibleStores as,
            ScanProgressReporter spr, CfgAbsRoots absRoots, CfgStorageType storageType,
            CfgRootSID rootSID, StoreCreator sc, IMapSID2SIndex sid2sidx, TransManager tm,
            LocalACL lacl, ACLFilter filter, InjectableFile.Factory factFile)
    {
        _as = as;
        _spr = spr;
        _linker = linker;
        _q = q;
        _cfgDB = cfgDB;
        _cfgRootSID = rootSID;
        _cfgAbsRoots = absRoots;
        _cfgStorageType = storageType;
        _sid2sidx = sid2sidx;
        _sc = sc;
        _tm = tm;
        _lacl = lacl;
        _filter = filter;
        _factFile = factFile;
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
        if (!_cfgDB.getBoolean(FIRST_START)) {
            // need to make sure all helper classes that abstract away various aspects of first
            // launch logic perform their "regular" (i.e. non-first launch) behavior
            onFirstLaunchCompletion_();
            return false;
        }

        // NB: fetchAccessibleStore needs to be performed from a Core thread in case the SP sign-in
        // fails as it causes a Ritual notification to be sent
        checkState(_q.enqueue(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                ScanCompletionCallback cb = () -> {
                    l.info("done indexing");
                    onFirstLaunchCompletion_();

                    callback.done_();

                    try {
                        // RitualService responds with ExIndexing as long as FIRST_START is set
                        // so resetting it needs to be the *last* step of the FirstLaunch
                        _cfgDB.set(FIRST_START, false);
                    } catch (SQLException e) {
                        SystemUtil.fatal(e);
                    }
                };

                if (!L.isMultiuser()) {
                    createUserRootStoreIfNeeded_();
                }

                l.info("start indexing");

                // first scan may not be necessary on some clients (most notably non-linked storage)
                // in which case we want to avoid the ACL fetch as the round-trip to SP introduces
                // latency that could degrade user's first impression
                if (_cfgStorageType.get() == StorageType.LINKED) {
                    fetchAccessibleStores_();
                    restoreRoots_();
                    _linker.scan_(cb);
                } else {
                    cb.done_();
                }
            }
        }, Prio.HI));

        return true;
    }

    private void onFirstLaunchCompletion_()
    {
        _as.onFirstLaunchCompletion_();
        _spr.onFirstLaunchCompletion_();
    }

    private void createUserRootStoreIfNeeded_()
    {
        try {
            if (_sid2sidx.getNullable_(_cfgRootSID.get()) != null) return;
            try (Trans t = _tm.begin_()) {
                _sc.createRootStore_(_cfgRootSID.get(), "", t);
                t.commit_();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void restoreRoots_()
    {
        try {
            try (Trans t = _tm.begin_()) {
                for (Entry<SID, String> e : RootDatabase.loadSeed_().entrySet()) {
                    restoreRoot_(e.getKey(), e.getValue(), t);
                }
                t.commit_();
            }
            RootDatabase.cleaupSeed_();
        } catch (Exception e) {
            l.info("failed to restore roots", e);
        }
    }

    private void restoreRoot_(SID sid, String absPath, Trans t) throws Exception
    {
        PBStoreACL acl = _as._accessibleStores.get(sid);
        if (!SharedFolderTagFileAndIcon.isStoreRoot(sid, _factFile.create(absPath))
                || _cfgAbsRoots.getNullable(sid) != null
                || acl == null) {
            return;
        }
        Map<UserID, Permissions> perms = new HashMap<>();
        acl.getSubjectPermissionsList().forEach(pb -> {
            try {
                perms.put(UserID.fromExternal(pb.getSubject()),
                        Permissions.fromPB(pb.getPermissions()));
            } catch (Exception e) {
                l.warn("invalid ACL entry", e);
            }
        });
        // honor acl filter
        if (!_filter.shouldKeep_(perms.keySet())) {
            l.info("skip restore {} {}", sid, absPath);
            return;
        }
        l.info("restore ext root {} {}", sid, absPath);
        SIndex sidx = _sid2sidx.getAbsent_(sid, t);
        // set ACLs before restoring root for consistency with auto-join
        // crucially, failing to do that would break EffectiveUserList
        _lacl.set_(sidx, perms, t);
        _linker.restoreRoot_(sid, absPath, t);
        _sc.createRootStore_(sid, acl.getName(), t);
    }

    private void fetchAccessibleStores_()
    {
        try {
            l.debug("signing");
            SPBlockingClient sp = newMutualAuthClientFactory().create()
                    .signInRemote();
            l.debug("get acl");
            ImmutableMap.Builder<SID, PBStoreACL> stores = ImmutableMap.builder();
            for (PBStoreACL sacl : sp.getACL(0L).getStoreAclList()) {
                stores.put(new SID(BaseUtil.fromPB(sacl.getStoreId())), sacl);
            }
            _as._accessibleStores = stores.build();
        } catch (Exception e) {
            _as._accessibleStores = ImmutableMap.of();
            l.warn("Unable to fetch accessible stores", BaseLogUtil.suppress(e));
        }
    }
}
