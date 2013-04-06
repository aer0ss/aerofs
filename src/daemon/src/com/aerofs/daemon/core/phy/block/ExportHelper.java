/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.event.admin.EIExportAll;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsAutoExportFolder;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.ICfgDatabaseListener;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableFile.Factory;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

public class ExportHelper implements ICfgDatabaseListener
{
    private final IMapSIndex2SID _sidx2sid;
    private final LocalACL _lacl;
    private final CfgAbsAutoExportFolder _exportFolder;
    private final InjectableFile.Factory _fileFactory;
    private static Logger l = Loggers.getLogger(ExportHelper.class);
    private final FrequentDefectSender _fds;
    private final InjectableFile.Factory _factFile;
    private final CoreScheduler _sched;
    private @Nullable String cachedAutoExportFolder;

    @Inject
    public ExportHelper(IMapSIndex2SID sidx2sid, LocalACL lacl, CfgAbsAutoExportFolder exportFolder,
            Factory fileFactory, FrequentDefectSender fds, Factory factFile, CoreScheduler sched)
    {
        _sidx2sid = sidx2sid;
        _lacl = lacl;
        _exportFolder = exportFolder;
        _fileFactory = fileFactory;
        _fds = fds;
        _factFile = factFile;
        _sched = sched;
        cachedAutoExportFolder = exportRoot();
        Cfg.db().addListener(this);
    }

    public String storeFullName_(SIndex sidx)
    {
        SID sid = _sidx2sid.get_(sidx);
        String storeTitle = sid.toStringFormal();
        if (sid.isUserRoot()) {
            storeTitle = purifyEmail(storeOwner_(sidx, sid).getString());
        } else {
            // Shared folders look like:
            // shared-folder-c12d379fed050c36bfd3496675a4fe47
            storeTitle = "shared-folder-" + storeTitle;
        }
        return storeTitle;
    }

    public UserID storeOwner_(SIndex sidx, SID sid)
    {
        // Loop over ACL entries, find non-self user, make folder with that name
        try {
            for (UserID uid : _lacl.get_(sidx).keySet()) {
                if (!uid.isTeamServerID()) {
                    assert SID.rootSID(uid).equals(sid);
                    return uid;
                }
            }
        } catch (SQLException e) {
            // LocalACL.get_ shouldn't throw here - it shouldn't be possible to receive events
            // about a store for which we have no ACL.
            SystemUtil.fatal("lacl get " + sidx + " " + sid + " " + e);
        }
        throw new AssertionError("store not accessible " + sidx + " " + sid);
    }

    public String purifyEmail(String email)
    {
        // Email addresses can have characters that are forbidden in file names.  Here, we strip
        // out the characters listed at
        // http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx
        // (which conveniently also covers all the characters forbidden on Unix systems)
        // I'm not going to deal with NFC/NFD here because that's over-the-top and the autoexport
        // folder is write-only, so it shouldn't matter.

        // Note: regex replacement is 3x as fast as chaining String.replace()
        // Note: the backslash is double-escaped: once for the compiler, and once for the regex.
        return email.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    @Nullable public String exportRoot()
    {
        return _exportFolder.get();
    }

    public void createExportFlagFile()
            throws IOException
    {
        assert exportRoot() != null;
        InjectableFile f = ongoingExportFlagFile();
        f.getParentFile().ensureDirExists();
        try {
            f.createNewFileIgnoreError();
        } catch (IOException e) {
            l.warn("Couldn't create export in-progress flag; may not continue export on crash");
            _fds.logSendAsync("Couldn't create export in-progress flag; " +
                    "may not continue export on crash", e);
        }
    }

    public boolean isExportOngoing()
    {
        return ongoingExportFlagFile().exists();
    }

    public void removeExportFlagFile()
    {
        assert exportRoot() != null;
        InjectableFile f = ongoingExportFlagFile();
        f.deleteIgnoreError();
        l.info("Completed full reexport.");
    }
    public InjectableFile ongoingExportFlagFile()
    {
        assert exportRoot() != null;
        return _fileFactory.create(exportRoot(), "export-in-progress");
    }

    @Nullable public String storeExportFolder(SIndex sidx)
    {
        // For root stores, the exported path is:
        // <Export root>/read-only-export/<user-email>/<path components>
        // For non-root stores (shared folders), the exported path is:
        // <Export root>/read-only-export/<sid>/<path components>

        // TODO (DF): symlink (or make a "Where are my files.txt" file) for anchors
        // Note that BlockPrefix is given by:
        // <Export root>/p/<prefix file>
        return Util.join(exportContentDir(exportRoot()), storeFullName_(sidx));
    }

    @Nullable public String exportContentDir(String exportRoot)
    {
        if (exportRoot == null) return null;
        return Util.join(exportRoot, "read-only-export");
    }

    public boolean exportEnabled()
    {
        return _exportFolder.get() != null;
    }

    @Override
    public void valueChanged_(Key key)
    {
        if (key.equals(Key.AUTO_EXPORT_FOLDER)) {
            if (cachedAutoExportFolder == null && exportEnabled()) {
                enableExport();
            }
            if (cachedAutoExportFolder != null && !exportEnabled()) {
                disableExport(cachedAutoExportFolder);
            }
            cachedAutoExportFolder = exportRoot();
        }
    }

    private void enableExport()
    {
        scheduleFullExportNow();
    }

    private void disableExport(String formerExportFolder)
    {
        // rm -rf the old read-only-export folder
        if (formerExportFolder != null) {
            File f = _factFile.create(exportContentDir(formerExportFolder)).getImplementation();
            FileUtil.deleteIgnoreErrorRecursively(
                    f,
                    ProgressIndicators.get());
        }
    }

    public void scheduleFullExportNow()
    {
        // Calling Core.imce() sucks, but you can't inject an IIMCExecutor because no implementers
        // of IIMCExecutor are registered at the time of this class's construction.
        _sched.schedule(new EIExportAll(Core.imce()), 0);
    }
}
