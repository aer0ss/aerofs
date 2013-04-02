package com.aerofs.gui.transfers;

import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

public class DlgTransfers extends AeroFSDialog
{
    private CompTransfers _comp;
    private boolean _showSOCID;

    /**
     * @param path the path to the folder to be shared, relative to the root anchor path.
     */
    public DlgTransfers(Shell parent)
    {
        super(parent, S.TRANSFERS, false, true);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setSize(430, 380);
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        _comp = new CompTransfers(shell);
        _comp.showSOCID(_showSOCID);
    }

    public void showSOCID(boolean enable)
    {
        _showSOCID = enable;
        if (_comp != null) _comp.showSOCID(_showSOCID);
    }
}
