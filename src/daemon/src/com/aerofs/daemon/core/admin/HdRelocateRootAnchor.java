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
import com.aerofs.lib.L;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Path;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExInUse;
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
    private final CfgAbsAuxRoot _cfgAbsAuxRoot;

    @Inject
    public HdRelocateRootAnchor(DirectoryService ds, InjectableFile.Factory factFile,
            TransManager tm, InjectableDriver dr, CfgAbsAuxRoot cfgAbsAuxRoot)
    {
        _ds = ds;
        _factFile = factFile;
        _tm = tm;
        _dr = dr;
        _cfgAbsAuxRoot = cfgAbsAuxRoot;
    }

    @Override
    protected void handleThrows_(EIRelocateRootAnchor ev, Prio prio) throws Exception
    {
        if (!new File(ev._newRootAnchor).isAbsolute()) {
            throw new ExBadArgs("the path to new " + L.PRODUCT + " location is not absolute");

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
        // sanity checking

        RootAnchorUtil.checkNewRootAnchor(absOldRoot, absNewRoot);
        RootAnchorUtil.checkRootAnchor(absNewRoot, Cfg.absRTRoot(), false);

        InjectableFile fNewRoot = _factFile.create(absNewRoot);
        InjectableFile fOldRoot = _factFile.create(absOldRoot);

        // The new root folder must be created so that getAuxRoot() below won't fail
        if (!fNewRoot.exists()) fNewRoot.mkdirs();
        boolean sameFS = OSUtil.get().isInSameFileSystem(absOldRoot, absNewRoot);

        AbstractRelocator relocator = sameFS ?
                new SameFSRelocator(fOldRoot, fNewRoot)
                : new DifferentFSRelocator(fOldRoot, fNewRoot);

        // Delete the empty new root that we just created, so that the move and copy operations
        // work properly. Note: we know this is empty because either we just created it or we
        // asserted its emptiness in RootAnchorUtil.checkRootAnchor above.
        fNewRoot.delete();

        // perform actual operations

        l.warn(absOldRoot + " -> " + absNewRoot + ". same fs? " + sameFS);

        Trans t = _tm.begin_();
        try {
            relocator.doWork(t);
            Cfg.db().set(Key.ROOT, absNewRoot);

            t.commit_();

        } catch (Exception e) {

            l.warn("move failed. rollback: " + Util.e(e));

            Cfg.db().set(Key.ROOT, absOldRoot);
            // Restart Cfg settings since the daemon keeps running
            Cfg.init_(Cfg.absRTRoot(), false);
            relocator.rollback();
            throw e;

        } finally {
            t.end_();
        }

        relocator.onSuccessfulTransaction();

        /*
         * Need to exit daemon so that the linker can be restarted and does not change the
         * database to an inconsistent state. This also allows Auxroot path to be reset (refer to
         * LinkedStorage.init_())
         */
        ExitCode.RELOCATE_ROOT_ANCHOR.exit();
    }

    private abstract class AbstractRelocator
    {
        final InjectableFile _oldRoot;
        final InjectableFile _newRoot;
        final InjectableFile _oldAuxRoot;
        final InjectableFile _newAuxRoot;

        AbstractRelocator(InjectableFile oldRoot, InjectableFile newRoot)
        {
            _oldRoot = oldRoot;
            _newRoot = newRoot;

            _oldAuxRoot = _factFile.create(_cfgAbsAuxRoot.get());
            _newAuxRoot = _factFile.create(_cfgAbsAuxRoot.forPath(_newRoot.getPath()));
        }

        abstract void doWork(Trans t) throws Exception;

        /**
         * Called if doWork throws
         */
        abstract void rollback() throws Exception;

        /**
         * Called after the transaction has been successfully ended
         */
        abstract void onSuccessfulTransaction();
    }

    private class SameFSRelocator extends AbstractRelocator
    {
        SameFSRelocator(InjectableFile oldRoot, InjectableFile newRoot)
        {
            super(oldRoot, newRoot);
        }

        @Override
        void doWork(Trans t) throws Exception
        {
            try {
                _oldRoot.moveInSameFileSystem(_newRoot);
                _oldAuxRoot.moveInSameFileSystem(_newAuxRoot);
            } catch (IOException e) {
                if (OSUtil.isWindows()) {
                    throw new ExInUse("files in the " + L.PRODUCT + " folder are in use. " +
                            "Please close all programs interacting with files in " +
                            L.PRODUCT);
                }
                throw e;
            }
        }

        @Override
        void rollback() throws Exception
        {
            if (_newAuxRoot.exists()) _newAuxRoot.moveInSameFileSystem(_oldAuxRoot);
            if (_newRoot.exists()) _newRoot.moveInSameFileSystem(_oldRoot);
        }

        @Override
        void onSuccessfulTransaction() {}
    }

    private class DifferentFSRelocator extends AbstractRelocator
    {
        DifferentFSRelocator(InjectableFile oldRoot, InjectableFile newRoot)
        {
            super(oldRoot, newRoot);
        }

        @Override
        void doWork(Trans t) throws Exception
        {
            OSUtil.get().copyRecursively(_oldRoot, _newRoot, true, true);
            updateFID(_ds.resolveThrows_(new Path()), _newRoot.getAbsolutePath(), t);
            OSUtil.get().copyRecursively(_oldAuxRoot, _newAuxRoot, false, false);
            OSUtil.get().markHiddenSystemFile(_newAuxRoot.getAbsolutePath());
        }

        @Override
        void rollback() throws Exception
        {
            deleteFolders(_newRoot, _newAuxRoot);
        }

        @Override
        void onSuccessfulTransaction()
        {
            // We only delete the old folders once we're absolutely sure everything was successful
            // Otherwise we risk deleting user data
            deleteFolders(_oldRoot, _oldAuxRoot);
        }

        private void updateFID(SOID newRootSOID, String absNewRoot, final Trans t) throws Exception
        {
            // Initial walk inserts extra byte to each new FID to avoid duplicate key conflicts in the
            // database in case some files in the new location happen to have identical FIDs with
            // original files -- although it should be very rare. Second walk removes this extra byte.

            _ds.walk_(newRootSOID, absNewRoot, new IObjectWalker<String>()
            {
                @Override
                public String prefixWalk_(String oldParent, OA oa)
                {
                    return prefixWalk(oldParent, oa);
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
                    return prefixWalk(oldParent, oa);
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

        /**
         * oldParent has the File.separator affixed to the end of the path (except root)
         */
        private @Nullable String prefixWalk(String oldParent, OA oa)
        {
            if (oa.isExpelled()) {
                assert oa.fid() == null;
                return null;
            }

            switch (oa.type()) {
            case ANCHOR: return oldParent + oa.name();
            case DIR:    return (oa.soid().oid().isRoot()) ? oldParent + File.separator
                                                           : oldParent + oa.name() + File.separator;
            case FILE:   return null;
            default:     assert false; return null;
            }
        }

        private void deleteFolders(InjectableFile rootAnchor, InjectableFile auxRoot)
        {
            if (!rootAnchor.deleteIgnoreErrorRecursively()) {
                l.warn("couldn't delete " + rootAnchor + ". ignored.");
            }
            if (!auxRoot.deleteIgnoreErrorRecursively()) {
                l.warn("couldn't delete " + auxRoot + ". ignored.");
            }
        }
    }
}
