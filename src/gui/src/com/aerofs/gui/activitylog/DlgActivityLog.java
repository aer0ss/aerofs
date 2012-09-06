package com.aerofs.gui.activitylog;

import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

public class DlgActivityLog extends AeroFSDialog
{
    private final Integer _initialSelection;

    public DlgActivityLog(Shell parent, Integer initialSelection)
    {
        super(parent, "Recent Activities", false, true);
        _initialSelection = initialSelection;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new CompActivityLog(shell, this, _initialSelection);
    }
}
