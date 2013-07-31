/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.google.common.base.Objects;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;

public class PageCredentialSignIn extends AbstractSignInPage
{
    private Text        _txtUserID;
    private Text        _txtPasswd;
    private Link        _lnkPasswd;
    private Text        _txtDeviceName;

    public PageCredentialSignIn(Composite parent)
    {
        super(parent);

        ModifyListener onTextChanged = new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                validateInput();
            }
        };

        _txtUserID.addModifyListener(onTextChanged);
        _txtPasswd.addModifyListener(onTextChanged);
        _txtDeviceName.addModifyListener(onTextChanged);

        _lnkPasswd.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                GUIUtil.launch(WWW.PASSWORD_RESET_REQUEST_URL.get());
            }
        });
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblMessage = new Label(composite, SWT.NONE);
        lblMessage.setText(S.SETUP_MESSAGE);

        Label lblUserID = new Label(composite, SWT.NONE);
        lblUserID.setText(S.ADMIN_EMAIL + ':');

        _txtUserID = new Text(composite, SWT.BORDER);
        _txtUserID.setFocus();

        Label lblPasswd = new Label(composite, SWT.NONE);
        lblPasswd.setText(S.ADMIN_PASSWD + ':');

        _txtPasswd = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        _lnkPasswd = new Link(composite, SWT.NONE);
        _lnkPasswd.setText(S.SETUP_LINK_FORGOT_PASSWD);

        Label lblDeviceName = new Label(composite, SWT.NONE);
        lblDeviceName.setText(S.SETUP_DEV_ALIAS + ':');

        _txtDeviceName = new Text(composite, SWT.BORDER);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 10;
        composite.setLayout(layout);

        GridData messageLayoutData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        messageLayoutData.heightHint = 30;
        lblMessage.setLayoutData(messageLayoutData);
        lblUserID.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUserID.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblPasswd.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _lnkPasswd.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false, 2, 1));
        lblDeviceName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return composite;
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        // do not populate password from model
        _txtUserID.setText(Objects.firstNonNull(model.getUsername(), ""));
        _txtDeviceName.setText(Objects.firstNonNull(model.getDeviceName(), ""));
        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model.setUserID(_txtUserID.getText().trim());
        model.setPassword(_txtPasswd.getText());
        model.setDeviceName(_txtDeviceName.getText().trim());
    }

    @Override
    protected void setControlState(boolean enabled)
    {
        super.setControlState(enabled);
        _txtUserID.setEnabled(enabled);
        _txtPasswd.setEnabled(enabled);
        _txtDeviceName.setEnabled(enabled);
        _lnkPasswd.setEnabled(enabled);
    }

    @Override
    protected boolean isInputValid()
    {
        String userID = _txtUserID.getText().trim();
        String passwd = _txtPasswd.getText();
        String deviceName = _txtDeviceName.getText().trim();

        return !userID.isEmpty() && Util.isValidEmailAddress(userID)
                && !passwd.isEmpty() && !deviceName.isEmpty();
    }
}
