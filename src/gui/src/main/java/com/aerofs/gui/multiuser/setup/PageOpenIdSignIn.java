/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.multiuser.setup.DlgMultiuserSetup.PageID;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.S;
import com.aerofs.ui.error.ErrorMessage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

public class PageOpenIdSignIn extends AbstractSetupWorkPage
{
    private Button      _btnContinue;

    private CompSpin    _compSpin;

    public PageOpenIdSignIn(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        String label = "Sign in using the organization administrator's\n" +
                Identity.SERVICE_IDENTIFIER + " account.";
        _btnContinue = createButton(content, label, BUTTON_DEFAULT);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.center = true;
        layout.spacing = GUIParam.VERTICAL_SPACING;
        content.setLayout(layout);
        _btnContinue.setLayoutData(new RowData(360, 70));

        return content;
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        _compSpin = new CompSpin(parent, SWT.NONE);

        createButton(parent, S.BTN_QUIT, BUTTON_BACK);
    }

    @Override
    protected void goNextPage()
    {
        _dialog.loadPage(_model.getNeedSecondFactor() ? PageID.PAGE_TWO_FACTOR : PageID.PAGE_SELECT_STORAGE);
    }

    @Override
    protected void goPreviousPage()
    {
        _dialog.closeDialog();
    }

    @Override
    protected @Nonnull Logger getLogger()
    {
        return Loggers.getLogger(PageOpenIdSignIn.class);
    }

    @Override
    protected @Nonnull Button getDefaultButton()
    {
        return _btnContinue;
    }

    @Override
    protected @Nonnull Control[] getControls()
    {
        return new Control[] { _btnContinue,  };
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
    protected ErrorMessage[] getErrorMessages(Exception e)
    {
        return new ErrorMessage[] {
                new ErrorMessage(ExBadCredential.class, S.OPENID_AUTH_BAD_CRED + " " +
                        S.TRY_AGAIN_LATER),
                new ErrorMessage(ExTimeout.class, S.OPENID_AUTH_TIMEOUT + " " + S.TRY_AGAIN_LATER)
        };
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_DEFAULT_SIGNIN_ERROR;
    }
}
