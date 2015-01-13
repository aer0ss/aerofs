/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EICreateRoot;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.inject.Inject;

import java.util.Collections;

public class HdCreateRoot extends AbstractHdIMC<EICreateRoot>
{
    private final TokenManager _tokenManager;
    private final StoreDeleter _sd;
    private final TransManager _tm;
    private final LinkerRootMap _lrm;
    private final ACLSynchronizer _aclsync;
    private final InjectableFile.Factory _factFile;
    private final SPBlockingClient.Factory _factSP;
    private final LinkRootUtil _lru;


    @Inject
    public HdCreateRoot(TokenManager tokenManager, TransManager tm, StoreDeleter sd,
            LinkerRootMap lrm, ACLSynchronizer aclsync, InjectableFile.Factory factFile,
            InjectableSPBlockingClientFactory factSP, LinkRootUtil lru)
    {
        _tokenManager = tokenManager;
        _tm = tm;
        _sd = sd;
        _lrm = lrm;
        _aclsync = aclsync;
        _factFile = factFile;
        _factSP = factSP;
        _lru = lru;
    }

    @Override
    protected void handleThrows_(EICreateRoot ev, Prio prio)
            throws Exception
    {
        InjectableFile f = _factFile.create(ev._path);
        _lru.checkSanity(f);

        SID sid = SID.folderOID2convertedStoreSID(OID.generate());
        l.info("link {} {}", ev._path, sid);

        // link the destination first
        // in the event of a failure of the SP call (only required when creating a new root) we
        // can unlink the root. On the other hand a failure to link would leave us in an state that
        // is not quite as easy to rollback (on Team Server that is... the regular client could
        // easily recover as it does not auto-join external-tagged shared folders)
        SIndex sidx = _lru.linkRoot(f, sid);
        try {
            callSPToCreateSharedFolder_(f.getName(), sid);
        } catch (Exception e) {
            try {
                unlinkRoot_(sid, sidx);
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
        l.info("linked {} {}", ev._path, sid);
    }

    private void callSPToCreateSharedFolder_(String name, SID sid) throws Exception
    {
        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "sp-share-ext", () ->
            _factSP.create()
                    .signInRemote()
                    .shareFolder(name, sid.toPB(),
                            Collections.<PBSubjectPermissions>emptyList(), "", true, false)
        );
    }

    private void unlinkRoot_(SID sid, SIndex sidx) throws Exception
    {
        try (Trans t = _tm.begin_()) {
            // NB: use MAP and not APPLY as we don't want to delete user's files
            _sd.deleteRootStore_(sidx, PhysicalOp.MAP, t);
            _lrm.unlink_(sid, t);
            t.commit_();
        }
    }
}