package com.aerofs.gui.sharing;

import com.aerofs.ui.UIUtil;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

public class DlgCreateSharedFolder extends AeroFSDialog
{
    private final Path _path;

    /**
     * @param path the path to the folder to be shared, relative to the root anchor path.
     */
    public DlgCreateSharedFolder(Shell parent, Path path)
    {
        super(parent, "Share Folder " + Util.quote(UIUtil.sharedFolderName(path, null)), false,
                false);
        _path = path;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        CompInviteUsers.createForNewSharedFolder(shell, _path, true);
    }
}
