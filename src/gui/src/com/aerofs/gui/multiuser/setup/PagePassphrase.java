/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.PageID;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.ui.StorageDataEncryptionPasswordVerifier;
import com.aerofs.ui.error.ErrorMessage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.createLabel;

public class PagePassphrase extends AbstractSetupWorkPage
{
    protected Text      _txtPass1;
    protected Text      _txtPass2;
    private StorageDataEncryptionPasswordVerifier _verifier;

    private Label       _lblStatus;
    private CompSpin    _compError;

    private CompSpin    _compSpin;
    protected Button    _btnInstall;
    protected Button    _btnBack;

    public PagePassphrase(Composite parent)
    {
        super(parent, SWT.NONE);

        _verifier = new StorageDataEncryptionPasswordVerifier();
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        Label lblPassDesc = createLabel(content, SWT.WRAP);
        lblPassDesc.setText(S.SETUP_STORAGE_PASSWD_DESC);

        Composite compPassphrase = createPassphraseComposite(content);

        ModifyListener onInputChanged = e -> validateInput();
        _txtPass1.addModifyListener(onInputChanged);
        _txtPass2.addModifyListener(onInputChanged);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.verticalSpacing = 10;
        content.setLayout(layout);

        lblPassDesc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compPassphrase.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return content;
    }

    protected Composite createPassphraseComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblPass1 = createLabel(composite, SWT.NONE);
        lblPass1.setText(S.SETUP_STORAGE_ENC_PASSWD_GUI);

        _txtPass1 = new Text(composite, SWT.BORDER | SWT.PASSWORD);
        _txtPass1.setFocus();

        Label lblPass2 = createLabel(composite, SWT.NONE);
        lblPass2.setText(S.SETUP_STORAGE_CONF_PASSWD);

        _txtPass2 = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        Composite compStatus = createStatusBar(composite);

        GridLayout layout = new GridLayout(2, true);
        layout.marginWidth = 20;
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
        _btnBack = createButton(parent, S.BTN_BACK, BUTTON_BACK);
        _btnInstall = createButton(parent, S.SETUP_BTN_INSTALL, BUTTON_DEFAULT);
    }

    @Override
    protected void goNextPage()
    {
        _dialog.closeDialog(_model);
    }

    @Override
    protected void goPreviousPage()
    {
        if (_model._backendConfig._storageType == StorageType.S3) {
            _dialog.loadPage(PageID.PAGE_S3_STORAGE);
        } else if (_model._backendConfig._storageType == StorageType.SWIFT) {
            _dialog.loadPage(PageID.PAGE_SWIFT_CONTAINER);
        }
    }

    @Override
    @Nonnull
    protected Button getDefaultButton()
    {
        return _btnInstall;
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        // Nothing to read: we won't read the passphrase
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model._backendConfig._passphrase = _txtPass1.getText();
    }

    protected boolean isInputValid()
    {
        char[] pass1 = _txtPass1.getTextChars();
        char[] pass2 = _txtPass2.getTextChars();

        StorageDataEncryptionPasswordVerifier.PasswordVerifierResult result = _verifier.verifyAndConfirmPasswords(pass1, pass2);

        if (result == StorageDataEncryptionPasswordVerifier.PasswordVerifierResult.OK) {
            updateStatus("", false);
            return true;
        } else if (result == StorageDataEncryptionPasswordVerifier.PasswordVerifierResult.TOO_SHORT
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
    protected void doWorkImpl() throws Exception
    {
        _model.doInstall();
    }

    @Override
    protected @Nonnull
    Logger getLogger()
    {
        return Loggers.getLogger(PagePassphrase.class);
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

    @Override
    protected @Nonnull CompSpin getSpinner()
    {
        return _compSpin;
    }

    @Override
    protected @Nonnull Control[] getControls()
    {
        return new Control[] {_txtPass1, _txtPass2, _btnBack, _btnInstall};
    }
}
