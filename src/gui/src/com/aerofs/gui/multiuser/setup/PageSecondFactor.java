/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExRateLimitExceeded;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.SecondFactorPrompt;
import com.aerofs.lib.S;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.error.ErrorMessage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

public class PageSecondFactor extends AbstractSetupWorkPage
{
    private Logger l = Loggers.getLogger(PageSecondFactor.class);

    private Button _btnContinue;
    private Button _btnBack;
    private CompSpin _compSpin;
    private SecondFactorPrompt _prompt;

    public PageSecondFactor(Composite parent)
    {
        super(parent, SWT.NONE);
    }

    @Override
    protected Composite createContent(Composite parent)
    {
        if (_prompt == null) {
            _prompt = new SecondFactorPrompt(parent)
            {
                @Override
                protected void onTextChange()
                {
                    _btnContinue.setEnabled(textField().getText().length() == 6);
                }
            };
        }
        return _prompt.content();
    }

    private GridData createMessageLayoutData()
    {
        GridData layoutData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 3, 1);
        layoutData.heightHint = 30;
        return layoutData;
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
    protected void readFromModel(SetupModel model)
    {
        l.info("Reading from model");
        // Don't fill the form if the code is unset (or happens to be 0)
        if (model.getSecondFactorCode() != 0) {
            String s = String.format("%06d", model.getSecondFactorCode());
            _prompt.textField().setText(s);
        }
        _btnContinue.setEnabled(_prompt.textField().getText().length() == 6);
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        try {
            Integer authcodeValue = Integer.parseInt(_prompt.textField().getText(), 10);
            l.info("saving {} to model", authcodeValue);
            model.setSecondFactorCode(authcodeValue);

        } catch (NumberFormatException e) {
            // Someone broke our restricted input textbox.
            // This branch shouldn't be reached, but if it is, don't change the model
            return;
        }
    }

    @Override
    protected void populateButtonBar(Composite parent)
    {
        _btnBack = createButton(parent, S.BTN_BACK, false);
        _btnBack.addSelectionListener(createListenerToGoBack());

        _btnContinue = createButton(parent, S.BTN_CONTINUE, true);
        _btnContinue.addSelectionListener(createListenerToDoWork());

        _compSpin = new CompSpin(parent, SWT.NONE);
    }

    @Nonnull
    @Override
    protected Logger getLogger()
    {
        return l;
    }

    @Nonnull
    @Override
    protected Button getDefaultButton()
    {
        return _btnContinue;
    }

    @Nonnull
    @Override
    protected Control[] getControls()
    {
        return new Control[] {
                _btnContinue,
                _btnBack,
                _prompt.textField(),
        };
    }

    @Nonnull
    @Override
    protected CompSpin getSpinner()
    {
        return _compSpin;
    }

    @Override
    protected void doWorkImpl()
            throws Exception
    {
        _model.doSecondFactorSignIn();
    }

    @Override
    protected String getDefaultErrorMessage()
    {
        return "Failed to provide two-factor auth code";
    }

    @Override
    protected ErrorMessage[] getErrorMessages(Exception e)
    {
        return new ErrorMessage[] {
                new ErrorMessage(ExSecondFactorRequired.class, S.SETUP_ERR_SECOND_FACTOR),
                new ErrorMessage(ExRateLimitExceeded.class, S.SETUP_ERR_RATE_LIMIT),
                new ErrorMessage(ExTimeout.class, "Connection timed out."),
        };
    }
}
