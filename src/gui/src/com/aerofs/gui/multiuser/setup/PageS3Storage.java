/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.S3DataEncryptionPasswordVerifier;
import com.aerofs.ui.S3DataEncryptionPasswordVerifier.PasswordVerifierResult;
import com.google.common.base.Objects;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import java.net.ConnectException;

public class PageS3Storage extends AbstractSetupPage
{
    private Logger l = Loggers.getLogger(PageS3Storage.class);

    private boolean _inProgress;
    private S3DataEncryptionPasswordVerifier _verifier;

    private Composite   _compHeader;
    private Composite   _compContent;
    private Composite   _compConfig;
    private Composite   _compPassphrase;
    private Composite   _compStatus;
    private Composite   _compButton;

    // TODO: convert fields to locals
    private Label       _lblTitle;
    private Label       _lblLogo;
    private Label       _lblConfigDesc;
    private Link        _lnkAmazon;
    private Label       _lblBucketID;
    private Text        _txtBucketID;
    private Label       _lblAccessKey;
    private Text        _txtAccessKey;
    private Label       _lblSecretKey;
    private Text        _txtSecretKey;
    private Label       _lblPassDesc;
    private Label       _lblPass1;
    private Text        _txtPass1;
    private Label       _lblPass2;
    private Text        _txtPass2;
    private Label       _lblStatus;
    private CompSpin    _compError;
    private CompSpin    _compSpin;
    private Button      _btnInstall;
    private Button      _btnBack;

    static final String AMAZON_S3_URL = "http://aws.amazon.com/s3";

    public PageS3Storage(Composite parent)
    {
        super(parent, SWT.NONE);

        _verifier = new S3DataEncryptionPasswordVerifier();

        createPage();

        getShell().addListener(SWT.Close, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                if (_inProgress) event.doit = false;
            }
        });

        _lnkAmazon.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                GUIUtil.launch(AMAZON_S3_URL);
            }
        });

        ModifyListener onInputChanged = new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                validateInput();
            }
        };

        _txtBucketID.addModifyListener(onInputChanged);
        _txtAccessKey.addModifyListener(onInputChanged);
        _txtSecretKey.addModifyListener(onInputChanged);
        _txtPass1.addModifyListener(onInputChanged);
        _txtPass2.addModifyListener(onInputChanged);

        _btnInstall.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                doWork();
            }
        });

        _btnBack.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                traverse(SWT.TRAVERSE_PAGE_PREVIOUS);
            }
        });

        validateInput();
    }

    protected void createPage()
    {
        createHeader(this);
        createContent(this);
        createStatusBar(this);
        createButtonBar(this);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);

        _compHeader.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _compContent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridData statusLayout = new GridData(SWT.RIGHT, SWT.BOTTOM, true, false);
        statusLayout.exclude = true;
        _compStatus.setLayoutData(statusLayout);
        _compButton.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
    }

    protected void createHeader(Composite parent)
    {
        _compHeader = new Composite(parent, SWT.NONE);
        _compHeader.setBackgroundMode(SWT.INHERIT_FORCE);
        _compHeader.setBackground(SWTResourceManager.getColor(0xFF, 0xFF, 0xFF));

        _lblTitle = new Label(_compHeader, SWT.NONE);
        _lblTitle.setText(S.SETUP_TITLE);
        GUIUtil.changeFont(_lblTitle, 16, SWT.BOLD);

        // n.b. when rendering images on a label, setImage clears the alignment bits,
        //   hence we have to call setAlignment AFTER setImage to display image on the right
        _lblLogo = new Label(_compHeader, SWT.NONE);
        _lblLogo.setImage(Images.get(Images.IMG_SETUP));

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        _compHeader.setLayout(layout);

        GridData titleLayout = new GridData(SWT.LEFT, SWT.TOP, false, true);
        titleLayout.verticalIndent = 20;
        titleLayout.horizontalIndent = 20;
        _lblTitle.setLayoutData(titleLayout);
        _lblLogo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true));
    }

    protected void createContent(Composite parent)
    {
        _compContent = new Composite(parent, SWT.NONE);

        _lblConfigDesc = new Label(_compContent, SWT.WRAP);
        _lblConfigDesc.setText(S.SETUP_S3_CONFIG_DESC);

        _lnkAmazon = new Link(_compContent, SWT.NONE);
        _lnkAmazon.setText(S.SETUP_S3_AMAZON_LINK);

        createConfigurationComposite(_compContent);

        _lblPassDesc = new Label(_compContent, SWT.WRAP);
        _lblPassDesc.setText(S.SETUP_S3_PASSWD_DESC);

        createPassphraseComposite(_compContent);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 40;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 0;
        _compContent.setLayout(layout);

        _lblConfigDesc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _lnkAmazon.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _compConfig.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _lblPassDesc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _compPassphrase.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    }

    protected void createConfigurationComposite(Composite parent)
    {
        _compConfig = new Composite(parent, SWT.NONE);

        _lblBucketID = new Label(_compConfig, SWT.NONE);
        _lblBucketID.setText(S.SETUP_S3_BUCKET_NAME_GUI);

        _txtBucketID = new Text(_compConfig, SWT.BORDER);
        _txtBucketID.setFocus();

        _lblAccessKey = new Label(_compConfig, SWT.NONE);
        _lblAccessKey.setText(S.SETUP_S3_ACCESS_KEY_GUI);

        _txtAccessKey = new Text(_compConfig, SWT.BORDER);

        _lblSecretKey = new Label(_compConfig, SWT.NONE);
        _lblSecretKey.setText(S.SETUP_S3_SECRET_KEY_GUI);

        _txtSecretKey = new Text(_compConfig, SWT.BORDER);

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 60;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        _compConfig.setLayout(layout);

        _lblBucketID.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtBucketID.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _lblAccessKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtAccessKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _lblSecretKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtSecretKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    protected void createPassphraseComposite(Composite parent)
    {
        _compPassphrase = new Composite(parent, SWT.NONE);

        _lblPass1 = new Label(_compPassphrase, SWT.NONE);
        _lblPass1.setText(S.SETUP_S3_ENC_PASSWD_GUI);

        _txtPass1 = new Text(_compPassphrase, SWT.BORDER | SWT.PASSWORD);

        _lblPass2 = new Label(_compPassphrase, SWT.NONE);
        _lblPass2.setText(S.SETUP_S3_CONF_PASSWD);

        _txtPass2 = new Text(_compPassphrase, SWT.BORDER | SWT.PASSWORD);

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 60;
        layout.marginHeight = 10;
        layout.verticalSpacing = 5;
        _compPassphrase.setLayout(layout);

        _lblPass1.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtPass1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _lblPass2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        _txtPass2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    protected void createStatusBar(Composite parent)
    {
        _compStatus = new Composite(parent, SWT.NONE);
        _compStatus.setVisible(false);

        _lblStatus = new Label(_compStatus, SWT.NONE);

        _compError = new CompSpin(_compStatus, SWT.NONE);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginLeft = 0;
        layout.marginRight = 40;
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.center = true;
        _compStatus.setLayout(layout);
    }

    protected void createButtonBar(Composite parent)
    {
        _compButton = new Composite(parent, SWT.NONE);

        _compSpin = new CompSpin(_compButton, SWT.NONE);

        _btnBack = GUIUtil.createButton(_compButton, SWT.NONE);
        _btnBack.setText(S.BTN_BACK);

        _btnInstall = GUIUtil.createButton(_compButton, SWT.NONE);
        _btnInstall.setText(S.SETUP_BTN_INSTALL);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginLeft = 0;
        layout.marginRight = 40;
        layout.marginTop = 0;
        layout.marginBottom = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.center = true;
        _compButton.setLayout(layout);

        _btnBack.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnInstall.setLayoutData(new RowData(100, SWT.DEFAULT));
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        _txtBucketID.setText(Objects.firstNonNull(model._s3Options._bucketID, ""));
        _txtAccessKey.setText(Objects.firstNonNull(model._s3Options._accessKey, ""));
        _txtSecretKey.setText(Objects.firstNonNull(model._s3Options._secretKey, ""));
        // do not load the passphrase
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._s3Options._bucketID = _txtBucketID.getText().trim();
        model._s3Options._accessKey = _txtAccessKey.getText();
        model._s3Options._secretKey = _txtSecretKey.getText();
        model._s3Options._passphrase = _txtPass1.getText();
    }

    protected void validateInput()
    {
        boolean valid = isInputValid();
        _btnInstall.setEnabled(valid && !_inProgress);
        if (_btnInstall.getEnabled()) getShell().setDefaultButton(_btnInstall);
    }

    protected boolean isInputValid()
    {
        return isPassphraseValid()
                && !_txtBucketID.getText().isEmpty()
                && !_txtAccessKey.getText().isEmpty()
                && !_txtSecretKey.getText().isEmpty();
    }

    @SuppressWarnings("fallthrough")
    protected boolean isPassphraseValid()
    {
        char[] pass1 = _txtPass1.getTextChars();
        char[] pass2 = _txtPass2.getTextChars();

        PasswordVerifierResult result = _verifier.verifyAndConfirmPasswords(pass1, pass2);

        switch (result) {
        case OK:
            updateStatus("", false, false);
            return true;
        case TOO_SHORT:
            if (pass1.length == 0 && pass2.length == 0) {
                updateStatus("", false, false);
                return false;
            }
        default:
            updateStatus(result.getMsg(), false, true);
            return false;
        }
    }

    protected void doWork()
    {
        setProgress(true);

        writeToModel(_model);

        GUI.get().safeWork(_btnInstall, new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                _model.doInstall();
            }

            @Override
            public void okay()
            {
                setProgress(false);
                traverse(SWT.TRAVERSE_PAGE_NEXT);
            }

            @Override
            public void error(Exception e)
            {
                l.error("Setup error", e);
                ErrorMessages.show(getShell(), e, formatException(e));
                setProgress(false);
            }

            private String formatException(Exception e)
            {
                if (e instanceof ConnectException) return S.SETUP_ERR_CONN;
                else if (e instanceof ExUIMessage) return e.getMessage();
                else if (e instanceof ExBadCredential) return S.BAD_CREDENTIAL_CAP + '.';
                else return "Sorry, " + ErrorMessages.e2msgNoBracketDeprecated(e) + '.';
            }
        });
    }

    protected void setProgress(boolean inProgress)
    {
        _inProgress = inProgress;

        if (_inProgress) {
            updateStatus("", true, false);
            setControlState(false);
        } else {
            updateStatus("", false, false);
            setControlState(true);
        }
    }

    protected void updateStatus(String status, boolean indicateProgress, boolean indicateError)
    {
        boolean visible = status.length() > 0 || indicateProgress || indicateError;
        ((GridData) _compStatus.getLayoutData()).exclude = !visible;
        _compStatus.setVisible(visible);
        _lblStatus.setText(status);
        layout(new Control[] { _lblStatus });

        if (indicateProgress) _compSpin.start();
        else _compSpin.stop();

        if (indicateError) _compError.error();
        else _compError.stop();
    }

    protected void setControlState(boolean enabled)
    {
        _txtBucketID.setEnabled(enabled);
        _txtAccessKey.setEnabled(enabled);
        _txtSecretKey.setEnabled(enabled);
        _txtPass1.setEnabled(enabled);
        _txtPass2.setEnabled(enabled);
        _btnBack.setEnabled(enabled);
        _btnInstall.setEnabled(enabled);
    }
}
