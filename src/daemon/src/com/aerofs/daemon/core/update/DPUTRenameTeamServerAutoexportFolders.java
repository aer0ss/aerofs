/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsAutoExportFolder;

import java.io.File;
import java.io.FilenameFilter;

public class DPUTRenameTeamServerAutoExportFolders implements IDaemonPostUpdateTask
{
    private final CfgAbsAutoExportFolder _autoExportFolder;

    public DPUTRenameTeamServerAutoExportFolders(CfgAbsAutoExportFolder autoExportFolder)
    {
        _autoExportFolder = autoExportFolder;
    }

    @Override
    public void run()
            throws Exception
    {
        // Don't do anything if there's no autoexport folder set.
        // This covers both single user installs and multi user installs with export disabled.
        String exportParent = _autoExportFolder.get();
        if (exportParent == null) return;
        String exportPath = Util.join(exportParent, "read-only-export");
        // Otherwise, rename every folder named like a store id into a folder named like
        // shared-folder-<sid>
        File exportFolder = new File(exportPath);
        String[] unconvertedSharedFolderNames = exportFolder.list(new FilenameFilter()
        {
            @Override
            public boolean accept(File file, String s)
            {
                // N.B. String.match() is true only if the pattern accounts for the entire string.
                return s.matches("[0-9a-f]{32}");
            }
        });
        for (String sharedFolderName : unconvertedSharedFolderNames) {
            String newName = "shared-folder-" + sharedFolderName;
            File share = new File(exportFolder, sharedFolderName);
            File target = new File(exportFolder, newName);
            // Ignore errors.  There's no reason this should fail and the rest of the autoexport
            // subsystem makes no assumptions, so we should soldier on silently in the case of
            // failure.
            share.renameTo(target);
        }
    }
}
