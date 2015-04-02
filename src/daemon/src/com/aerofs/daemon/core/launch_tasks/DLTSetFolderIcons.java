/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.launch_tasks;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.ILinker;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgAbsDefaultRoot;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgStorageType;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile.Factory;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.os.OSUtil.Icon;
import org.slf4j.Logger;

import javax.inject.Inject;

import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;

/**
 * This class is intended to be a DaemonLaunchTask except it's not run by DaemonLaunchTasks because
 * we need to delay this task until the linker has finished
 */
public class DLTSetFolderIcons extends DaemonLaunchTask
{
    private final Logger l = Loggers.getLogger(DLTSetFolderIcons.class);

    private final InjectableDriver  _driver;
    private final CfgAbsDefaultRoot _cfgAbsDefRoot;
    private final CfgAbsRoots       _cfgAbsRoots;
    private final CfgStorageType    _cfgStorageType;
    private final StoreHierarchy    _ss;
    private final IMapSIndex2SID    _sidx2sid;
    private final IMapSID2SIndex    _sid2sidx;
    private final DirectoryService  _ds;
    private final ILinker           _linker;
    private final Factory           _factory;

    @Inject
    DLTSetFolderIcons(CoreScheduler sched, InjectableDriver driver, CfgAbsDefaultRoot cfgAbsRoot,
            CfgAbsRoots cfgAbsRoots, CfgStorageType cfgStorageType, StoreHierarchy ss,
            IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, DirectoryService ds,
            ILinker linker, Factory factory)
    {
        super(sched);
        _driver         = driver;
        _cfgAbsDefRoot  = cfgAbsRoot;
        _cfgAbsRoots    = cfgAbsRoots;
        _cfgStorageType = cfgStorageType;
        _ss             = ss;
        _sidx2sid       = sidx2sid;
        _sid2sidx       = sid2sidx;
        _ds             = ds;
        _linker         = linker;
        _factory        = factory;
    }

    @Override
    protected void run_()
            throws Exception
    {
        // don't update icons on Linux
        if (OSUtil.isLinux()) return;

        // don't set any folder icons if we are using S3/SWIFT storage.
        if (_cfgStorageType.get().isRemote()) return;

        l.info("Setting folder icons.");

        // N.B. we have to set the root anchor folder icon for local block storage as well
        setFolderIcon(_cfgAbsDefRoot.get(), Icon.RootAnchor);

        if (_cfgStorageType.get() == StorageType.LINKED) {
            // continuation
            new DLTSetFolderIconsForRoots(_sched, newLinkedList(_cfgAbsRoots.getAll().entrySet()))
                    .schedule();
        } else {
            // for local block storage
            l.info("Finished setting folder icons.");
        }
    }

    private void setFolderIcon(String absPath, Icon icon)
    {
        if (!_factory.create(absPath).isDirectory()) return;
        if (icon == Icon.SharedFolder
                && !_factory.create(absPath, LibParam.SHARED_FOLDER_TAG).isFile()) return;

        // this is debug because it reveals the user's file structure.
        l.debug("Setting shared folder icon at {}", absPath);

        // remove the previously-set folder icon first because Finder will sporadically fail to
        // refresh the icons otherwise.
        _driver.setFolderIcon(absPath, "");
        _driver.setFolderIcon(absPath, OSUtil.get().getIconPath(icon));
    }

    private class DLTSetFolderIconsForRoots extends DaemonLaunchTask
    {
        private final List<Entry<SID, String>> _roots;

        private final Executor _executor;

        DLTSetFolderIconsForRoots(CoreScheduler sched, List<Entry<SID, String>> roots)
        {
            super(sched);

            _roots = roots;

            _executor = Executors.newSingleThreadExecutor();
        }

        @Override
        protected void run_()
                throws Exception
        {
            List<String> paths = getAbsPathsForRootsAndAnchors_();

            if (!paths.isEmpty()) {
                // there's work to be done, dispatch work and reschedule when work's done
                dispatchUpdateTask(paths, () -> _sched.schedule(this, 1 * C.SEC));
            } else if (!_roots.isEmpty()) {
                // there's no work to be done immediately, but we are not done scanning yet
                // so reschedule.
                _sched.schedule(this, 1 * C.SEC);
            } else {
                l.info("Finished setting folder icons.");
            }
        }

        /**
         * @return a list of absolute paths for roots and anchors to update immediately
         */
        private List<String> getAbsPathsForRootsAndAnchors_()
        {
            List<String> absPaths = newArrayList();

            // N.B. this loop logic is a little funky because we need to deal with retries
            while (!_roots.isEmpty()) {
                if (absPaths.size() >= 200) {
                    // cap our memory usage at 200 paths
                    return absPaths;
                }

                SID sid = _roots.get(0).getKey();

                if (_linker.isFirstScanInProgress_(sid)) {
                    l.info("Waiting for scan session to finish {}", sid);
                    return absPaths;
                }

                try {
                    String absRoot = checkNotNull(_roots.get(0).getValue());
                    absPaths.add(absRoot);
                    SIndex sidx = checkNotNull(_sid2sidx.getNullable_(sid));

                    // set the folder icons for all anchors under this root
                    for (SIndex childSIdx : _ss.getChildren_(sidx)) {
                        try {
                            SID childSID = checkNotNull(_sidx2sid.getNullable_(childSIdx));
                            SOID soid = new SOID(sidx, SID.storeSID2anchorOID(childSID));
                            ResolvedPath path = checkNotNull(_ds.resolveNullable_(soid));

                            absPaths.add(path.toAbsoluteString(absRoot));
                        } catch (Exception e) {
                            // ignore and move on to the next anchor
                        }
                    }
                } catch (Exception e) {
                    // ignore and move on to the next root
                }

                _roots.remove(0);
            }

            return absPaths;
        }

        private void dispatchUpdateTask(List<String> absPaths, Runnable continuation)
        {
            _executor.execute(() -> {
                String absDefRoot = _cfgAbsDefRoot.get();

                for (String absPath : absPaths) {
                    try {
                        if (absPath.equals(absDefRoot)) {
                            continue;
                        }

                        setFolderIcon(absPath, Icon.SharedFolder);
                    } catch (Exception e) {
                        l.warn("Failed to update folder icon: {}", e.toString());
                        // ignore and continue
                    }
                }

                continuation.run();
            });
        }
    }
}
