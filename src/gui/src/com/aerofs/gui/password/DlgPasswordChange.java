/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.password;

import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class DlgPasswordChange extends AeroFSJFaceDialog
{
    private String _oldPassword;
    private String _password;

    private Label _lblOldPassword;
    private Label _lblPassword;
    private Label _lblPassword2;

    private Text _txtOldPassword;
    private Text _txtPassword;
    private Text _txtPassword2;

    private Label _lblStatus;

    public DlgPasswordChange(Shell parentShell)
    {
        super("Change Your Password", parentShell, true, false, false, false);

    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout(3,false);
        gridLayout.marginRight = 0;
        gridLayout.marginTop = 20;
        gridLayout.marginLeft = 0;
        container.setLayout(gridLayout);

        _lblOldPassword = new Label(container, SWT.NONE);
        _lblOldPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblOldPassword.setText("Current Password");

        _txtOldPassword = new Text(container, SWT.BORDER | SWT.PASSWORD);
        GridData gdOP = new GridData(SWT.FILL, SWT.CENTER, false ,false, 1, 1);
        gdOP.widthHint = 100;
        _txtOldPassword.setLayoutData(gdOP);

        new Label(container, SWT.NONE);

        _lblPassword = new Label(container, SWT.NONE);
        _lblPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblPassword.setText("New Password");

        _txtPassword = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        new Label(container, SWT.NONE);

        _lblPassword2 = new Label(container, SWT.NONE);
        _lblPassword2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblPassword2.setText("Confirm New Password");

        _txtPassword2 = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPassword2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        _txtPassword2.addModifyListener(new ModifyListener()
        {
            public void modifyText(ModifyEvent ev)
            {

                String passwd = _txtPassword.getText();
                String passwd2 = _txtPassword2.getText();
                String err = "";
                boolean ready = true;
                if (passwd.isEmpty()) {
                    ready = false;
                } else if (passwd.length() < Param.MIN_PASSWD_LENGTH) {
                    ready = false;
                    err = S.SETUP_PASSWD_TOO_SHORT;
                } else if (!Util.isValidPassword(passwd.toCharArray())) {
                    ready = false;
                    err = S.SETUP_PASSWD_INVALID;
                } else if (!passwd.equals(passwd2)) {
                    ready = false;
                    err = S.SETUP_PASSWD_DONT_MATCH;
                }

                if (ready) {
                    getButton(IDialogConstants.OK_ID).setEnabled(true);
                    updateStatus("");
                } else {
                    updateStatus(err);
                }
            }
        });

        new Label(container, SWT.NONE);

        _lblStatus = new Label(container, SWT.NONE);
        GridData gdls = new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1);
        gdls.verticalIndent = 10;
        _lblStatus.setLayoutData(gdls);
        _lblStatus.setText("");


        return container;
    }

    @Override
    protected void buttonPressed(int buttonID)
    {
        _password = _txtPassword.getText();
        _oldPassword = _txtOldPassword.getText();
        super.buttonPressed(buttonID);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        getButton(IDialogConstants.OK_ID).setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }


    private void updateStatus(String status)
    {
        _lblStatus.setText(status);
        _lblStatus.pack();
    }

    public String getPassword()
    {
        return _password;
    }

    public String getOldPassword()
    {
        return _oldPassword;
    }
}
