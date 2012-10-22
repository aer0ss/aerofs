package com.aerofs.gui.exclusion;

import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

public class DlgExclusion extends AeroFSDialog
{
    public DlgExclusion(Shell parent)
    {
        super(parent, "Selective Sync", true, true);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompExclusion(shell);
    }
}
