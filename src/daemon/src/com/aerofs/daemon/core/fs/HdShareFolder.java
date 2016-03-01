package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.SubjectPermissionsList;
import com.aerofs.base.ex.*;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;


public class HdShareFolder extends AbstractHdIMC<EIShareFolder>
{
    private final static Logger l = Loggers.getLogger(HdShareFolder.class);

    private final TokenManager _tokenManager;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;
    private final StoreHierarchy _ss;
    private final DescendantStores _dss;
    private final ACLSynchronizer _aclsync;
    private final SPBlockingClient.Factory _factSP;
    private final UnlinkedRootDatabase _urdb;
    private final CfgAbsRoots _absRoots;
    private final PolarisAsyncClient _client;
    private final RemoteLinkDatabase _rldb;

    private final Executor _sameThread = MoreExecutors.sameThreadExecutor();

    @Inject
    public HdShareFolder(TokenManager tokenManager, DirectoryService ds,
            IMapSID2SIndex sid2sidx, StoreHierarchy ss, DescendantStores dss,
            ACLSynchronizer aclsync, InjectableSPBlockingClientFactory factSP,
            CfgAbsRoots cfgAbsRoots, UnlinkedRootDatabase urdb,
            RemoteLinkDatabase rldb, PolarisAsyncClient.Factory clientFactory)
    {
        _ss = ss;
        _tokenManager = tokenManager;
        _ds = ds;
        _sid2sidx = sid2sidx;
        _dss = dss;
        _aclsync = aclsync;
        _factSP = factSP;
        _absRoots = cfgAbsRoots;
        _urdb = urdb;
        _rldb = rldb;
        // NB: do not reuse the background polaris connection to bypass pipelined messages and
        // reduce the likelihood of suffering from a broken connection
        _client = clientFactory.create();
    }

    @Override
    protected void handleThrows_(EIShareFolder ev) throws Exception
    {
        l.info("sharing: {}", ev._path);

        OA oa;
        SID sid;
        String name;
        boolean alreadyShared = false;

        if (ev._path.sid().isUserRoot()) {
            // physical root is the user's default root store: it cannot be shared but its subfolders can.
            oa = throwIfUnshareable_(ev._path);

            if (oa.isAnchor()) {
                alreadyShared = true;
                sid = SID.anchorOID2storeSID(oa.soid().oid());
            } else if (oa.isDir()) {
                sid = SID.folderOID2convertedStoreSID(oa.soid().oid());
            } else {
                throw new ExNotDir();
            }
            name = sharedFolderName(ev._path, _absRoots);
        } else {
            // option 1: Block Storage
            // option 2: physical root is an external shared folder. The root can be shared and no
            // nested sharing is allowed as it would break consistency (an externally shared folder
            // for one user may be located under the root anchor for another)
            if (!ev._path.isEmpty()) throw new ExParentAlreadyShared();
            alreadyShared = true;
            sid = ev._path.sid();
            // reject unknown SID (failure to do so would lead to crash when determining the name)
            if (_sid2sidx.getLocalOrAbsentNullable_(sid) == null) throw new ExBadArgs();
            oa = null;
            // Grabbing name for external shared folders. If the store is unlinked, query unlinked
            // root db else get it through the sharedFolderName util.
            name = _urdb.getUnlinkedRoot(sid);
            if (name == null) {
                name = sharedFolderName(ev._path, _absRoots);
            }
        }

        //
        // IMPORTANT: the order of operations in the following code matters
        //
        // You have to:
        // 1) contact the central ACL server to share the folder
        // 2) convert the local folder into a shared folder
        //
        // We contact the remote _first_ to update the ACLs because remote calls are much more
        // likely to fail than local ones. A failure will safely prevent the folder from being
        // converted into a store (i.e. a half-state in the system).
        //
        // If the order were reversed we would convert the store locally first,
        // and then update the ACLs for this store. At the same time,
        // the migration process has started, and objects will be deleted from the old folder and
        // added to the store. At the same time, this update would be propagated to other devices
        // for the user. If the RPC call fails however, neither the sharer or their devices would
        // have permissions to receive the files or make modifications to the store,
        // resulting in all the contents vanishing. Not good. Moreover,
        // the system is in a half-state; the folder has been shared,
        // but no ACLs for the owner exist; again, not good.
        //
        // To prevent this we contact the remote first, verify that the ACLs are added, and then,
        // make changes locally. Since the acl update process is idempotent from the perspective
        // of the stores' owner, multiple local failures can be handled properly,
        // while remote failures will prevent the system from being in a half-state
        //
        if (!alreadyShared) {
            try (Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "share")) {
                OID oid = SID.convertedStoreSID2folderOID(sid);
                UniqueID parent = oa.parent().isRoot() ? ev._path.sid() : oa.parent();

                LocalChange c = new LocalChange();
                c.type = Type.SHARE;
                c.child = oid.toStringFormal();
                SettableFuture<UniqueID> f = SettableFuture.create();

                Future<RemoteLink> w = _rldb.wait_(oa.soid().sidx(), oid);

                // synchronous request to polaris
                UniqueID jobId = tk.inPseudoPause_(() -> {
                    // wait for the original folder to be present on polaris, w/ timeout
                    // NB: get() blocks and must be called wo/ holding the core lock
                    try {
                        w.get(10, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        w.cancel(false);
                        throw new ExTimeout();
                    }

                    _client.post("/objects/" + parent.toStringFormal(), c,
                            new AsyncTaskCallback() {
                                @Override
                                public void onSuccess_(boolean hasMore) {
                                    // TODO(phoenix): extract job id and propagate to UI for spinner

                                    f.set(null);
                                }

                                @Override
                                public void onFailure_(Throwable t) {
                                    f.setException(t);
                                }
                            }, r -> handle_(f, r), _sameThread);

                    return f.get(10, TimeUnit.SECONDS);
                });
            } catch (ExecutionException e) {
                Throwables.propagateIfPossible(e.getCause(), Exception.class);
                throw e;
            }
        }

        callSP_(sid, name, SubjectPermissionsList.mapToPB(ev._subject2role), ev._emailNote,
                ev._suppressSharedFolderRulesWarnings);

        // ensure ACLs are updated (at the very least we need an entry for the local user...)
        _aclsync.syncToLocal_();

        l.info("shared: {} -> {}", ev._path, sid.toStringFormal());
    }

    private static Boolean handle_(SettableFuture<UniqueID> f, HttpResponse r) throws Exception
    {
        String content = r.getContent().toString(BaseUtil.CHARSET_UTF);
        if (!r.getStatus().equals(HttpResponseStatus.OK)) {
            l.info("polaris error {}\n{}", r.getStatus(), content);
            if (r.getStatus().getCode() >= 500) {
                throw new ExRetryLater(r.getStatus().getReasonPhrase());
            }
            throw new ExProtocolError(r.getStatus().getReasonPhrase());
        }

        return false;
    }

    /**
     * Derive the name of a shared folder from its Path
     * This is necessary to handle external roots, whose Path are empty and whose name are derived
     * from the physical folder they are linked too.
     */
    private static String sharedFolderName(Path path, CfgAbsRoots absRoots) throws SQLException
    {
        return path.isEmpty() ? new File(absRoots.get(path.sid())).getName() : path.last();
    }

    private OA throwIfUnshareable_(Path path)
            throws SQLException, ExNotFound, ExNoPerm, ExExpelled, ExParentAlreadyShared,
            ExChildAlreadyShared
    {
        SOID soid = _ds.resolveThrows_(path);

        // can't share root folder or trash
        if (soid.oid().isRoot() || soid.oid().isTrash()) {
            throw new ExNoPerm("can't share system folders");
        }

        OA oa = _ds.getOA_(soid);

        // can't share if a parent folder is already shared
        if (!_ss.isRoot_(soid.sidx())) throw new ExParentAlreadyShared();

        // can't share if a child folder is already shared
        Set<SIndex> descendants = _dss.getDescendantStores_(soid);
        if (!descendants.isEmpty()) throw new ExChildAlreadyShared();
        return oa;
    }

    /**
     * Pseudo-pause and make a call to SP to share the folder
     */
    private void callSP_(SID sid, String folderName, List<PBSubjectPermissions> roles,
            String emailNote, boolean suppressSharingRulesWarnings) throws Exception
    {
        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "sp-share", () -> _factSP.create()
                .signInRemote()
                .shareFolder(folderName, BaseUtil.toPB(sid), roles, emailNote, false,
                        suppressSharingRulesWarnings)
        );
    }
}
