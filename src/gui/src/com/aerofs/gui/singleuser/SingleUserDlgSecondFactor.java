/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.singleuser;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExInternalError;
import com.aerofs.base.ex.ExRateLimitExceeded;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.base.ex.ExSecondFactorSetupRequired;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.SecondFactorPrompt;
import com.aerofs.gui.SecondFactorPrompt.SecondFactorSetup;
import com.aerofs.lib.S;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

// This majority of this class probably ought to be refactored away, but I don't have the high-level
// vision nor the patience to make that happen right now when we have two separate setup flows
// and one uses a wizard-like setup and the other doesn't and all this other nonsense
public class SingleUserDlgSecondFactor extends AeroFSJFaceDialog
{
    private Logger l = Loggers.getLogger(SingleUserDlgSecondFactor.class);
    private SetupModel _model;
    private Button _btnContinue;
    private Button _btnCancel;
    private SecondFactorPrompt _prompt;
    private CompSpin _spinner;

    public SingleUserDlgSecondFactor(Shell parent, SetupModel model)
    {
        super("Two Factor Authentication", parent, true, false, false, false);
        _model = model;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite body = new Composite(parent, SWT.NONE);

        SecondFactorSetup needed = _model.getNeedSecondFactorSetup() ? SecondFactorSetup.NEEDED :
                SecondFactorSetup.OKAY;
        _prompt = new SecondFactorPrompt(parent, needed)
        {
            @Override
            protected void onTextChange()
            {
                _btnContinue.setEnabled(textField().getText().length() == 6);
            }
        };
        Composite content = _prompt.content();

        GridLayout gridLayout = new GridLayout();
        gridLayout.marginHeight = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        gridLayout.marginLeft = 20;
        gridLayout.marginWidth = 40;
        gridLayout.marginRight = 20;
        body.setLayout(gridLayout);

        content.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

        return body;
    }

    private void saveToModel()
    {
        try {
            Integer authcodeValue = Integer.parseInt(_prompt.textField().getText(), 10);
            _model.setSecondFactorCode(authcodeValue);
        } catch (NumberFormatException e) {
            // Someone broke our restricted input textbox.
            // This branch shouldn't be reached, but if it is, don't change the model
            return;
        }
    }

    @Override
    protected Control createButtonBar(Composite parent)
    {
        Composite buttonBar = new Composite(parent, SWT.NONE);
        populateButtonBar(buttonBar);
        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.center = true;
        buttonBar.setLayout(layout);

        return buttonBar;
    }

    private void populateButtonBar(Composite parent)
    {
        final SingleUserDlgSecondFactor dialog = this;
        _btnCancel = createButton(parent, S.BTN_QUIT, false);
        _btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                dialog.setReturnCode(IDialogConstants.CANCEL_ID);
                dialog.close();
            }
        });
        _btnContinue = createButton(parent, S.BTN_CONTINUE, true);
        _btnContinue.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setProgress(true);
                saveToModel();
                GUI.get().safeWork(e.widget, new ISWTWorker()
                {
                    @Override
                    public void run()
                            throws Exception
                    {
                        _model.doSecondFactorSignIn();
                    }

                    @Override
                    public void okay()
                    {
                        // Success!  We can dispose this modal and continue on to installing.
                        dialog.setReturnCode(IDialogConstants.OK_ID);
                        dialog.close();
                    }

                    @Override
                    public void error(Exception e)
                    {
                        setProgress(false);
                        // Handle the exception and display something useful to the user
                        l.info("Failed to sign in:", e);
                        ErrorMessages.show(getShell(), e, formatExceptionMessage(e));
                        _prompt.textField().setFocus();
                    }
                });
            }
        });

        _spinner = new CompSpin(parent, SWT.NONE);
    }

    private static String formatExceptionMessage(Exception e)
    {
        if (e instanceof ExSecondFactorRequired) return "Incorrect authentication code.";
        else if (e instanceof ExSecondFactorSetupRequired) return "Set up your second factor first.";
        else if (e instanceof ExRateLimitExceeded) return "Too many incorrect tries.";
        else if (e instanceof ExInternalError) return S.SERVER_INTERNAL_ERROR;
        else return S.SETUP_DEFAULT_SIGNIN_ERROR;
    }

    private void setProgress(boolean inProgress)
    {
        // spinner
        if (inProgress) {
            _spinner.start();
        } else {
            _spinner.stop();
        }

        // deny user interaction
        for (Control control : new Control[]{_btnCancel, _prompt.textField()}) {
            if (control != null) {
                control.setEnabled(!inProgress);
            }
        }
        _btnContinue.setEnabled(!inProgress && _prompt.textField().getText().length() == 6);
    }

    // an utility method to create a button for the button bar
    protected final Button createButton(Composite parent, String text, boolean isDefault)
    {
        Button button = GUIUtil.createButton(parent, SWT.NONE);

        button.setText(text);
        button.setLayoutData(new RowData(100, SWT.DEFAULT));

        if (isDefault) getShell().setDefaultButton(button);

        return button;
    }
}
