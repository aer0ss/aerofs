/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.*;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EIImportFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.ContentHash;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.*;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class HdImportFile  extends AbstractHdIMC<EIImportFile>
{
    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final DirectoryService _ds;
    private final VersionUpdater _vu;
    private final ObjectCreator _oc;
    private final IPhysicalStorage _ps;

    @Inject
    public HdImportFile(TokenManager tokenManager, TransManager tm, DirectoryService ds,
            ObjectCreator oc, IPhysicalStorage ps, VersionUpdater vu)
    {
        _tokenManager = tokenManager;
        _tm = tm;
        _ds = ds;
        _vu = vu;
        _oc = oc;
        _ps = ps;
    }

    @Override
    protected void handleThrows_(EIImportFile ev) throws Exception
    {
        File f = new File(ev._source);
        if (!(f.exists() && f.isFile())) throw new ExBadArgs("Invalid source");

        SOID soid = _ds.resolveNullable_(ev._dest);
        SOID soidParent = _ds.resolveFollowAnchorThrows_(ev._dest.removeLast());

        OA oaParent = _ds.getOA_(soidParent);
        if (!oaParent.isDir() || oaParent.isExpelled()) throw new ExBadArgs("Invalid destination");

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
            try (Trans t = _tm.begin_()) {
                // create a new object with no associated CA (this is important to avoid generating
                // a bogus empty version)
                soid = new SOID(soidParent.sidx(), new OID(UniqueID.generate()));
                _oc.createMeta_(OA.Type.FILE, soid, soidParent.oid(), ev._dest.last(),
                        PhysicalOp.APPLY, true, t);
                t.commit_();
            }
        }

        OA oa = _ds.getOA_(soid);
        if (oa.isExpelled()) throw new ExExpelled(ev._dest.toString());
        if (oa.isDirOrAnchor()) throw new ExNotFile(ev._dest);

        boolean wasPresent = (oa.caMasterNullable() != null);
        SOKID sokid = new SOKID(soid, KIndex.MASTER);
        IPhysicalPrefix pp = _ps.newPrefix_(sokid, UniqueID.generate().toStringFormal());

        final ContentHash h;
        try (Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "import-file")) {
            // copy source file to prefix (with core lock released)
            h = tk.inPseudoPause_(() -> {
                PrefixOutputStream po = pp.newOutputStream_(false);
                try (
                        InputStream in = new FileInputStream(f);
                        PrefixOutputStream out = po
                ) {
                    ByteStreams.copy(in, out);
                }
                return po.digest();
            });
        } catch (Exception e) {
            pp.delete_();
            throw e;
        }

        try (Trans t = _tm.begin_()) {
            // Values might have changed while the core lock was released
            oa = _ds.getOA_(soid);
            ResolvedPath path = _ds.resolve_(oa);
            IPhysicalFile pf = _ps.newFile_(path, sokid.kidx());

            // move prefix to persistent storage
            long length = pp.getLength_();
            mtime = _ps.apply_(pp, pf, wasPresent, mtime, t);

            // update CA
            if (!wasPresent) _ds.createCA_(soid, KIndex.MASTER, t);
            _ds.setCA_(sokid, length, mtime, h, t);

            // increment version number after local update
            _vu.update_(new SOCID(soid, CID.CONTENT), t);

            t.commit_();
        }
    }
}
