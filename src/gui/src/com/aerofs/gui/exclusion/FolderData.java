/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.exclusion;

/**
 * This is package specific class that is responsible for holding shared folder data. We need to
 * have this class because ritual calls that are needed to display folders in the Selective Sync
 * dialog may return different different data types. More specifically, listExcludedFolders returns
 * PBPaths whereas listSharedFolders returns PBSharedFolders. So we convert both of them to
 * FolderData which contains all the relevant information needed by the Selective Sync dialog.
 */
class FolderData
{
   public final String _name;
   public final boolean _isShared;
   public final boolean _isInternal;
   public final String _absPath;

   FolderData(String name, boolean isShared, boolean isInternal, String absPath) {
       _name = name;
       _absPath = absPath;
       _isShared = isShared;
       _isInternal = isInternal;
   }
}