/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.controller.CredentialUtil;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.UIParam;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

/**
 * Show this dialog after the user has changed the password on the web site causing the local
 * device unable to login with the stored password.
 */
public class DlgRetypePassword extends AeroFSJFaceDialog
{
    private static final Logger l = Loggers.getLogger(DlgRetypePassword.class);

    private Label _status;
    private Text _passwd;

    public DlgRetypePassword(Shell parentShell)
    {
        super("Enter Password", parentShell, false, false, true, true);
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
        gridLayout.numColumns = 1;
        container.setLayout(gridLayout);

        // row 1

        Label label = new Label(container, SWT.NONE);
        label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        label.setText(S.PASSWORD_HAS_CHANGED + ":");

        // row 2

        _passwd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _passwd.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                getButton(IDialogConstants.OK_ID).setEnabled(!_passwd.getText().isEmpty());
            }
        });
        final GridData gd__passwd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        _passwd.setLayoutData(gd__passwd);

        // row 3

        _status = new Label(container, SWT.NONE);
        _status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        return container;
    }

    /**
     * Create contents of the button bar
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        final Button button = createButton(parent, IDialogConstants.OK_ID, "Sign In",
                true);
        button.setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    private void enableControls(boolean enabled)
    {
        _passwd.setEnabled(enabled);
        getButton(IDialogConstants.OK_ID).setEnabled(enabled);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId != IDialogConstants.OK_ID) {
            super.buttonPressed(buttonId);

        } else {
            enableControls(false);
            _status.setText("Logging in...");

            final String passwd = _passwd.getText();

            GUI.get().safeWork(getShell(), new ISWTWorker()
            {
                @Override
                public void run() throws Exception
                {
                    try {
                        CredentialUtil.updateStoredPassword(
                                UserID.fromExternal(Cfg.user().getString()), passwd.toCharArray());
                    } catch (Exception e) {
                        ThreadUtil.sleepUninterruptable(UIParam.LOGIN_PASSWD_RETRY_DELAY);
                        throw e;
                    }
                }

                @Override
                public void error(Exception e)
                {
                    enableControls(true);

                    if (e instanceof ExBadCredential) {
                        _status.setText("Password is incorrect.");
                        _passwd.setText("");
                        _passwd.setFocus();
                    } else {
                        l.warn("login: " + Util.e(e));
                        // If it's not a bad credential, show the error
                        _status.setText(S.PASSWORD_CHANGE_INTERNAL_ERROR);
                    }

                    getShell().pack();
                }

                @Override
                public void okay()
                {
                    DlgRetypePassword.super.buttonPressed(IDialogConstants.OK_ID);
                }
            });
        }
    }
}
