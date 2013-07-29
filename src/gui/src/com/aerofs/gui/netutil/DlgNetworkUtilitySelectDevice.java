package com.aerofs.gui.netutil;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Util;

import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

public class DlgNetworkUtilitySelectDevice extends AeroFSDialog
{
    private static final Logger l = Loggers.getLogger(DlgNetworkUtilitySelectDevice.class);

    private Text _txtDID;
    private Button _btnOK;

    /**
     * @param okay called in a non-UI thread when the joining succeeded
     */
    public DlgNetworkUtilitySelectDevice(Shell parent, boolean sheet)
    {
        super(parent, "Enter Device ID", sheet, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        GridLayout glShell = new GridLayout(3, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        glShell.verticalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(glShell);

        Label lblDeviceIdsCan = new Label(shell, SWT.NONE);
        lblDeviceIdsCan.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        lblDeviceIdsCan.setText("Input the ID of a remote computer below. It can be found\non the remote computer by using Preferecnes... > Settings,\nand clicking on the user ID.");

        Label lblInvitationCode = new Label(shell, SWT.NONE);
        lblInvitationCode.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblInvitationCode.setText("Computer ID:");

        _txtDID = new Text(shell, SWT.BORDER);
        _txtDID.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                if (_btnOK.isEnabled()) work();
            }
        });

        GridData gdText = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        gdText.widthHint = 160;
        _txtDID.setLayoutData(gdText);
        _txtDID.addVerifyListener(new VerifyListener() {
            public void verifyText(VerifyEvent e)
            {
                verify(GUIUtil.getNewText(_txtDID, e));
            }
        });

        Button btnCancel = GUIUtil.createButton(shell, SWT.NONE);
        btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
        btnCancel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
        btnCancel.setText(" " + IDialogConstants.CANCEL_LABEL + " ");

        _btnOK = GUIUtil.createButton(shell, SWT.NONE);
        _btnOK.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _btnOK.setText(" " + IDialogConstants.OK_LABEL + " ");
        _btnOK.setEnabled(false);
        _btnOK.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                work();
            }
        });
    }

    private void verify(String did)
    {
        if (did == null) did = _txtDID.getText();

        boolean ready = true;
        try {
            new DID(did);
        } catch (Exception e) {
            ready = false;
        }

        _btnOK.setEnabled(ready);
    }

    private void work()
    {
        try {
            String strDID = _txtDID.getText();
            DID did = new DID(strDID);
            new DlgNetworkUtility(getParent(), did, strDID, null).open();
            closeDialog();
        } catch (Exception e) {
            l.warn(Util.e(e));
        }
    }
}
