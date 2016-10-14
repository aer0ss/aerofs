/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.controller.SetupModel;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.PageID;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.S;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
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

public class PageS3Storage extends PageStorageBackend
{
    private Text        _txtEndpoint;
    private Text        _txtBucketName;
    private Text        _txtAccessKey;
    private Text        _txtSecretKey;

    public PageS3Storage(Composite parent)
    {
        super(parent);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Link lnkAmazon = new Link(content, SWT.WRAP);
        lnkAmazon.setText(S.SETUP_S3_CONFIG_DESC);
        lnkAmazon.addSelectionListener(createUrlLaunchListener(BLOCK_STORAGE_HELP_URL));

        Composite compConfig = createConfigurationComposite(content);

        ModifyListener onInputChanged = e -> validateInput();

        _txtEndpoint.addModifyListener(onInputChanged);
        _txtBucketName.addModifyListener(onInputChanged);
        _txtAccessKey.addModifyListener(onInputChanged);
        _txtSecretKey.addModifyListener(onInputChanged);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 0;
        content.setLayout(layout);

        lnkAmazon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compConfig.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return content;
    }

    @Override
    protected void goNextPage()
    {
        _dialog.loadPage(PageID.PAGE_PASSPHRASE);
    }

    @Override
    protected void goPreviousPage()
    {
        _dialog.loadPage(PageID.PAGE_SELECT_STORAGE);
    }

    protected Composite createConfigurationComposite(Composite parent)
    {
        Composite compConfig = new Composite(parent, SWT.NONE);

        Label lblEndpoint = createLabel(compConfig, SWT.NONE);
        lblEndpoint.setText(S.SETUP_S3_ENDPOINT_GUI);
        _txtEndpoint = new Text(compConfig, SWT.BORDER);

        Label lblBucketName = createLabel(compConfig, SWT.NONE);
        lblBucketName.setText(S.SETUP_S3_BUCKET_NAME_GUI);
        _txtBucketName = new Text(compConfig, SWT.BORDER);
        _txtBucketName.setFocus();

        Label lblAccessKey = createLabel(compConfig, SWT.NONE);
        lblAccessKey.setText(S.SETUP_S3_ACCESS_KEY_GUI);
        _txtAccessKey = new Text(compConfig, SWT.BORDER);

        Label lblSecretKey = createLabel(compConfig, SWT.NONE);
        lblSecretKey.setText(S.SETUP_S3_SECRET_KEY_GUI);
        _txtSecretKey = new Text(compConfig, SWT.BORDER);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 20;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        compConfig.setLayout(layout);

        lblEndpoint.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtEndpoint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblBucketName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtBucketName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblAccessKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtAccessKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        lblSecretKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtSecretKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return compConfig;
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtEndpoint.setText(firstNonNull(model._backendConfig._s3Config._endpoint,
                ClientParam.DEFAULT_S3_ENDPOINT));
        _txtBucketName.setText(firstNonNull(model._backendConfig._s3Config._bucketID, ""));
        _txtAccessKey.setText(firstNonNull(model._backendConfig._s3Config._accessKey, ""));
        _txtSecretKey.setText(firstNonNull(model._backendConfig._s3Config._secretKey, ""));

        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._backendConfig._s3Config._endpoint    = _txtEndpoint.getText().trim();
        model._backendConfig._s3Config._bucketID    = _txtBucketName.getText().trim();
        model._backendConfig._s3Config._accessKey   = _txtAccessKey.getText();
        model._backendConfig._s3Config._secretKey   = _txtSecretKey.getText();
    }

    @Override
    protected boolean isInputValid()
    {
        return isNotBlank(_txtEndpoint.getText())
                && isNotBlank(_txtBucketName.getText())
                && isNotEmpty(_txtAccessKey.getText())
                && isNotEmpty(_txtSecretKey.getText());
    }

    @Override
    protected @Nonnull
    Control[] getControls()
    {
        return new Control[] {_txtEndpoint, _txtBucketName, _txtAccessKey, _txtSecretKey, _btnContinue, _btnBack};
    }

    /**
     * Checks the connection to the bucket:
     *  - endpoint up
     *  - access key & secret key
     *  - read access to the bucket
     *
     *  This doesn't check for write access as this is managed by the
     *  magic chunk logic (which is done after the passphrase has been
     *  entered).
     *
     * @throws Exception -> more specifically, AmazonS3Exception with different error codes
     *   - NoSuchBucket
     *   - InvalidAccessKey
     *   - InvalidSecretKey
     *   ...
     */
    @Override
    protected void doWorkImpl() throws Exception
    {
        BasicAWSCredentials credentials = new BasicAWSCredentials(
                _model._backendConfig._s3Config._accessKey,
                _model._backendConfig._s3Config._secretKey);

        AmazonS3 s3 = new AmazonS3Client(credentials);

        // Use path-style (http://host.com/bucket) rather than virtual-host-style
        // (http://bucket.host.com) for privately deployed S3 compatible systems
        s3.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));

        // Set the endpoint
        s3.setEndpoint(_model._backendConfig._s3Config._endpoint);

        // Try to read the bucket
        s3.listObjects(_model._backendConfig._s3Config._bucketID);
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_S3_CONNECTION_ERROR;
    }
}
