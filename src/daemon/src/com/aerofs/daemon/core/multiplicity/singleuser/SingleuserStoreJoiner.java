/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.AbstractStoreJoiner;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.inject.Inject;

import java.util.Collection;

import static com.aerofs.daemon.core.notification.Notifications.newSharedFolderJoinNotification;
import static com.aerofs.daemon.core.notification.Notifications.newSharedFolderKickoutNotification;
import static com.aerofs.daemon.core.notification.Notifications.newSharedFolderPendingNotification;

public class SingleuserStoreJoiner extends AbstractStoreJoiner
{
    private final SingleuserStoreHierarchy _stores;
    private final StoreDeleter _sd;
    private final CfgRootSID _cfgRootSID;
    private final RitualNotificationServer _rns;
    private final SharedFolderAutoUpdater _lod;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final UnlinkedRootDatabase _urdb;

    @Inject
    public SingleuserStoreJoiner(DirectoryService ds, SingleuserStoreHierarchy stores, ObjectCreator oc,
            ObjectDeleter od, ObjectSurgeon os, CfgRootSID cfgRootSID, RitualNotificationServer rns,
            SharedFolderAutoUpdater lod, StoreDeleter sd, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx, UnlinkedRootDatabase urdb)
    {
        super(ds, os, oc, od);
        _sd = sd;
        _stores = stores;
        _cfgRootSID = cfgRootSID;
        _rns = rns;
        _lod = lod;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _urdb = urdb;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, StoreInfo info, Trans t)
            throws Exception
    {
        l.debug("join store sid:{} external:{} foldername:{}", sid, info._external, info._name);

        // ignore changes on the root store
        if (sid.equals(_cfgRootSID.get())) return;

        // make sure we don't have an old "leave request" queued
        _lod.removeLeaveCommandsFromQueue_(sid, t);

        // external folders are not auto-joined (user interaction is needed)
        if (info._external) {
            l.info("pending {} {}", sid, info._name);
            _urdb.addUnlinkedRoot(sid, info._name, t);
            _rns.getRitualNotifier().sendNotification(newSharedFolderPendingNotification());
            return;
        }

        createAnchorIfNeeded_(sidx, sid, info._name, _sid2sidx.get_(_cfgRootSID.get()), t);

        final Path path = new Path(_cfgRootSID.get(), info._name);
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                _rns.getRitualNotifier().sendNotification(newSharedFolderJoinNotification(path));
            }
        });

    }

    @Override
    public void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception
    {
        // ignore changes on the root store
        if (sid.equals(_cfgRootSID.get())) return;

        l.info("leaving share: {} {}", sidx, sid);

        // remove any unlinked external root
        _urdb.removeUnlinkedRoot(sid, t);

        // delete any existing anchor (even expelled ones)
        Collection<SIndex> sidxs = _stores.getAll_();

        for (SIndex sidxWithAnchor : sidxs) {
            final Path path = deleteAnchorIfNeeded_(sidxWithAnchor, sid, t);

            if (path == null) continue;

            t.addListener_(new AbstractTransListener()
            {
                @Override
                public void committed_()
                {
                    _rns.getRitualNotifier()
                            .sendNotification(newSharedFolderKickoutNotification(path));
                }
            });
        }

        // special treatment for root stores
        if (_sidx2sid.getNullable_(sidx) != null && _stores.isRoot_(sidx)) {
            _sd.deleteRootStore_(sidx, PhysicalOp.APPLY, t);
        }
    }

    @Override
    public boolean onMembershipChange_(SIndex sidx, StoreInfo info)
    {
        return true;
    }
}
