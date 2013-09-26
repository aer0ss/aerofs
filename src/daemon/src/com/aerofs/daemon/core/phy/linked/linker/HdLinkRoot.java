/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.fs.EILinkRoot;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.inject.Inject;

import java.util.Collections;

public class HdLinkRoot extends AbstractHdIMC<EILinkRoot>
{
    private final TC _tc;
    private final StoreCreator _sc;
    private final StoreDeleter _sd;
    private final TransManager _tm;
    private final LinkerRootMap _lrm;
    private final CfgLocalUser _localUser;
    private final ACLSynchronizer _aclsync;
    private final InjectableFile.Factory _factFile;
    private final SPBlockingClient.Factory _factSP;

    @Inject
    public HdLinkRoot(TC tc, TransManager tm, StoreCreator sc, StoreDeleter sd, LinkerRootMap lrm,
            CfgLocalUser localUser, ACLSynchronizer aclsync, InjectableFile.Factory factFile,
            SPBlockingClient.Factory factSP)
    {
        _tc = tc;
        _tm = tm;
        _sc = sc;
        _sd = sd;
        _lrm = lrm;
        _localUser = localUser;
        _aclsync = aclsync;
        _factFile = factFile;
        _factSP = factSP;
    }

    @Override
    protected void handleThrows_(EILinkRoot ev, Prio prio) throws Exception
    {
        InjectableFile f = _factFile.create(ev._path);
        checkSanity(f);

        // use provided SID (join) or generate a new one (share)
        SID sid = ev._sid != null ? ev._sid : SID.folderOID2convertedStoreSID(OID.generate());

        l.info("link {} {}", ev._path, ev._sid);

        // link the destination first
        // in the event of a failure of the SP call (only required when creating a new root) we
        // can unlink the root. On the other hand a failure to link would leave us in an state that
        // is not quite as easy to rollback (on Team Server that is... the regular client could
        // easily recover as it does not auto-join external-tagged shared folders)
        SIndex sidx = linkRoot(f.getName(), sid, ev._path);

        if (ev._sid == null) {
            try {
                createExternalSharedFolder(f.getName(), sid);
            } catch (Exception e) {
                try {
                    unlinkRoot(sid, sidx);
                } catch (Exception ex) {
                    // NB: unlinking of a freshly linked root should only ever fail if db or fs
                    // failure of catastrophic proportion occurs
                    l.error("link rollback failed: {} {}", Util.e(ex), Util.e(e));
                    throw ex;
                }
                throw e;
            }

            ev.setResult(sid);

            // force an ACL update to make sure the caller can immediately invite users when we return
            // from this call
            try {
                _aclsync.syncToLocal_();
            } catch (Exception e) {
                // ACL update is not critical to this call so we can safely ignore a failure
                l.warn("post-link acl sync failed: {}", Util.e(e));
            }
        }

        l.info("linked {} {}", ev._path, ev._sid);
    }

    void checkSanity(InjectableFile f) throws Exception
    {
        if (!f.exists()) throw new ExNotFound();
        if (!f.isDirectory()) throw new ExNotDir();

        // check for conflict with existing roots
        String canonicalPath = f.getCanonicalPath();
        l.info("canon {}", canonicalPath);
        if (_lrm.isAnyRootUnder_(canonicalPath)) {
            throw new ExChildAlreadyShared();
        }
        if (_lrm.rootForAbsPath_(canonicalPath) != null) {
            throw new ExParentAlreadyShared();
        }

        RootAnchorUtil.checkRootAnchor(f.getAbsolutePath(), Cfg.absRTRoot(), StorageType.LINKED,
                true);
    }

    // call SP to create external shared folder with core lock released
    private void createExternalSharedFolder(String name, SID sid) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "sp-share-ext");
        try {
            TCB tcb = tk.pseudoPause_("sp-share-ext");
            try {
                SPBlockingClient sp = _factSP.create_(_localUser.get());
                sp.signInRemote();
                sp.shareFolder(name, sid.toPB(), Collections.<PBSubjectRolePair>emptyList(), "", true, false);
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    private SIndex linkRoot(String name, SID sid, String path) throws Exception
    {
        Trans t = _tm.begin_();
        try {
            // must link before creating store prevent creating of a conflicting root in teamserver
            // (FlatLinkedStorage creates new root by default on every store creation)
            _lrm.link_(sid, path, t);

            SIndex sidx = _sc.createRootStore_(sid, name, t);
            t.commit_();
            return sidx;
        } finally {
            t.end_();
        }
    }

    private void unlinkRoot(SID sid, SIndex sidx) throws Exception
    {
        Trans t = _tm.begin_();
        try {
            // NB: use MAP and not APLLY as we don't want to delete user's files
            _sd.deleteRootStore_(sidx, PhysicalOp.MAP, t);
            _lrm.unlink_(sid, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
