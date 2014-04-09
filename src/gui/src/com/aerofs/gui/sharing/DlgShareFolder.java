package com.aerofs.gui.sharing;

import com.aerofs.ui.UIUtil;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

/**
 * This dialog is used when the user selects shell extension's "Share This Folder..." menu on either
 * a shared or non-shared folder.
 */
public class DlgShareFolder extends AeroFSDialog
{
    private final Path _path;

    public DlgShareFolder(Shell parent, Path path)
    {
        super(parent, "Share Folder " + Util.quote(UIUtil.sharedFolderName(path, "")), false, false);
        _path = path;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompInviteUsers(shell, _path, true);
    }
}
