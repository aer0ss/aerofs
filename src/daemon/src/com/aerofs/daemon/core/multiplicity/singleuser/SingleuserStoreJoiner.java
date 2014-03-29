/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.AbstractStoreJoiner;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.PendingRootDatabase;
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

    private final SingleuserStores _stores;
    private final StoreDeleter _sd;
    private final CfgRootSID _cfgRootSID;
    private final RitualNotificationServer _rns;
    private final SharedFolderAutoUpdater _lod;
    private final IMapSIndex2SID _sidx2sid;
    private final PendingRootDatabase _prdb;

    @Inject
    public SingleuserStoreJoiner(DirectoryService ds, SingleuserStores stores, ObjectCreator oc,
            ObjectDeleter od, ObjectSurgeon os, CfgRootSID cfgRootSID, RitualNotificationServer rns,
            SharedFolderAutoUpdater lod, StoreDeleter sd, IMapSIndex2SID sidx2sid,
            PendingRootDatabase prdb)
    {
        super(ds, os, oc, od);
        _sd = sd;
        _stores = stores;
        _cfgRootSID = cfgRootSID;
        _rns = rns;
        _lod = lod;
        _sidx2sid = sidx2sid;
        _prdb = prdb;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, String folderName, boolean external, Trans t)
            throws Exception
    {
        l.debug("join store sid:{} external:{} foldername:{}", sid, external, folderName);

        // ignore changes on the root store
        if (sid.equals(_cfgRootSID.get())) return;

        // make sure we don't have an old "leave request" queued
        _lod.removeLeaveCommandsFromQueue_(sid, t);

        // external folders are not auto-joined (user interaction is needed)
        if (external) {
            l.info("pending {} {}", sid, folderName);
            _prdb.addPendingRoot(sid, folderName, t);
            _rns.getRitualNotifier().sendNotification(newSharedFolderPendingNotification());
            return;
        }

        SIndex root = _stores.getUserRoot_();

        createAnchorIfNeeded_(sidx, sid, folderName, root, t);

        final Path path = new Path(_cfgRootSID.get(), folderName);
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

        l.info("leaving share: " + sidx + " " + sid);

        // remove any pending external root
        _prdb.removePendingRoot(sid, t);

        // delete any existing anchor (even expelled ones)
        Collection<SIndex> sidxs = _stores.getAll_();

        for (SIndex sidxWithanchor : sidxs) {
            final Path path = deleteAnchorIfNeeded_(sidxWithanchor, sid, t);

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
}
