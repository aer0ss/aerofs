package com.aerofs.gui.exclusion;

import com.aerofs.lib.Path;

import java.util.Map;

/**
 * We use this class to pass data along from the GUI(CompExclusionList) to the class(CompExclusion)
 * that performs the backend calls to selectively sync/unsync.
 */
class SelectivelySyncedFolders
{
    // Maps of path to FolderData of those folders that have just been excluded or included by
    // the user in the Selective Sync dialog.
    Map<Path, FolderData> _newlyExcludedFolders;
    Map<Path, FolderData> _newlyIncludedFolders;
}