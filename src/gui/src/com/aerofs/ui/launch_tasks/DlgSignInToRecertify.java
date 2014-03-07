/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.CredentialActor;
import com.aerofs.controller.SignInActor.OpenIdGUIActor;
import com.aerofs.gui.AeroFSJFaceDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.S;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Show this dialog when we need to recertify the device and we can't use a current certificate
 * to do so.
 *
 * All relevant state is passed back to the caller in a SetupModel instance.
 *
 * If the isCancelled() state is false, the SetupModel is sufficiently populated to call doSignIn().
 */
public class DlgSignInToRecertify extends AeroFSJFaceDialog
{
    private SetupModel _setupModel;
    private Text _txtEmail;
    private Text _txtPasswd;
    private boolean _cancelled = true;

    public DlgSignInToRecertify(Shell parentShell, SetupModel setup)
    {
        super("Authorize this device", parentShell, false, false, true, true);
        _setupModel = setup;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginTop = GUIParam.MARGIN;
        gridLayout.marginHeight = 0;
        gridLayout.marginBottom = 0;
        gridLayout.verticalSpacing = GUIParam.MAJOR_SPACING;
        gridLayout.numColumns = 2;
        container.setLayout(gridLayout);

        // row 1

        Label label = new Label(container, SWT.WRAP | SWT.BORDER);
        GridData gd = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        gd.widthHint = 300;
        label.setLayoutData(gd);
        label.setText(S.SIGN_IN_TO_RECERTIFY_EXPLANATION);

        // row 1a : call to action

        Label labelAction = new Label(container, SWT.WRAP);
        GridData gdAction = new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1);
        gdAction.widthHint = 300;
        labelAction.setLayoutData(gdAction);
        labelAction.setText(S.SIGN_IN_TO_RECERTIFY_ACTION);

        if (LibParam.OpenId.enabled()) {

            // optional row 1 : OpenID button
            Button signInButton = GUIUtil.createButton(container, SWT.PUSH);
            signInButton.setText("Sign in with " + Identity.SERVICE_IDENTIFIER);
            signInButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
            signInButton.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    _setupModel.setSignInActor(new OpenIdGUIActor());
                    _cancelled = false;
                    // just use any button id other than 'ok':
                    buttonPressed(IDialogConstants.FINISH_ID);
                }
            });

            // optional row 2 : separator between OpenId and external auth:
            Label separator = new Label(container, SWT.NONE);
            separator.setText(L.product() + " user without " +
                    Identity.SERVICE_IDENTIFIER + " accounts?");
            separator.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
        }

        // row 2

        if (L.isMultiuser()) {
            new Label(container, SWT.RIGHT).setText(S.ADMIN_EMAIL + ":");

            _txtEmail = new Text(container, SWT.NONE);
            _txtEmail.setText(_setupModel.getUsername());
            _txtEmail.addModifyListener(new ModifyListener()
            {
                @Override
                public void modifyText(ModifyEvent ev)
                {
                    updateButtons();
                }
            });
            _txtEmail.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        } else {
            new Label(container, SWT.RIGHT).setText(S.SETUP_USER_ID + ":");

            _txtEmail = new Text(container, SWT.NONE);
            _txtEmail.setEditable(false);
            _txtEmail.setText(_setupModel.getUsername());
            _txtEmail.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        }

        // row 3

        new Label(container, SWT.RIGHT).setText(
                L.isMultiuser() ? S.ADMIN_PASSWD + ":" : S.SETUP_PASSWD + ":");

        _txtPasswd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                updateButtons();
            }
        });
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _txtPasswd.setFocus();

        return container;
    }

    private void updateButtons()
    {
        getButton(IDialogConstants.OK_ID)
                .setEnabled(!_txtEmail.getText().isEmpty() && !_txtPasswd.getText().isEmpty());
    }

    /**
     * Create contents of the button bar
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        final Button button = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
                true);
        button.setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            _setupModel.setSignInActor(new CredentialActor());
            _setupModel.setUserID(_txtEmail.getText());
            _setupModel.setPassword(_txtPasswd.getText());
            _cancelled = false;
        }

        super.buttonPressed(IDialogConstants.OK_ID);
    }

    boolean isCancelled()
    {
        return _cancelled;
    }
}
