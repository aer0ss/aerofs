/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.PageID;
import com.aerofs.lib.S;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;
import static com.google.common.base.Objects.firstNonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * This page will ask for:
 *  - Auth mode
 *  - URL
 *  - username
 *  - password
 */
public class PageSwiftStorage extends PageStorageBackend
{
    private Combo       _comboAuthMode;
    private Text        _txtUrl;
    private Text        _txtUsername;
    private Text        _txtPassword;

    // Use BASIC as Keystone is still experimental
    static private final String DEFAULT_AUTHMODE = "basic";

    /**
     * This contains the available authentication modes: basic and keystone.
     * We use a map because we need a human-friendly value and the exact value for the database
     */
    Map<String, String> authModes = new HashMap<String, String>();

    public PageSwiftStorage(Composite parent)
    {
        super(parent);

        authModes.put("basic", "Basic");
        authModes.put("keystone", "Keystone (BETA)");
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Link lnkSwift = new Link(content, SWT.WRAP);
        lnkSwift.setText(S.SETUP_SWIFT_CONFIG_DESC);
        lnkSwift.addSelectionListener(createUrlLaunchListener(BLOCK_STORAGE_HELP_URL));

        Composite compConfig = createConfigurationComposite(content);

        ModifyListener onInputChanged = e -> validateInput();

        _comboAuthMode.addModifyListener(onInputChanged);
        _txtUrl.addModifyListener(onInputChanged);
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

    @Override
    protected void goNextPage()
    {
        if ("basic".equals(_model._backendConfig._swiftConfig._authMode)) {
            _dialog.loadPage(PageID.PAGE_SWIFT_CONTAINER);
        }
        else {
            _dialog.loadPage(PageID.PAGE_SWIFT_TENANT);
        }
    }

    @Override
    protected void goPreviousPage() {
        _dialog.loadPage(PageID.PAGE_SELECT_STORAGE);
    }

    protected Composite createConfigurationComposite(Composite parent)
    {
        Composite compConfig = new Composite(parent, SWT.NONE);

        Label lblAuthMode = createLabel(compConfig, SWT.NONE);
        lblAuthMode.setText(S.SETUP_SWIFT_AUTH_MODE_GUI);
        _comboAuthMode = new Combo(compConfig, SWT.DROP_DOWN | SWT.READ_ONLY);
        _comboAuthMode.setItems(authModes.values().toArray(new String[0]));

        Label lblUrl = createLabel(compConfig, SWT.NONE);
        lblUrl.setText(S.SETUP_SWIFT_URL_GUI);
        _txtUrl = new Text(compConfig, SWT.BORDER);
        _txtUrl.setFocus();

        Label lblUsername = createLabel(compConfig, SWT.NONE);
        lblUsername.setText(S.SETUP_SWIFT_USERNAME_GUI);
        _txtUsername = new Text(compConfig, SWT.BORDER);

        Label lblPassword = createLabel(compConfig, SWT.NONE);
        lblPassword.setText(S.SETUP_SWIFT_PASSWORD_GUI);
        _txtPassword = new Text(compConfig, SWT.BORDER | SWT.PASSWORD);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 20;
        layout.marginHeight = 10;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 5;
        compConfig.setLayout(layout);

        lblAuthMode.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        _comboAuthMode.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lblUrl.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUrl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblUsername.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUsername.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtPassword.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return compConfig;
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        // Default to Keystone mode
        _comboAuthMode.setText(authModes.get(firstNonNull(model._backendConfig._swiftConfig._authMode, DEFAULT_AUTHMODE)));
        _txtUrl.setText(firstNonNull(model._backendConfig._swiftConfig._url, ""));
        _txtUsername.setText(firstNonNull(model._backendConfig._swiftConfig._username, ""));
        _txtPassword.setText(firstNonNull(model._backendConfig._swiftConfig._password, ""));

        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._backendConfig._swiftConfig._authMode     = (_comboAuthMode.getText().equals("Basic")) ? "basic" : "keystone";
        model._backendConfig._swiftConfig._url          = _txtUrl.getText().trim();
        model._backendConfig._swiftConfig._username     = _txtUsername.getText();
        model._backendConfig._swiftConfig._password     = _txtPassword.getText();
    }

    @Override
    protected boolean isInputValid()
    {
        return isNotBlank(_txtUrl.getText())
                && isNotEmpty(_comboAuthMode.getText())
                && isNotEmpty(_txtUsername.getText())
                && isNotEmpty(_txtPassword.getText());
    }

    @Override
    protected @Nonnull
    Control[] getControls()
    {
        return new Control[] {_comboAuthMode, _txtUrl, _txtUsername, _txtPassword, _btnContinue, _btnBack};
    }

    @Override
    protected void doWorkImpl() throws Exception
    {
        SwiftConnectionChecker connChecker = new SwiftConnectionChecker(_model);
        connChecker.checkConnection();

        // Save the list of the containers for later use
        _model._backendConfig._swiftConfig._containerList = connChecker.listContainers();
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_SWIFT_CONNECTION_ERROR;
    }
}
