/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.S;
import com.aerofs.ui.S3DataEncryptionPasswordVerifier;
import com.aerofs.ui.S3DataEncryptionPasswordVerifier.PasswordVerifierResult;
import com.aerofs.ui.error.ErrorMessage;
import com.google.common.base.Objects;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;

public class PageS3Storage extends AbstractSetupWorkPage
{
    private S3DataEncryptionPasswordVerifier _verifier;

    private Text        _txtEndpoint;
    private Text        _txtBucketName;
    private Text        _txtAccessKey;
    private Text        _txtSecretKey;
    private Text        _txtPass1;
    private Text        _txtPass2;

    private Label       _lblStatus;
    private CompSpin    _compError;

    private CompSpin    _compSpin;
    private Button      _btnInstall;
    private Button      _btnBack;

    static final String BLOCK_STORAGE_HELP_URL =
            "https://support.aerofs.com/hc/en-us/articles/203618620";

    public PageS3Storage(Composite parent)
    {
        super(parent, SWT.NONE);

        _verifier = new S3DataEncryptionPasswordVerifier();
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Link lnkAmazon = new Link(content, SWT.WRAP);
        lnkAmazon.setText(S.SETUP_S3_CONFIG_DESC);
        lnkAmazon.addSelectionListener(createUrlLaunchListener(BLOCK_STORAGE_HELP_URL));

        Composite compConfig = createConfigurationComposite(content);

        Label lblPassDesc = createLabel(content, SWT.WRAP);
        lblPassDesc.setText(S.SETUP_STORAGE_PASSWD_DESC);

        Composite compPassphrase = createPassphraseComposite(content);

        ModifyListener onInputChanged = createListenerToValidateInput();

        _txtBucketName.addModifyListener(onInputChanged);
        _txtAccessKey.addModifyListener(onInputChanged);
        _txtSecretKey.addModifyListener(onInputChanged);
        _txtPass1.addModifyListener(onInputChanged);
        _txtPass2.addModifyListener(onInputChanged);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 0;
        content.setLayout(layout);

        lnkAmazon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compConfig.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lblPassDesc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compPassphrase.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return content;
    }

    protected Composite createConfigurationComposite(Composite parent)
    {
        Composite compConfig = new Composite(parent, SWT.NONE);

        // Endpoint
        Label lblEndpoint = createLabel(compConfig, SWT.NONE);
        lblEndpoint.setText(S.SETUP_S3_ENDPOINT_GUI);
        _txtEndpoint = new Text(compConfig, SWT.BORDER);

        // Bucket name
        Label lblBucketName = createLabel(compConfig, SWT.NONE);
        lblBucketName.setText(S.SETUP_S3_BUCKET_NAME_GUI);
        _txtBucketName = new Text(compConfig, SWT.BORDER);
        _txtBucketName.setFocus();

        // Access key
        Label lblAccessKey = createLabel(compConfig, SWT.NONE);
        lblAccessKey.setText(S.SETUP_S3_ACCESS_KEY_GUI);
        _txtAccessKey = new Text(compConfig, SWT.BORDER);

        // Secret key
        Label lblSecretKey = createLabel(compConfig, SWT.NONE);
        lblSecretKey.setText(S.SETUP_S3_SECRET_KEY_GUI);
        _txtSecretKey = new Text(compConfig, SWT.BORDER);

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 60;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        compConfig.setLayout(layout);

        lblEndpoint.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtEndpoint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblBucketName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtBucketName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblAccessKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtAccessKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblSecretKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtSecretKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return compConfig;
    }

    protected Composite createPassphraseComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblPass1 = createLabel(composite, SWT.NONE);
        lblPass1.setText(S.SETUP_S3_ENC_PASSWD_GUI);

        _txtPass1 = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        Label lblPass2 = createLabel(composite, SWT.NONE);
        lblPass2.setText(S.SETUP_S3_CONF_PASSWD);

        _txtPass2 = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        Composite compStatus = createStatusBar(composite);

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 60;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        composite.setLayout(layout);

        lblPass1.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtPass1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblPass2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtPass2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        compStatus.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false, 2, 1));

        return composite;
    }

    protected Composite createStatusBar(Composite parent)
    {
        Composite statusBar = new Composite(parent, SWT.NONE);

        _lblStatus = createLabel(statusBar, SWT.NONE);
        _compError = new CompSpin(statusBar, SWT.NONE);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.center = true;
        statusBar.setLayout(layout);

        return statusBar;
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        _compSpin = new CompSpin(parent, SWT.NONE);

        _btnBack = createButton(parent, S.BTN_BACK, false);
        _btnBack.addSelectionListener(createListenerToGoBack());

        _btnInstall = createButton(parent, S.SETUP_BTN_INSTALL, true);
        _btnInstall.addSelectionListener(createListenerToDoWork());
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtEndpoint.setText(Objects.firstNonNull(model._backendConfig._s3Config._endpoint,
                LibParam.DEFAULT_S3_ENDPOINT));
        _txtBucketName.setText(Objects.firstNonNull(model._backendConfig._s3Config._bucketID, ""));
        _txtAccessKey.setText(Objects.firstNonNull(model._backendConfig._s3Config._accessKey, ""));
        _txtSecretKey.setText(Objects.firstNonNull(model._backendConfig._s3Config._secretKey, ""));
        // do not load the pass-phrase

        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._backendConfig._s3Config._endpoint = _txtEndpoint.getText().trim();
        model._backendConfig._s3Config._bucketID = _txtBucketName.getText().trim();
        model._backendConfig._s3Config._accessKey = _txtAccessKey.getText();
        model._backendConfig._s3Config._secretKey = _txtSecretKey.getText();
        model._backendConfig._passphrase = _txtPass1.getText();
    }

    @Override
    protected @Nonnull Logger getLogger()
    {
        return Loggers.getLogger(PageS3Storage.class);
    }

    @Override
    protected @Nonnull Button getDefaultButton()
    {
        return _btnInstall;
    }

    @Override
    protected @Nonnull Control[] getControls()
    {
        return new Control[] {_txtEndpoint, _txtBucketName, _txtAccessKey, _txtSecretKey, _txtPass1,
                _txtPass2, _btnBack, _btnInstall
        };
    }

    @Override
    protected @Nonnull CompSpin getSpinner()
    {
        return _compSpin;
    }

    @Override
    protected void doWorkImpl() throws Exception
    {
        _model.doInstall();
    }

    @Override
    protected boolean isInputValid()
    {
        return isPassphraseValid()
                && !_txtEndpoint.getText().trim().isEmpty()
                && !_txtBucketName.getText().trim().isEmpty()
                && !_txtAccessKey.getText().isEmpty()
                && !_txtSecretKey.getText().isEmpty();
    }

    @SuppressWarnings("fallthrough")
    protected boolean isPassphraseValid()
    {
        char[] pass1 = _txtPass1.getTextChars();
        char[] pass2 = _txtPass2.getTextChars();

        PasswordVerifierResult result = _verifier.verifyAndConfirmPasswords(pass1, pass2);

        if (result == PasswordVerifierResult.OK) {
            updateStatus("", false);
            return true;
        } else if (result == PasswordVerifierResult.TOO_SHORT
                && pass1.length == 0 && pass2.length == 0) {
            updateStatus("", false);
            return false;
        } else {
            updateStatus(result.getMsg(), true);
            return false;
        }
    }

    protected void updateStatus(String status, boolean indicateError)
    {
        _lblStatus.setText(status);

        layout(new Control[]{_lblStatus});

        if (indicateError) _compError.error();
        else _compError.stop();
    }

    @Override
    protected ErrorMessage[] getErrorMessages(Exception e)
    {
        return new ErrorMessage[] { new ErrorMessage(ExNoPerm.class, S.SETUP_NOT_ADMIN) };
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_DEFAULT_INSTALL_ERROR;
    }
}
