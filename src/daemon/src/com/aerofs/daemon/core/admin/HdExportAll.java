/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.ObjectWalkerAdapter;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.core.phy.block.ExportHelper;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.admin.EIExportAll;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Set;

public class HdExportAll extends AbstractHdIMC<EIExportAll>
{
    private static final Logger l = Loggers.getLogger(HdExportAll.class);
    private final DirectoryService _ds;
    private final IStores _stores;
    private final ExportHelper _eh;
    private final LinkStateService _lss;
    private final TC _tc;

    @Inject
    public HdExportAll(DirectoryService ds, IStores stores, ExportHelper eh, LinkStateService lss,
            TC tc)
    {
        _ds = ds;
        _stores = stores;
        _eh = eh;
        _lss = lss;
        _tc = tc;
    }

    private class ExportContext
    {
        public final File parentDir;
        private ExportContext(File parent)
        {
            parentDir = parent;
        }
    }

    private class ExportWalker extends ObjectWalkerAdapter<ExportContext>
    {
        @Nullable
        @Override
        public ExportContext prefixWalk_(ExportContext cookieFromParent, OA oa)
                throws IOException, SQLException, ExStreamInvalid, ExNotFound, ExNotDir,
                ExAlreadyExist
        {
            OID oid = oa.soid().oid();
            if (oid.isTrash()) {
                return null;
            }
            File thisFile = oid.isRoot() ? cookieFromParent.parentDir :
                    new File(cookieFromParent.parentDir, oa.name());
            l.debug("export: on {}", thisFile);
            switch(oa.type()) {
            case FILE:
                if (thisFile.exists()) {
                    // We've already got this file on the FS; no need to redo this work.
                    break;
                }
                CA masterCA = oa.caMasterNullable();
                if (masterCA == null) {
                    // We have the META but no CONTENT for this file.  Skip.
                    break;
                }
                // Copy the file to a temp file, then move it to the export folder.
                InputStream is = masterCA.physicalFile().newInputStream_();
                // We create temp files in the same folder as the export filename because it'd be
                // nice if that renameTo() were fast.
                File tempFile = FileUtil.createTempFile("export-in-progress", null,
                        thisFile.getParentFile(), false);
                OutputStream os = new FileOutputStream(tempFile);
                try {
                    l.debug("Copying data to {}", tempFile);
                    byte[] buf = new byte[Param.FILE_BUF_SIZE];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        os.write(buf, 0, len);
                        os.flush();
                        // Who knows how much data could need to be copied here?
                        // Could be a lot, in the case of reexporting a ~4GB file.
                        ProgressIndicators.get().incrementMonotonicProgress();
                    }
                } finally {
                    is.close();
                    os.close();
                }
                thisFile.delete(); // Try to make sure the target file doesn't exist
                boolean fileExportedSuccessfully = tempFile.renameTo(thisFile);
                if (!fileExportedSuccessfully) {
                    l.warn("export: failed to export {}", oa.soid());
                    tempFile.delete();
                };
                l.debug("Successfully exported {}", thisFile);
                break;
            case DIR:
                FileUtil.ensureDirExists(thisFile);
                break;
            case ANCHOR:
                // TODO (DF): create symbolic link or shortcut on supported platforms to target
                // shared folder?
                l.debug("Ignoring anchor: {}", thisFile);
                break;
            }
            return new ExportContext(thisFile);
        }
    }

    @Override
    protected void handleThrows_(EIExportAll ev, Prio prio)
            throws Exception
    {
        // This is only intended for multiuser.
        _eh.createExportFlagFile();
        // We wish to pause syncing so the transport threads won't fill up the core queue during
        // this long operation.
        final boolean shouldPauseSync = !_lss.linksManuallyDowned();
        try {
            if (shouldPauseSync) {
                _lss.markLinksDown_();
            }
            // In multiuser, we can't walk DirectoryService from the root store object - we have to
            // walk the paths from each store root.
            Set<SIndex> roots = _stores.getAll_();
            for (SIndex sidx : roots) {
                // Get the SOID for this store's root.
                SOID soid = new SOID(sidx, OID.ROOT);
                ExportContext ec = new ExportContext(new File(_eh.storeExportFolder(sidx)));
                // Walk DirectoryService from this root, creating folders and files.
                // We skip files that already exist, and all anchors, but walk folders regardless of
                // their previous existence.
                _ds.walk_(soid, ec, new ExportWalker());
            }
            _eh.removeExportFlagFile();
            // We are now done with everything that needs the core lock.
        } finally {
            if (shouldPauseSync) {
                Token tk = _tc.acquire_(Cat.UNLIMITED, "iface-up");
                try {
                    _lss.markLinksUp_(tk);
                } finally {
                    tk.reclaim_();
                }
            }
        }
    }
}
