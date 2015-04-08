/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.gui.CompSpin;
import com.aerofs.lib.S;
import com.aerofs.ui.error.ErrorMessage;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.javaswift.joss.exception.UnauthorizedException;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

/**
 * Abstract page template for S3 and Swift pages
 */
abstract public class PageStorageBackend extends AbstractSetupWorkPage
{
    protected Button      _btnContinue;
    protected Button      _btnBack;

    private CompSpin    _compSpin;

    static final String BLOCK_STORAGE_HELP_URL =
            "https://support.aerofs.com/hc/en-us/articles/203618620";

    public PageStorageBackend(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    abstract protected Composite createConfigurationComposite(Composite parent);

    @Override
    protected void populateButtonBar(Composite parent)
    {
        _compSpin = new CompSpin(parent, SWT.NONE);

        _btnBack = createButton(parent, S.BTN_BACK, false);
        _btnBack.addSelectionListener(createListenerToGoBack());

        _btnContinue = createButton(parent, S.BTN_CONTINUE, true);
        _btnContinue.addSelectionListener(createListenerToDoWork());
    }

    @Override
    protected @Nonnull Button getDefaultButton()
    {
        return _btnContinue;
    }

    @Override
    protected @Nonnull
    Logger getLogger()
    {
        return Loggers.getLogger(PageStorageBackend.class);
    }

    @Override
    protected ErrorMessage[] getErrorMessages(Exception e)
    {
        return new ErrorMessage[] {
                new ErrorMessage(ExNoPerm.class, S.SETUP_NOT_ADMIN),
                new ErrorMessage(UnauthorizedException.class, S.SETUP_SWIFT_CONNECTION_ERROR),
                new ErrorMessage(AmazonS3Exception.class, S.SETUP_S3_CONNECTION_ERROR),
        };
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return S.SETUP_DEFAULT_INSTALL_ERROR;
    }

    @Override
    protected @Nonnull
    CompSpin getSpinner()
    {
        return _compSpin;
    }
}
