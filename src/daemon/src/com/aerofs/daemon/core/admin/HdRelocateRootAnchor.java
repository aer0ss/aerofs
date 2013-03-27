/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.labeling.L;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Path;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExInUse;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.rocklog.EventType;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

public class HdRelocateRootAnchor extends AbstractHdIMC<EIRelocateRootAnchor>
{
    private static final Logger l = Loggers.getLogger(HdRelocateRootAnchor.class);
    private final InjectableFile.Factory _factFile;
    private final TransManager _tm;
    private final ICrossFSRelocator _crossFSRelocator;
    private final CfgAbsAuxRoot _cfgAbsAuxRoot;

    @Inject
    public HdRelocateRootAnchor(InjectableFile.Factory factFile, TransManager tm,
            ICrossFSRelocator differentFSRelocator, CfgAbsAuxRoot cfgAbsAuxRoot)
    {
        _factFile = factFile;
        _tm = tm;
        _crossFSRelocator = differentFSRelocator;
        _cfgAbsAuxRoot = cfgAbsAuxRoot;
    }

    @Override
    protected void handleThrows_(EIRelocateRootAnchor ev, Prio prio) throws Exception
    {
        if (!new File(ev._newRootAnchor).isAbsolute()) {
            throw new ExBadArgs("the path to new " + L.PRODUCT + " location is not absolute");
        }

        SVClient.sendEventAsync(Type.MOVE_ROOT);
        RockLog.newEvent(EventType.MOVE_ROOT).sendAsync();

        // Even though we expect the UI to adjust the new root anchor, users may pass in a raw path
        // through the Ritual call.
        move_(Cfg.rootSID(), Cfg.absDefaultRootAnchor(),
                RootAnchorUtil.adjustRootAnchor(ev._newRootAnchor));
    }

    /**
     * Linker is 'disabled' during this method since the Core runs as single threaded process
     */
    private void move_(SID sid, String absOldRoot, String absNewRoot) throws Exception
    {
        // Sanity checking.
        RootAnchorUtil.checkNewRootAnchor(absOldRoot, absNewRoot);
        RootAnchorUtil.checkRootAnchor(absNewRoot, Cfg.absRTRoot(), Cfg.storageType(), false);

        InjectableFile fNewRoot = _factFile.create(absNewRoot);
        InjectableFile fOldRoot = _factFile.create(absOldRoot);

        // The new root folder must be created so that getAuxRoot() below won't fail.
        if (!fNewRoot.exists()) fNewRoot.mkdirs();
        boolean sameFS = OSUtil.get().isInSameFileSystem(absOldRoot, absNewRoot);

        IRelocator relocator;

        if (sameFS) {
            relocator = new SameFSRelocator(_factFile, _cfgAbsAuxRoot, fOldRoot, fNewRoot);
        } else {
            // Injection provides the correct different FS relocator, we just have to initialize it.
            _crossFSRelocator.init_(fOldRoot, fNewRoot);
            relocator = _crossFSRelocator;
        }

        // Delete the empty new root that we just created, so that the move and copy operations
        // work properly. Note: we know this is empty because either we just created it or we
        // asserted its emptiness in RootAnchorUtil.checkRootAnchor above.
        fNewRoot.delete();

        // Perform actual operations.
        l.warn(absOldRoot + " -> " + absNewRoot + ". same fs? " + sameFS);

        Trans t = _tm.begin_();
        try {
            relocator.doWork(t);
            Cfg.db().set(Key.ROOT, absNewRoot);
            Cfg.db().moveRoot(sid, absNewRoot);
            // Since the default behavior is to set the autoexport dir to be the root
            // anchor, when we relocate the data at the root anchor, we also wind up
            // relocating the data in the autoexport folder.  Since the user probably
            // wanted to move both in such a scenario, we should update the config key.
            // TODO (DF): It might make more sense (once we expose the autoexport folder
            // as a separate concept) if we check that the autoexport folder is *under*
            // the root anchor, and then update the path relative to the root anchor...
            // but that doesn't seem necessary at this point.
            if (absOldRoot.equals(Cfg.absAutoExportFolder())) {
                Cfg.db().set(Key.AUTO_EXPORT_FOLDER, absNewRoot);
            }

            t.commit_();

        } catch (Exception e) {

            l.warn("Move failed. Rollback: " + Util.e(e));

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

    public interface IRelocator
    {
        void doWork(Trans t) throws Exception;

        /**
         * Called if doWork throws.
         */
        void rollback() throws Exception;

        /**
         * Called after the transaction has been successfully completed.
         */
        void onSuccessfulTransaction();
    }

    private static abstract class AbstractRelocator implements IRelocator
    {
        private final InjectableFile.Factory _factFile;
        private final CfgAbsAuxRoot _cfgAbsAuxRoot;

        AbstractRelocator(InjectableFile.Factory factFile, CfgAbsAuxRoot cfgAbsAuxRoot)
        {
            _factFile = factFile;
            _cfgAbsAuxRoot = cfgAbsAuxRoot;
        }

        protected InjectableFile _oldRoot;
        protected InjectableFile _newRoot;
        protected InjectableFile _oldAuxRoot;
        protected InjectableFile _newAuxRoot;

        public void init_(InjectableFile oldRoot, InjectableFile newRoot)
        {
            _oldRoot = oldRoot;
            _newRoot = newRoot;

            _oldAuxRoot = _factFile.create(_cfgAbsAuxRoot.get());
            _newAuxRoot = _factFile.create(_cfgAbsAuxRoot.forPath(_newRoot.getPath()));
        }
   }

    private class SameFSRelocator extends AbstractRelocator
    {
        SameFSRelocator(InjectableFile.Factory factFile, CfgAbsAuxRoot cfgAbsAuxRoot,
                InjectableFile oldRoot, InjectableFile newRoot)
        {
            super(factFile, cfgAbsAuxRoot);
            init_(oldRoot, newRoot);
        }

        @Override
        public void doWork(Trans t) throws Exception
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
        public void rollback() throws Exception
        {
            if (_newAuxRoot.exists()) _newAuxRoot.moveInSameFileSystem(_oldAuxRoot);
            if (_newRoot.exists()) _newRoot.moveInSameFileSystem(_oldRoot);
        }

        @Override
        public void onSuccessfulTransaction()
        {
            // Nothing to do.
        }
    }

    public interface ICrossFSRelocator extends IRelocator
    {
        /**
         * Initialize file parameters.
         */
        void init_(InjectableFile oldRoot, InjectableFile newRoot);

        /**
         * Called after the root directory has been copied.
         */
        void afterRootCopy(Trans t) throws Exception;
    }

    protected static abstract class AbstractCrossFSRelocator
            extends AbstractRelocator
            implements ICrossFSRelocator
    {
        protected final DirectoryService _ds;
        protected final InjectableDriver _dr;

        protected AbstractCrossFSRelocator(InjectableFile.Factory factFile,
                CfgAbsAuxRoot cfgAbsAuxRoot, DirectoryService ds, InjectableDriver dr)
        {
            super(factFile, cfgAbsAuxRoot);

            _ds = ds;
            _dr = dr;
        }

        @Override
        public void doWork(Trans t) throws Exception
        {
            OSUtil.get().copyRecursively(_oldRoot, _newRoot, true, true);
            afterRootCopy(t);
            OSUtil.get().copyRecursively(_oldAuxRoot, _newAuxRoot, false, false);
            OSUtil.get().markHiddenSystemFile(_newAuxRoot.getAbsolutePath());
        }

        @Override
        public void rollback() throws Exception
        {
            deleteFolders(_newRoot, _newAuxRoot);
        }

        @Override
        public void onSuccessfulTransaction()
        {
            // We only delete the old folders once we're absolutely sure everything was successful
            // Otherwise we risk deleting user data
            deleteFolders(_oldRoot, _oldAuxRoot);
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

    public static class SingleuserCrossFSRelocator extends AbstractCrossFSRelocator
    {
        @Inject
        public SingleuserCrossFSRelocator(InjectableFile.Factory factFile,
                CfgAbsAuxRoot cfgAbsAuxRoot, DirectoryService ds, InjectableDriver dr)
        {
            super(factFile, cfgAbsAuxRoot, ds, dr);
        }

        @Override
        public void afterRootCopy(Trans t)
                throws Exception
        {
            // TODO: handle all root stores..
            updateFID(_ds.resolveThrows_(Path.root(Cfg.rootSID())), _newRoot.getAbsolutePath(), t);
        }

        private void updateFID(SOID newRootSOID, String absNewRoot, final Trans t) throws Exception
        {
            // Initial walk inserts extra byte to each new FID to avoid duplicate key conflicts in
            // the database in case some files in the new location happen to have identical FIDs
            // with original files -- although it should be very rare. Second walk removes this
            // extra byte.

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
                        throws IOException, SQLException {
                    if (!oa.soid().oid().isRoot() && oa.fid() != null) {
                        assert oa.fid().getBytes().length == _dr.getFIDLength() + 1;

                        byte[] bytesFID = Arrays.copyOf(oa.fid().getBytes(), _dr.getFIDLength());
                        _ds.setFID_(oa.soid(), new FID(bytesFID), t);
                    }
                }
            });
        }

        /**
         * oldParent has the File.separator affixed to the end of the path (except root).
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
    }

    public static class MultiuserCrossFSRelocator extends AbstractCrossFSRelocator
    {
        @Inject
        public MultiuserCrossFSRelocator(InjectableFile.Factory factFile,
                CfgAbsAuxRoot cfgAbsAuxRoot, DirectoryService ds, InjectableDriver dr)
        {
            super(factFile, cfgAbsAuxRoot, ds, dr);
        }

        @Override
        public void afterRootCopy(Trans t)
                throws Exception
        {
            // Nothing to do.
        }
    }
}
