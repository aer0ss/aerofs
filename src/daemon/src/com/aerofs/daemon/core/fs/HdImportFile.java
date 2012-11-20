/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.fs.EIImportFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class HdImportFile  extends AbstractHdIMC<EIImportFile>
{
    private final TC _tc;
    private final TransManager _tm;
    private final DirectoryService _ds;
    private final VersionUpdater _vu;
    private final LocalACL _lacl;
    private final ObjectCreator _oc;
    private final IPhysicalStorage _ps;

    @Inject
    public HdImportFile(TC tc, TransManager tm, DirectoryService ds, LocalACL lacl,
            ObjectCreator oc, IPhysicalStorage ps, VersionUpdater vu)
    {
        _tc = tc;
        _tm = tm;
        _ds = ds;
        _vu = vu;
        _lacl = lacl;
        _oc = oc;
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIImportFile ev, Prio prio) throws Exception
    {
        File f = new File(ev._source);
        if (!(f.exists() && f.isFile())) throw new ExBadArgs("Invalid source");

        SOID soid = _ds.resolveNullable_(ev._dest);
        Path pathParent = ev._dest.removeLast();
        SOID soidParent = _lacl.checkThrows_(Cfg.user(), pathParent, Role.EDITOR);

        OA oaParent = _ds.getOA_(soidParent);
        if (oaParent.isExpelled() || !oaParent.isDir()) throw new ExBadArgs("Invalid destination");

        long mtime = System.currentTimeMillis();

        /**
         * If the destination file does not exist, try to create it. If it does exist, simply
         * overwrite the contents of the MASTER branch.
         *
         * We need two separate transactions for creation and content import as physical storage may
         * release the core lock.
         * This is safe even in the event of a crash between the two transactions. In such a case
         * we would end up with an object without content (which is what happens after re-admission
         * for instance). Besides, the first transaction will no longer be needed on any subsequent
         * retry of such a failing command.
         */
        if (soid == null) {
            Trans t = _tm.begin_();
            try {
                _oc.create_(OA.Type.FILE, soidParent, ev._dest.last(), PhysicalOp.APPLY, t);
                soid = _ds.resolveThrows_(ev._dest);
                t.commit_();
            } finally {
                t.end_();
            }
        }

        OA oa = _ds.getOA_(soid);
        if (oa.isExpelled()) throw new ExExpelled(ev._dest.toString());
        if (oa.isDirOrAnchor()) throw new ExNotFile(ev._dest.toString());

        boolean wasPresent = (oa.caMaster() != null);
        SOCKID sockid = new SOCKID(soid, CID.CONTENT, KIndex.MASTER);
        IPhysicalPrefix pp = _ps.newPrefix_(sockid);

        ContentHash h;
        Token tk = _tc.acquire_(Cat.UNLIMITED, "import-file");
        try {
            // copy source file to prefix (with core lock released)
            TCB tcb = tk.pseudoPause_("import to prefix");
            try {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = new FileInputStream(f);
                    out = pp.newOutputStream_(false);
                    ByteStreams.copy(in, out);
                } finally {
                    if (in != null) in.close();
                    if (out != null) out.close();
                }
            } finally {
                tcb.pseudoResumed_();
            }

            // prepare prefix for persistent storage
            h = pp.prepare_(tk);
        } finally {
            tk.reclaim_();
        }

        Trans t = _tm.begin_();
        try {
            // Values might have changed while the core lock was released
            oa = _ds.getOA_(soid);
            Path path = _ds.resolve_(oa);
            IPhysicalFile pf = _ps.newFile_(sockid.sokid(), path);

            // move prefix to persistent storage
            _ps.apply_(pp, pf, wasPresent, mtime, t);

            // update CA
            _ds.setCA_(sockid.sokid(), pp.getLength_(), mtime, h, t);

            // increment version number after local update
            _vu.update_(sockid, t);

            t.commit_();
        } finally {
            t.end_();
        }
    }
}
