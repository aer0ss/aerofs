/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;
import static com.google.common.base.Objects.firstNonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class PageSwiftStorage extends PageStorageBackend
{
    private Text        _txtUrl;
    private Text        _txtContainerName;
    private Text        _txtUsername;
    private Text        _txtPassword;

    public PageSwiftStorage(Composite parent)
    {
        super(parent);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Link lnkSwift = new Link(content, SWT.WRAP);
        lnkSwift.setText(S.SETUP_SWIFT_CONFIG_DESC);
        lnkSwift.addSelectionListener(createUrlLaunchListener(BLOCK_STORAGE_HELP_URL));

        Composite compConfig = createConfigurationComposite(content);

        ModifyListener onInputChanged = createListenerToValidateInput();

        _txtUrl.addModifyListener(onInputChanged);
        _txtContainerName.addModifyListener(onInputChanged);
        _txtUsername.addModifyListener(onInputChanged);
        _txtPassword.addModifyListener(onInputChanged);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 0;
        content.setLayout(layout);

        lnkSwift.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compConfig.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return content;
    }

    protected Composite createConfigurationComposite(Composite parent)
    {
        Composite compConfig = new Composite(parent, SWT.NONE);

        Label lblUrl = createLabel(compConfig, SWT.NONE);
        lblUrl.setText(S.SETUP_SWIFT_URL_GUI);
        _txtUrl = new Text(compConfig, SWT.BORDER);
        _txtUrl.setFocus();

        Label lblContainerName = createLabel(compConfig, SWT.NONE);
        lblContainerName.setText(S.SETUP_SWIFT_CONTAINER_GUI);
        _txtContainerName = new Text(compConfig, SWT.BORDER);

        Label lblUsername = createLabel(compConfig, SWT.NONE);
        lblUsername.setText(S.SETUP_SWIFT_USERNAME_GUI);
        _txtUsername = new Text(compConfig, SWT.BORDER);

        Label lblPassword = createLabel(compConfig, SWT.NONE);
        lblPassword.setText(S.SETUP_SWIFT_PASSWORD_GUI);
        _txtPassword = new Text(compConfig, SWT.BORDER | SWT.PASSWORD);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 60;
        layout.marginHeight = 10;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 5;
        compConfig.setLayout(layout);

        lblUrl.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUrl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblContainerName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtContainerName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblUsername.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUsername.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return compConfig;
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtUrl.setText(firstNonNull(model._backendConfig._swiftConfig._url, ""));
        _txtContainerName.setText(firstNonNull(model._backendConfig._swiftConfig._container, ""));
        _txtUsername.setText(firstNonNull(model._backendConfig._swiftConfig._username, ""));
        _txtPassword.setText(firstNonNull(model._backendConfig._swiftConfig._password, ""));

        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        // For now, only "basic" mode is supported
        model._backendConfig._swiftConfig._authMode     = "BASIC";
        model._backendConfig._swiftConfig._url          = _txtUrl.getText().trim();
        model._backendConfig._swiftConfig._container    = _txtContainerName.getText().trim();
        model._backendConfig._swiftConfig._username     = _txtUsername.getText();
        model._backendConfig._swiftConfig._password     = _txtPassword.getText();
    }

    @Override
    protected boolean isInputValid()
    {
        return isNotBlank(_txtUrl.getText())
                && isNotBlank(_txtContainerName.getText())
                && isNotEmpty(_txtUsername.getText())
                && isNotEmpty(_txtPassword.getText());
    }

    @Override
    protected @Nonnull
    Control[] getControls()
    {
        return new Control[] {_txtUrl, _txtContainerName, _txtUsername, _txtPassword, _btnContinue, _btnBack};
    }

    @Override
    protected void doWorkImpl() throws Exception
    {
        SwiftConnectionChecker connChecker = new SwiftConnectionChecker(_model);
        connChecker.checkConnection();
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_SWIFT_CONNECTION_ERROR;
    }
}
