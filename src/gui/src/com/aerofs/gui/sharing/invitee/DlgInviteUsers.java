package com.aerofs.gui.sharing.invitee;

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
 * a shared or non-shared folder or when the user select tray menu -> "Manage Shared Folders..." ->
 * "Invite Others..."
 */
public class DlgInviteUsers extends AeroFSDialog
{
    private final Path _path;
    private final String _name;

    public DlgInviteUsers(Shell parent, String label, Path path, String name, boolean sheet)
    {
        super(parent, label, sheet, false);
        _path = path;
        _name = name;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompInviteUsers(shell, _path, _name, true);
    }

    public static String getLabelByPath(Path path)
    {
        return "Share Folder " + Util.quote(UIUtil.sharedFolderName(path, ""));
    }

    public static String getLabelByName(String name)
    {
        return "Invite Members to " + Util.quote(name);
    }
}
