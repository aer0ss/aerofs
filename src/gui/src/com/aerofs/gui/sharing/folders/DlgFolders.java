package com.aerofs.gui.sharing.folders;

import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

public class DlgFolders extends AeroFSDialog
{
    /**
     * @param path the path to the folder to be shared, relative to the root anchor path.
     */
    public DlgFolders(Shell parent)
    {
        super(parent, "Select Folder", false, true);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompFolders(shell);
    }
}
