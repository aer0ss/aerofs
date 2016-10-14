/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.ids.SID;
import com.aerofs.gui.TaskDialog;
import com.aerofs.gui.sharing.folders.DlgFolders;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * These dialogs are used by DlgManageSharedFolders when the user creates new shared folders.
 */
class AddSharedFolderDialogs
{
    private final IShareNewFolderCallback _callback;
    private Shell _parentShell;

    /**
     * This callback is used to indicate that the user has successfully shared a folder. The path
     * parameter is the Path to the new shared folder.
     *
     * For internal folders, the path is '{root_store_id}:/path/to/subfolder'
     * For external folders, the path is '{store_id}:'
     */
    interface IShareNewFolderCallback
    {
        void onSuccess(@Nonnull Path path);
    }

    private interface IShareNewFolder {
        // Get the name of the new shared folder
        @Nonnull String getName();
        // Share the specified folder. Return the Path of the shared folder.
        @Nonnull Path share() throws Exception;
    }

    AddSharedFolderDialogs(Shell parent, IShareNewFolderCallback callback)
    {
        _parentShell = parent;
        _callback = callback;
    }

    public void open(boolean allowExternalFolder)
    {
        @Nullable IShareNewFolder snf = allowExternalFolder ?
                    selectArbitraryFolderToShare() : selectInternalFolderToShare();
        if (snf != null) shareFolder(snf);
    }

    private Path shareInternalFolder(Path path) throws Exception
    {
        List<PBSubjectPermissions> sps = Collections.emptyList();
        UIGlobals.ritual().shareFolder(path.toPB(), sps, "", false);
        return path;
    }

    private Path shareExternalFolder(String path) throws Exception
    {
        SID sid = new SID(BaseUtil.fromPB(UIGlobals.ritual().createRoot(path).getSid()));
        return new Path(sid);
    }

    /**
     * This method allows users to pick a folder out of the default root anchor.
     *
     * @return null if the user cancels the selection
     */
    private @Nullable IShareNewFolder selectArbitraryFolderToShare()
    {
        DirectoryDialog dlg = new DirectoryDialog(_parentShell, SWT.SHEET);
        if (!L.isMultiuser()) dlg.setFilterPath(Cfg.absDefaultRootAnchor());
        dlg.setMessage("Select a folder to share:");
        final String path = dlg.open();
        if (path == null) return null;
        return new IShareNewFolder() {
            @Override
            public @Nonnull String getName()
            {
                return new File(path).getName();
            }

            @Override
            public @Nonnull Path share()
                    throws Exception
            {
                // Even after clicking shift add, the user is trying to share a folder within
                // the AeroFS folder. We need to check if the client is TS because all folders are
                // external from the TS point of view, and TS must only call shareExternalFolders.
                if (!L.isMultiuser() && path.startsWith(Cfg.absDefaultRootAnchor())) {
                    return shareInternalFolder(UIUtil.getPathNullable(path));
                }
                return shareExternalFolder(path);
            }
        };
    }

    /**
     * This method limits folder selection within the default root anchor.
     *
     * @return null if the user cancels the selection
     */
    private @Nullable IShareNewFolder selectInternalFolderToShare()
    {
        final Path path = (Path) new DlgFolders(_parentShell, true).openDialog();
        if (path == null) return null;
        return new IShareNewFolder() {
            @Override
            public @Nonnull String getName()
            {
                return path.last();
            }

            @Override
            public @Nonnull Path share() throws Exception
            {
                return shareInternalFolder(path);
            }
        };
    }

    public void shareFolder(@Nonnull final IShareNewFolder snf)
    {
        new TaskDialog(_parentShell, "Sharing Folder", null, "Sharing " + Util.quote(snf.getName())) {
            Path _path;
            @Override
            public void run() throws Exception
            {
                _path = snf.share();
            }

            @Override
            public void okay()
            {
                super.okay();
                _callback.onSuccess(_path);
            }

            @Override
            public void error(Exception e)
            {
                ErrorMessages.show(getShell(), e, L.product() + " could not share this folder.",
                        new ErrorMessage(ExChildAlreadyShared.class, S.CHILD_ALREADY_SHARED),
                        new ErrorMessage(ExParentAlreadyShared.class, S.PARENT_ALREADY_SHARED),
                        new ErrorMessage(ExNoPerm.class, S.NON_OWNER_CANNOT_SHARE));

                errorWithNoErrorMessage();
            }
        }.openDialog();
    }
}
