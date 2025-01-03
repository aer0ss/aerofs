/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.PageID;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.error.ErrorMessage;
import com.google.common.base.Objects;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.updateFont;
import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;

public class PageCredentialSignIn extends AbstractSetupWorkPage
{
    private Text        _txtUserID;
    private Text        _txtPasswd;
    private Link        _lnkPasswd;
    private Text        _txtDeviceName;

    private CompSpin    _compSpin;
    private Button      _btnContinue;

    public PageCredentialSignIn(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblMessage = createLabel(composite, SWT.NONE);
        updateFont(lblMessage, 110, SWT.NONE);
        lblMessage.setText(S.SETUP_MESSAGE);

        Composite compInput = createInputComposite(composite);

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 0;
        layout.verticalSpacing = 20;
        composite.setLayout(layout);

        lblMessage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        compInput.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        return composite;
    }

    private Composite createInputComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Label lblUserID = createLabel(composite, SWT.NONE);
        lblUserID.setText(S.ADMIN_EMAIL + ": ");

        _txtUserID = new Text(composite, SWT.BORDER);
        _txtUserID.setFocus();

        Label lblPasswd = createLabel(composite, SWT.NONE);
        lblPasswd.setText(S.ADMIN_PASSWD + ": ");

        _txtPasswd = new Text(composite, SWT.BORDER | SWT.PASSWORD);

        createLabel(composite, SWT.NONE);

        _lnkPasswd = new Link(composite, SWT.NONE);
        _lnkPasswd.setText(S.SETUP_LINK_FORGOT_PASSWD);
        _lnkPasswd.addSelectionListener(createUrlLaunchListener(WWW.PASSWORD_RESET_REQUEST_URL));

        Label lblDeviceName = createLabel(composite, SWT.NONE);
        lblDeviceName.setText(S.SETUP_DEV_ALIAS + ": ");

        _txtDeviceName = new Text(composite, SWT.BORDER);

        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 20;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 0;
        layout.verticalSpacing = 10;
        composite.setLayout(layout);

        lblUserID.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUserID.setLayoutData(createTextBoxLayoutData());
        lblPasswd.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtPasswd.setLayoutData(createTextBoxLayoutData());
        _lnkPasswd.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1));
        lblDeviceName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtDeviceName.setLayoutData(createTextBoxLayoutData());

        ModifyListener onInputChanged = e -> validateInput();

        _txtUserID.addModifyListener(onInputChanged);
        _txtPasswd.addModifyListener(onInputChanged);
        _txtDeviceName.addModifyListener(onInputChanged);

        return composite;
    }

    private GridData createTextBoxLayoutData()
    {
        GridData layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        if (OSUtil.isOSX()) {
            layoutData.horizontalIndent = 2;
        }
        return layoutData;
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        _compSpin = new CompSpin(parent, SWT.NONE);
        createButton(parent, S.BTN_QUIT, BUTTON_BACK);
        _btnContinue = createButton(parent, S.BTN_CONTINUE, BUTTON_DEFAULT);
    }

    @Override
    protected void goNextPage()
    {
        _dialog.loadPage(_model.getNeedSecondFactor() ? PageID.PAGE_TWO_FACTOR : PageID.PAGE_SELECT_STORAGE);
    }

    @Override
    protected void goPreviousPage() {
        _dialog.closeDialog();
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
    protected @Nonnull Logger getLogger()
    {
        return Loggers.getLogger(PageCredentialSignIn.class);
    }

    @Override
    protected @Nonnull Button getDefaultButton()
    {
        return _btnContinue;
    }

    @Override
    protected @Nonnull Control[] getControls()
    {
        return new Control[] {
                _btnContinue, _txtUserID, _txtPasswd, _txtDeviceName, _lnkPasswd,
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
        _model.doSignIn();
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

    @Override
    protected ErrorMessage[] getErrorMessages(Exception e)
    {
        return new ErrorMessage[] {
                new ErrorMessage(ExBadCredential.class, S.BAD_CREDENTIAL_CAP + ". " +
                        S.TRY_AGAIN_LATER)
        };
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_DEFAULT_SIGNIN_ERROR;
    }
}
