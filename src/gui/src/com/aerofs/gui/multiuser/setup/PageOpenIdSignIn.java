/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.base.Objects;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import java.net.ConnectException;

import static org.eclipse.jface.dialogs.IDialogConstants.OK_ID;

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
