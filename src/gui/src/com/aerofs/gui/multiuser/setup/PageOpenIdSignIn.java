/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIParam;
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
    private Text _txtDeviceName;

    public PageOpenIdSignIn(Composite parent)
    {
        super(parent);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Label lblWelcome = new Label(content, SWT.WRAP);
        lblWelcome.setText("Welcome to " + L.product() + " setup.\n\n"
                + "Click Continue to sign in with your OpenID Provider and "
                + "configure this device.");

        Composite compDeviceName = createDeviceNameComposite(content);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 80;
        layout.marginHeight = 0;
        layout.verticalSpacing = GUIParam.MAJOR_SPACING;
        content.setLayout(layout);

        lblWelcome.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        compDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return content;
    }

    protected Composite createDeviceNameComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblDeviceName = new Label(composite, SWT.NONE);
        lblDeviceName.setText(S.SETUP_DEV_ALIAS + ':');

        _txtDeviceName = new Text(composite, SWT.BORDER);
        _txtDeviceName.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                validateInput();
            }
        });

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 60;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 10;
        composite.setLayout(layout);

        lblDeviceName.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return composite;
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
