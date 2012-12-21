package com.aerofs.gui.singleuser.preferences;

import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

public class SingleuserDlgPreferences extends AeroFSDialog
{
    private final boolean _showTransfers;

    public SingleuserDlgPreferences(Shell parent, boolean showTransfers)
    {
        super(parent, S.PREFERENCES, false, false);
        _showTransfers = showTransfers;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        new SingleuserCompPreferences(shell, _showTransfers);

        shell.pack();
    }
}
