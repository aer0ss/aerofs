/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.lib.os.OSUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import static com.aerofs.gui.GUIUtil.createLabel;

public abstract class SecondFactorPrompt
{
    private Text _txtAuthCode;
    private Composite _content;

    // An enum to show whether we need to tell the user to take care of setting up the
    public static enum SecondFactorSetup {
        NEEDED,
        OKAY,
    }

    public SecondFactorPrompt(Composite parent, SecondFactorSetup setupNeeded)
    {
        _content = new Composite(parent, SWT.NONE);

        Label lblMessage = null;
        switch (setupNeeded) {
        case NEEDED:
            Label lblAdmonition = createLabel(_content, SWT.NONE);
            lblAdmonition.setText("Your administrator requires that you\n" +
                                  "set up two-factor authentication.");
            lblAdmonition.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 3, 1));
            Button btnSetupTwoFactor = GUIUtil.createButton(_content, SWT.NONE);
            btnSetupTwoFactor.setText("Set Up Two-Factor Authentication");
            btnSetupTwoFactor.addSelectionListener(
                    GUIUtil.createUrlLaunchListener(WWW.TWO_FACTOR_SETUP_URL));
            btnSetupTwoFactor.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 3, 1));
            lblMessage = createLabel(_content, SWT.NONE);
            lblMessage.setText("Then, enter your 6-digit authentication code below.");
            break;
        case OKAY:
            lblMessage = createLabel(_content, SWT.NONE);
            lblMessage.setText("Two Factor Authentication required.\n" +
                    "Enter 6-digit authentication code below.");
            break;
        }

        Label lblCode = createLabel(_content, SWT.NONE);
        lblCode.setText("Authentication code:");

        _txtAuthCode = new Text(_content, SWT.BORDER);
        _txtAuthCode.addVerifyListener(new VerifyListener()
        {
            @Override
            public void verifyText(VerifyEvent e)
            {
                switch (e.keyCode) {
                case SWT.BS:           // Backspace
                case SWT.DEL:          // Delete
                case SWT.HOME:         // Home
                case SWT.END:          // End
                case SWT.ARROW_LEFT:   // Left arrow
                case SWT.ARROW_RIGHT:  // Right arrow
                    return;
                }
                // Restrict to digits
                if (!Character.isDigit(e.character)) {
                    e.doit = false;
                }
                // Restrict to 6 characters max
                if (_txtAuthCode.getText().length() >= 6) {
                    e.doit = false;
                }
            }
        });
        _txtAuthCode.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                onTextChange();
            }
        });
        _txtAuthCode.setFocus();

        // Set layout
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        layout.horizontalSpacing = 0;
        layout.verticalSpacing = 10;
        _content.setLayout(layout);

        GridData lblMessageLayoutData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 3, 1);
        lblMessageLayoutData.heightHint = 30;
        lblMessage.setLayoutData(lblMessageLayoutData);

        lblCode.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        GridData authCodeLayoutData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        if (OSUtil.isOSX()) {
            authCodeLayoutData.horizontalIndent = 2;
        }
        _txtAuthCode.setLayoutData(authCodeLayoutData);
    }

    public Composite content()
    {
        return _content;
    }

    public Text textField()
    {
        return _txtAuthCode;
    }

    protected abstract void onTextChange();
}
