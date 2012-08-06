/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.password;

import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public class DlgPasswordReset extends AeroFSJFaceDialog
{

    private String _userID;
    private String _token;
    private String _password;

    private Label _lblUserID;
    private Text _txtUserID;

    private Label _lblExplainToken;
    private Label _lblToken;
    private Text _txtToken;

    private Label _lblExplainPassword;
    private Label _lblPassword;
    private Label _lblPassword2;
    private Text _txtPassword;
    private Text _txtPassword2;

    private Label _lblStatus;

    public DlgPasswordReset(Shell parentShell, String userID)
    {
         super("Reset Your Password", parentShell, true, false, false, false);
        _userID = userID;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout(5, true);
        gridLayout.marginRight = 0;
        gridLayout.marginTop = 20;
        gridLayout.marginLeft = 0;
        container.setLayout(gridLayout);

        _lblUserID = new Label(container, SWT.NONE);
        _lblUserID.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        _lblUserID.setText("Email Address:");

        _txtUserID = new Text(container, SWT.BORDER);
        _txtUserID.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
        _txtUserID.setText(_userID);

        Button btnSendEmail = new Button(container,SWT.NONE);
        btnSendEmail.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
        btnSendEmail.setText("Send");
        btnSendEmail.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selefctionEvent)
            {
                try {
                    updateStatus("Sending Email to: " + _txtUserID.getText());
                    UI.controller().sendPasswordResetEmail(_txtUserID.getText());
                    _lblExplainToken.setVisible(true);
                    _lblToken.setVisible(true);
                    _txtToken.setVisible(true);
                    updateStatus("Email Sent.");
                } catch (Exception e) {
                    updateStatus("Error: " + UIUtil.e2msgNoBracket(e));
                }
            }
        });

        _lblExplainToken = new Label(container, SWT.WRAP);
        _lblExplainToken.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false ,false, 5, 1));
        _lblExplainToken.setText(
                "You should receive an email containing a token.\n" +
                "Please copy and paste that token into the box below.\n");
        _lblExplainToken.setVisible(false);

        _lblToken = new Label(container, SWT.NONE);
        _lblToken.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        _lblToken.setText("Token:");
        _lblToken.setVisible(false);

        _txtToken = new Text(container, SWT.BORDER);
        _txtToken.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
        _txtToken.setVisible(false);
        _txtToken.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent ev) {
                _lblExplainPassword.setVisible(true);
                _lblPassword.setVisible(true);
                _lblPassword2.setVisible(true);
                _txtPassword.setVisible(true);
                _txtPassword2.setVisible(true);
            }
        });

        new Label(container, SWT.NONE);

        _lblExplainPassword = new Label(container, SWT.WRAP);
        _lblExplainPassword.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 5,
                3));
        _lblExplainPassword.setVisible(false);
        _lblExplainPassword.setText("Please set a new password below.\n  Click OK when finished.");

        _lblPassword = new Label(container, SWT.NONE);
        _lblPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        _lblPassword.setText("New Password:");
        _lblPassword.setVisible(false);

        _txtPassword = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
        _txtPassword.setVisible(false);

        new Label(container, SWT.NONE);

        _lblPassword2 = new Label(container, SWT.NONE);
        _lblPassword2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        _lblPassword2.setText("Confirm New Password:");
        _lblPassword2.setVisible(false);

        _txtPassword2 = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPassword2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
        _txtPassword2.setVisible(false);
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
        _lblStatus.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, false, 5, 1));


        return container;
    }

    /**
     * Create contents of the button bar
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        getButton(IDialogConstants.OK_ID).setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        _password = _txtPassword.getText();
        _token = _txtToken.getText();
        _userID = _txtUserID.getText();
        super.buttonPressed(buttonId);
    }

    private void updateStatus(String status)
    {
        _lblStatus.setText(status);
        _lblStatus.pack();
    }

    public String getUserID()
    {
        return _userID;
    }
    public String getPassword()
    {
        return _password;
    }
    public String getToken()
    {
        return _token;
    }
}
