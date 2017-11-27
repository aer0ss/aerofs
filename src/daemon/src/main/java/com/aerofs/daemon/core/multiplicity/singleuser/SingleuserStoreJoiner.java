/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.ids.OID;
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
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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
    private final PolarisAsyncClient _polaris;
    private final RemoteLinkDatabase _rldb;

    private final Executor _sameThread = MoreExecutors.sameThreadExecutor();

    @Inject
    public SingleuserStoreJoiner(DirectoryService ds, SingleuserStoreHierarchy stores, ObjectCreator oc,
            ObjectDeleter od, ObjectSurgeon os, CfgRootSID cfgRootSID, RitualNotificationServer rns,
            SharedFolderAutoUpdater lod, StoreDeleter sd, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx, UnlinkedRootDatabase urdb,
            PolarisAsyncClient polaris, RemoteLinkDatabase rldb)
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
        _polaris = polaris;
        _rldb = rldb;
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

        SIndex rootSidx = _sid2sidx.get_(_cfgRootSID.get());

        OID anchor = SID.storeSID2anchorOID(sid);
        if (_rldb.getParent_(rootSidx, anchor) != null) {
            l.info("anchor already exists {}", sid);
            return;
        }

        if (_rldb.getParent_(rootSidx, SID.anchorOID2folderOID(anchor)) != null) {
            l.info("original folder already exists {}", sid);
            return;
        }

        // create anchor on polaris directly
        LocalChange c = new LocalChange();
        c.type = Type.INSERT_CHILD;
        c.child = anchor.toStringFormal();
        c.childName = info._name;
        c.childObjectType = ObjectType.STORE;
        // pick a locally non-conflicting name to ensure eventual success
        while (_ds.getChild_(rootSidx, OID.ROOT, c.childName) != null) {
            c.childName = FileUtil.nextFileName(c.childName);
        }
        SettableFuture<Void> f = SettableFuture.create();

        _polaris.post("/objects/" + _cfgRootSID.get().toStringFormal(), c, new AsyncTaskCallback() {
            @Override
            public void onSuccess_(boolean hasMore) { f.set(null); }

            @Override
            public void onFailure_(Throwable t) { f.setException(t); }
        }, SingleuserStoreJoiner::handle, _sameThread);

        try {
            f.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause(), Exception.class);
            throw e;
        }

        final Path path = new Path(_cfgRootSID.get(), info._name);
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                _rns.getRitualNotifier().sendNotification(newSharedFolderJoinNotification(path));
            }
        });
    }

    private static Boolean handle(HttpResponse r) throws Exception
    {
        String content = r.getContent().toString(BaseUtil.CHARSET_UTF);
        if (!r.getStatus().equals(HttpResponseStatus.OK)) {
            l.info("polaris error {}\n{}", r.getStatus(), content);
            if (r.getStatus().getCode() >= 500) {
                throw new ExRetryLater(r.getStatus().getReasonPhrase());
            }
            if (r.getStatus().equals(HttpResponseStatus.CONFLICT)) {
                Map<String, String> fields = GsonUtil.GSON.fromJson(content,
                        new TypeToken<Map<String, String>>(){}.getType());
                String errorName = fields.get("error_name");
                if ("PARENT_CONFLICT".equals(errorName)) {
                    l.info("anchor already exists in polaris");
                    return false;
                }
            }
            throw new ExProtocolError(r.getStatus().getReasonPhrase());
        }
        return false;
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
            _lod.removeLeaveCommandsFromQueue_(sid, t);

            if (path == null) continue;

            t.addListener_(new AbstractTransListener() {
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
