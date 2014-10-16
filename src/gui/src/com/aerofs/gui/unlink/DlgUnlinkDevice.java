package com.aerofs.gui.unlink;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.lib.S;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.events.*;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.ui.UIUtil;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class DlgUnlinkDevice extends AeroFSDialog implements ISWTWorker
{
    private Label _lblStatus;
    private CompSpin _compSpin;
    private Button _btnOK;
    private Button _btnCancel;

    public DlgUnlinkDevice(Shell parent, boolean sheet)
    {
        super(parent, "Unlink Computer", sheet, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) {
            shell = new Shell(getParent(), getStyle());
        }

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        glShell.verticalSpacing = GUIParam.MAJOR_SPACING;
        glShell.horizontalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(glShell);

        Composite composite = new Composite(shell, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        GridLayout glComposite = new GridLayout(3, false);
        composite.setLayout(glComposite);

        CLabel lblIcon = new CLabel(composite, SWT.NONE);
        lblIcon.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, true, 1, 1));
        lblIcon.setImage(getShell().getDisplay().getSystemImage(SWT.ICON_WARNING));

        _compSpin = new CompSpin(composite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        _lblStatus = new Label(composite, SWT.WRAP);
        GridData gdText = new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1);
        gdText.widthHint = 360;
        _lblStatus.setLayoutData(gdText);
        _lblStatus.setText(S.UNLINK_THIS_COMPUTER_CONFIRM);

        setStatusText(S.UNLINK_THIS_COMPUTER_CONFIRM, true);

        Composite composite2 = new Composite(shell, SWT.NONE);
        composite2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 2));
        composite2.setLayout(new FillLayout(SWT.HORIZONTAL));

        // An awesome customer's fingerspitzengefuhl demands the buttons to be arranged OK, Cancel.
        // see ENG-2115
        _btnOK = GUIUtil.createButton(composite2, SWT.NONE);
        _btnOK.setText(" " + IDialogConstants.YES_LABEL + " ");
        _btnOK.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                enableAll(false);
                _compSpin.start();
                setStatusText("Unlinking computer...", false);

                GUI.get().safeWork(getShell(), DlgUnlinkDevice.this);
            }
        });

        _btnCancel = GUIUtil.createButton(composite2, SWT.NONE);
        _btnCancel.setText(" " + IDialogConstants.NO_LABEL + " ");
        _btnCancel.setFocus();
        _btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
    }

    private void enableAll(boolean b)
    {
        _btnOK.setEnabled(b);
        _btnCancel.setEnabled(b);
    }

    private void setStatusText(String msg, boolean packShell)
    {
        _lblStatus.setText(msg);
        _lblStatus.pack();
        _lblStatus.getParent().layout();
        if (packShell) getShell().pack();
    }

    @Override
    public void run()
            throws Exception
    {
        UIUtil.scheduleUnlinkAndExit();
    }

    @Override
    public void error(Exception e)
    {
        enableAll(true);
        _compSpin.stop();
        setStatusText(S.UNLINK_THIS_COMPUTER_CONFIRM, true);

        String msg = S.COULDNT_UNLINK_DEVICE + "\n\nError message: " + ErrorMessages.e2msgDeprecated(
                e) + ".";
        GUI.get().show(getShell(), MessageType.ERROR, msg);
    }

    @Override
    public void okay()
    {
        // noop
    }
}
