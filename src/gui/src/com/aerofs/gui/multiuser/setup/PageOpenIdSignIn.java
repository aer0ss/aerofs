/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.google.common.base.Objects;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class PageOpenIdSignIn extends AbstractSignInPage
{
    private Text        _txtDeviceName;

    public PageOpenIdSignIn(Composite parent)
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

        _txtDeviceName.addModifyListener(onTextChanged);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        GridLayout welcomeLayout = new GridLayout(1, false);
        welcomeLayout.marginHeight = 0;
        welcomeLayout.marginWidth = 0;

        Composite welcomeComp = new Composite(content, SWT.NONE);
        welcomeComp.setLayout(welcomeLayout);
        welcomeComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1));

        Label welcomeLabel = new Label(welcomeComp, SWT.WRAP | SWT.CENTER);
        welcomeLabel.setText("Welcome to " + L.product() +
                " setup. Click the Continue button to sign in with your OpenId provider and" +
                " configure this device.");

        Label deviceNameLabel = new Label(content, SWT.NONE);
        deviceNameLabel.setText(S.SETUP_DEV_ALIAS + ':');

        _txtDeviceName = new Text(content, SWT.BORDER);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 100;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 10;
        content.setLayout(layout);

        welcomeLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        deviceNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return content;
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtDeviceName.setText(Objects.firstNonNull(model.getDeviceName(), ""));
        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model.setDeviceName(_txtDeviceName.getText().trim());
    }

    @Override
    protected boolean isInputValid()
    {
        String deviceName = _txtDeviceName.getText().trim();
        return !deviceName.isEmpty();
    }

    @Override
    protected void setControlState(boolean enabled)
    {
        super.setControlState(enabled);
        _btnQuit.setEnabled(true);
        _txtDeviceName.setEnabled(enabled);
    }
}
