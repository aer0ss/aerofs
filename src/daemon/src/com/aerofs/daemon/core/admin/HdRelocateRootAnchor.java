/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;


import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.C;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Path;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExInUse;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

public class HdRelocateRootAnchor extends AbstractHdIMC<EIRelocateRootAnchor>
{
    private static final Logger l = Util.l(HdRelocateRootAnchor.class);
    private final DirectoryService _ds;
    private final InjectableFile.Factory _factFile;
    private final TransManager _tm;
    private final InjectableDriver _dr;

    @Inject
    public HdRelocateRootAnchor(DirectoryService ds, InjectableFile.Factory factFile,
            TransManager tm, InjectableDriver dr)
    {
        _ds = ds;
        _factFile = factFile;
        _tm = tm;
        _dr = dr;
    }

    @Override
    protected void handleThrows_(EIRelocateRootAnchor ev, Prio prio) throws Exception
    {
        if (!new File(ev._newRootAnchor).isAbsolute()) {
            throw new ExBadArgs("the path to new " + S.PRODUCT + " location is not absolute");

        }

        SVClient.sendEventAsync(Type.MOVE_ROOT);

        // Even though we expect the UI to adjust the new root anchor, users may pass in a raw path
        // through the Ritual call.
        move_(Cfg.absRootAnchor(), RootAnchorUtil.adjustRootAnchor(ev._newRootAnchor));
    }

    /**
     * Linker is 'disabled' during this method since the Core runs as single threaded process
     */
    private void move_(String absOldRoot, String absNewRoot) throws Exception
    {
        ////////
        // sanity checking

        RootAnchorUtil.checkNewRootAnchor(absOldRoot, absNewRoot);
        RootAnchorUtil.checkRootAnchor(absNewRoot, Cfg.absRTRoot(), false);

        InjectableFile fNewRoot = _factFile.create(absNewRoot);
        InjectableFile fOldRoot = _factFile.create(absOldRoot);

        // The new root folder must be created so that getAuxRoot() below won't fail
        if (!fNewRoot.exists()) fNewRoot.mkdirs();

        String oldAux = OSUtil.get().getAuxRoot(absOldRoot);
        String newAux = OSUtil.get().getAuxRoot(absNewRoot);
        InjectableFile fNewAuxRoot = _factFile.create(newAux);

        if (!fNewAuxRoot.exists()) fNewAuxRoot.mkdirs();

        if (!fNewAuxRoot.canRead() || !fNewAuxRoot.canWrite()) {
            throw new ExNoPerm("cannot read or write to " + newAux);
        }

        boolean copyNDelete = !OSUtil.get().isInSameFileSystem(absOldRoot, absNewRoot);

        // Delete the empty directory so later moving or copying won't fail
        fNewRoot.delete();

        ////////
        // perform actual operations

        l.warn(absOldRoot + " -> " + absNewRoot + " " + copyNDelete);

        Trans t = _tm.begin_();
        try {
            if (copyNDelete) {
                OSUtil.get().copyRecursively(fOldRoot, fNewRoot, true, true);
                _updateFID(_ds.resolveThrows_(new Path()), absNewRoot, t);

                // Must copy over individual aux folders because AuxRoot is equivalent to the
                // RTRoot when the Anchor Root is on the same volume as the RTRoot.
                for (C.AuxFolder af : C.AuxFolder.values()) {
                    InjectableFile fOldAuxFolder = _factFile.create(Util.join(oldAux, af._name));
                    InjectableFile fNewAuxFolder = _factFile.create(Util.join(newAux, af._name));
                    OSUtil.get().copyRecursively(fOldAuxFolder, fNewAuxFolder, false, false);
                }

            } else {
                try {
                    fOldRoot.moveInSameFileSystem(fNewRoot);
                } catch (IOException e) {
                    if (OSUtil.isWindows()) {
                        throw new ExInUse("files in the " + S.PRODUCT + " folder are in use. " +
                                "Please close all processes interacting with files in " +
                                S.PRODUCT);
                    }

                    throw e;
                }
            }

            Cfg.db().set(Key.ROOT, absNewRoot);

            t.commit_();

        } catch (Exception e) {

            l.warn("move failed. rollback: " + Util.e(e));

            Cfg.db().set(Key.ROOT, absOldRoot);
            // Restart Cfg settings since the daemon keeps running
            Cfg.init_(Cfg.absRTRoot(), false);

            if (copyNDelete) {
                deleteOldFolders(fNewRoot, newAux);
            } else {
                if (fNewRoot.exists() && !fNewRoot.moveInSameFileSystemIgnoreError(fOldRoot)) {
                    throw new ExInconsistentState(S.PRODUCT + " cannot roll back move");
                }
            }

            throw e;

        } finally {
            t.end_();
        }

        if (copyNDelete) deleteOldFolders(fOldRoot, oldAux);

        /*
         * Need to exit daemon so that the linker can be restarted and does not change the
         * database to an inconsistent state. This also allows Auxroot path to be reset (refer to
         * LinkedStorage.init_())
         */
        ExitCode.RELOCATE_ROOT_ANCHOR.exit();
    }

    /**
     * oldParent has the File.separator affixed to the end of the path
     */
    private @Nullable String _prefixWalk(String oldParent, OA oa)
    {
        if (oa.isExpelled()) {
            assert oa.fid() == null;
            return null;
        }

        switch (oa.type()) {
        case ANCHOR:
            return oldParent + oa.name();
        case DIR:
            // Root has oa.name() == "R"
            if (oa.soid().oid().isRoot()) {
                return oldParent + File.separator;
            } else {
                return oldParent + oa.name() + File.separator;
            }
        case FILE:
            return null;
        default:
            assert false;
            return null;
        }
    }

    private void _updateFID(SOID newRootSOID, String absNewRoot, final Trans t) throws Exception
    {
        // Initial walk inserts extra byte to each new FID to avoid duplicate key conflicts in the
        // database in case some files in the new location happen to have identical FIDs with
        // original files -- although it should be very rare. Second walk removes this extra byte.

        _ds.walk_(newRootSOID, absNewRoot, new IObjectWalker<String>()
        {
            @Override
            public String prefixWalk_(String oldParent, OA oa)
            {
                return _prefixWalk(oldParent, oa);
            }

            @Override
            public void postfixWalk_(String oldParent, OA oa)
                    throws IOException, SQLException
            {
                // Root objects have null FIDs
                if (!oa.soid().oid().isRoot() && oa.fid() != null) {
                    FID newFID = _dr.getFID(oldParent + oa.name());

                    // newFID can be null if it is removed by filesystem during relocation
                    if (newFID == null) {
                        newFID = oa.fid();
                        assert newFID != null : oa;
                    }

                    byte[] bytesFID = Arrays.copyOf(newFID.getBytes(), _dr.getFIDLength() + 1);
                    _ds.setFID_(oa.soid(), new FID(bytesFID), t);
                }
            }
        });

        _ds.walk_(newRootSOID, absNewRoot, new IObjectWalker<String>()
        {
            @Override
            public String prefixWalk_(String oldParent, OA oa)
            {
                return _prefixWalk(oldParent, oa);
            }

            @Override
            public void postfixWalk_(String oldParent, OA oa)
                    throws IOException, SQLException
{
                if (!oa.soid().oid().isRoot() && oa.fid() != null) {
                    assert oa.fid().getBytes().length == _dr.getFIDLength() + 1;

                    byte[] bytesFID = Arrays.copyOf(oa.fid().getBytes(), _dr.getFIDLength());
                    _ds.setFID_(oa.soid(), new FID(bytesFID), t);
                }
            }
        });
    }

    private void deleteOldFolders(InjectableFile rootAnchor, String auxRoot)
    {
        if (!rootAnchor.deleteIgnoreErrorRecursively()) {
            l.warn("couldn't delete " + rootAnchor + ". ignored.");
        }

        for (C.AuxFolder af : C.AuxFolder.values()) {
            InjectableFile fAuxFolder = _factFile.create(Util.join(auxRoot, af._name));
            if (!fAuxFolder.deleteIgnoreErrorRecursively()) {
                l.warn("couldn't delete " + fAuxFolder + ". ignored.");
            }
        }
    }
}
