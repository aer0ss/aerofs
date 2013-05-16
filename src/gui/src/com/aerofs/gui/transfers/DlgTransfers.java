package com.aerofs.gui.transfers;

import com.aerofs.gui.TransferState;
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
    private boolean _showDID;
    private TransferState _ts;

    public DlgTransfers(Shell parent)
    {
        super(parent, S.TRANSFERS, false, true);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());
        shell.setSize(800, 400);
        shell.setLayout(new FillLayout(SWT.HORIZONTAL));

        _comp = new CompTransfers(shell);
        _comp.showSOCID(_showSOCID);
        _comp.showDID(_showDID);
        _comp.setTransferState(_ts);
    }

    public void showSOCID(boolean enable)
    {
        _showSOCID = enable;
        if (_comp != null) _comp.showSOCID(_showSOCID);
    }

    public void showDID(boolean enable)
    {
        _showDID = enable;
        if (_comp != null) _comp.showDID(_showDID);
    }

    public void setTransferState(TransferState ts)
    {
        _ts = ts;
        if (_comp != null) _comp.setTransferState(_ts);
    }
}
