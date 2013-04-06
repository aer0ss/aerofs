/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.event.admin.EIRelocateRootAnchor;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsDefaultRoot;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.labeling.L;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.ex.ExInUse;
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

public class HdRelocateRootAnchor extends AbstractHdIMC<EIRelocateRootAnchor>
{
    private static final Logger l = Loggers.getLogger(HdRelocateRootAnchor.class);

    private final CfgAbsDefaultRoot _cfgAbsDefaultRoot;
    private final CfgAbsRoots _cfgAbsRoots;
    private final InjectableFile.Factory _factFile;
    private final TransManager _tm;
    private final SameFSRelocator _sameFSRelocator;
    private final CrossFSRelocator _crossFSRelocator;

    @Inject
    public HdRelocateRootAnchor(InjectableFile.Factory factFile, TransManager tm,
            SameFSRelocator sameFSRelocator, CrossFSRelocator crossFSRelocator,
            CfgAbsDefaultRoot cfgAbsDefaultRoot, CfgAbsRoots cfgAbsRoots)
    {
        _cfgAbsDefaultRoot = cfgAbsDefaultRoot;
        _cfgAbsRoots = cfgAbsRoots;
        _factFile = factFile;
        _tm = tm;
        _sameFSRelocator = sameFSRelocator;
        _crossFSRelocator = crossFSRelocator;
    }

    @Override
    protected void handleThrows_(EIRelocateRootAnchor ev, Prio prio) throws Exception
    {
        l.info("relocate {} {}", ev._sid, ev._newRootAnchor);

        if (!new File(ev._newRootAnchor).isAbsolute()) {
            throw new ExBadArgs("absolute path expected");
        }

        SVClient.sendEventAsync(Type.MOVE_ROOT);
        RockLog.newEvent(EventType.MOVE_ROOT).sendAsync();

        @Nullable SID sid = ev._sid;
        // Even though we expect the UI to adjust the new root anchor, users may pass in a raw path
        // through the Ritual call.
        String newAbsRoot = RootAnchorUtil.adjustRootAnchor(ev._newRootAnchor, sid);

        String oldAbsRoot;
        if (sid == null) {
            sid = Cfg.rootSID();
            oldAbsRoot = _cfgAbsDefaultRoot.get();
        } else {
            oldAbsRoot = _cfgAbsRoots.get(sid);
            if (oldAbsRoot == null) {
                throw new ExBadArgs("No external root for " + sid.toStringFormal());
            }
        }

        move_(sid, oldAbsRoot, newAbsRoot);
    }

    /**
     * Linker is 'disabled' during this method since the Core runs as single threaded process
     */
    private void move_(SID sid, String absOldRoot, String absNewRoot) throws Exception
    {
        final boolean isDefaultRoot = sid.equals(Cfg.rootSID());

        // Sanity checking.
        RootAnchorUtil.checkNewRootAnchor(absOldRoot, absNewRoot);
        RootAnchorUtil.checkRootAnchor(absNewRoot, Cfg.absRTRoot(), Cfg.storageType(), false);

        InjectableFile fNewRoot = _factFile.create(absNewRoot);
        InjectableFile fOldRoot = _factFile.create(absOldRoot);

        // The new root folder must be created so that getAuxRoot() below won't fail.
        if (!fNewRoot.exists()) fNewRoot.mkdirs();
        boolean sameFS = OSUtil.get().isInSameFileSystem(absOldRoot, absNewRoot);

        IRelocator relocator = sameFS ? _sameFSRelocator : _crossFSRelocator;
        relocator.init_(sid, isDefaultRoot, fOldRoot, fNewRoot);

        // Delete the empty new root that we just created, so that the move and copy operations
        // work properly. Note: we know this is empty because either we just created it or we
        // asserted its emptiness in RootAnchorUtil.checkRootAnchor above.
        fNewRoot.delete();

        // Perform actual operations.
        l.warn(absOldRoot + " -> " + absNewRoot + ". same fs? " + sameFS);

        Trans t = _tm.begin_();
        try {
            relocator.doWork(t);

            if (isDefaultRoot) {
                Cfg.db().set(Key.ROOT, absNewRoot);
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
            }

            t.commit_();

        } catch (Exception e) {
            l.warn("Move failed. Rollback: " + Util.e(e));

            if (isDefaultRoot) Cfg.db().set(Key.ROOT, absOldRoot);
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
        // TODO(hugues): avoid restart for BlockStorage (fairly easy)
        // TODO(hugues): avoid restart for LinkedStorage? (pretty hard)
        ExitCode.RELOCATE_ROOT_ANCHOR.exit();
    }

    private interface IRelocator
    {
        /**
         * Initialize file parameters.
         */
        void init_(SID sid, boolean isDefaultRoot, InjectableFile oldRoot, InjectableFile newRoot);

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
        protected final boolean _isS3Storage;
        private final InjectableFile.Factory _factFile;


        AbstractRelocator(InjectableFile.Factory factFile)
        {
            _factFile = factFile;
            _isS3Storage = Cfg.storageType() == StorageType.S3;
        }

        protected SID _sid;
        protected boolean _isDefaultRoot;
        protected InjectableFile _oldRoot;
        protected InjectableFile _newRoot;
        protected InjectableFile _oldAuxRoot;
        protected InjectableFile _newAuxRoot;

        @Override
        public void init_(SID sid, boolean isDefaultRoot,
                InjectableFile oldRoot, InjectableFile newRoot)
        {
            _sid = sid;
            _isDefaultRoot = isDefaultRoot;

            _oldRoot = oldRoot;
            _newRoot = newRoot;

            _oldAuxRoot = _factFile.create(Cfg.absAuxRootForPath(oldRoot.getAbsolutePath(), sid));
            _newAuxRoot = _factFile.create(Cfg.absAuxRootForPath(newRoot.getAbsolutePath(), sid));
        }

        /**
         * Called after the root directory has been copied.
         *
         * The default implementation does nothing
         */
        protected void afterRootRelocation(Trans t) throws Exception
        {}
   }

    public static class SameFSRelocator extends AbstractRelocator
    {
        @Inject
        public SameFSRelocator(InjectableFile.Factory factFile)
        {
            super(factFile);
        }

        @Override
        public void doWork(Trans t) throws Exception
        {
            try {
                _oldRoot.moveInSameFileSystem(_newRoot);

                afterRootRelocation(t);

                // TeamServer does not have a default aux root
                if (!(_isDefaultRoot && L.isMultiuser())) {
                    _oldAuxRoot.moveInSameFileSystem(_newAuxRoot);
                }
            } catch (IOException e) {
                if (OSUtil.isWindows()) {
                    throw new ExInUse("files in the " + L.product() + " folder are in use. " +
                            "Please close all programs interacting with files in " +
                            L.product());
                }
                throw e;
            }
        }

        @Override
        public void rollback() throws Exception
        {
            // TeamServer does not have a default aux root
            if (!(_isDefaultRoot && L.isMultiuser()) && _newAuxRoot.exists()) {
                _newAuxRoot.moveInSameFileSystem(_oldAuxRoot);
            }
            if (_newRoot.exists()) _newRoot.moveInSameFileSystem(_oldRoot);
        }

        @Override
        public void onSuccessfulTransaction()
        {
            // Nothing to do.
        }
    }

    public static class CrossFSRelocator extends AbstractRelocator
    {
        protected final DirectoryService _ds;
        protected final InjectableDriver _dr;

        @Inject
        public CrossFSRelocator(InjectableFile.Factory factFile,
                DirectoryService ds, InjectableDriver dr)
        {
            super(factFile);

            _ds = ds;
            _dr = dr;
        }

        @Override
        public void doWork(Trans t) throws Exception
        {
            if (!_isS3Storage) {
                OSUtil.get().copyRecursively(_oldRoot, _newRoot, true, true);
            }

            afterRootRelocation(t);

            // TeamServer does not have a default aux root
            if (!(_isDefaultRoot && L.isMultiuser())) {
                OSUtil.get().copyRecursively(_oldAuxRoot, _newAuxRoot, false, false);
                OSUtil.get().markHiddenSystemFile(_newAuxRoot.getAbsolutePath());
            }
        }

        @Override
        public void rollback() throws Exception
        {
            // TeamServer does not have a default aux root
            if (!(_isDefaultRoot && L.isMultiuser())) {
                deleteFolder(_newAuxRoot);
            }
            deleteFolder(_newRoot);
        }

        @Override
        public void onSuccessfulTransaction()
        {
            // We only delete the old folders once we're absolutely sure everything was successful
            // Otherwise we risk deleting user data

            deleteFolder(_oldRoot);

            // TeamServer does not have a default aux root
            if (!(_isDefaultRoot && L.isMultiuser())) {
                deleteFolder(_oldAuxRoot);
            }
        }

        private void deleteFolder(InjectableFile folder)
        {
            if (!folder.deleteIgnoreErrorRecursively()) {
                l.warn("couldn't delete " + folder + ". ignored.");
            }
        }
    }
}
