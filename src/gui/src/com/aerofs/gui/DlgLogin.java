package com.aerofs.gui;

import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import org.apache.log4j.Logger;
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


import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.ui.UIParam;
import com.aerofs.gui.GUI.ISWTWorker;

public class DlgLogin extends AeroFSJFaceDialog {

    private static final Logger l = Util.l(DlgLogin.class);

    private Label _status;
    private Label label;
    private Label passwordLabel;
    private Label emailAddressLabel;
    private Text _passwd;
    private Label _email;

    public DlgLogin(Shell parentShell)
    {
        super("Login", parentShell, false, false, true, true);
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        GUIUtil.setShellIcon(getShell());

        Composite container = (Composite) super.createDialogArea(parent);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.marginRight = 20;
        gridLayout.numColumns = 2;
        gridLayout.verticalSpacing = 15;
        gridLayout.marginTop = 20;
        gridLayout.marginLeft = 20;
        container.setLayout(gridLayout);

        emailAddressLabel = new Label(container, SWT.NONE);
        emailAddressLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        emailAddressLabel.setText("User:");

        _email = new Label(container, SWT.READ_ONLY);
        _email.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        _email.setText(Cfg.user().toString());

        passwordLabel = new Label(container, SWT.NONE);
        passwordLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        passwordLabel.setText("Password:");

        _passwd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _passwd.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent arg0)
            {
                getButton(IDialogConstants.OK_ID).setEnabled(
                        !_passwd.getText().isEmpty());
            }
        });
        final GridData gd__passwd = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        gd__passwd.widthHint = 129;
        _passwd.setLayoutData(gd__passwd);
        label = new Label(container, SWT.NONE);

        _status = new Label(container, SWT.WRAP);
        final GridData gd__status = new GridData(SWT.FILL, SWT.CENTER, false, false);
        gd__status.heightHint = 33;
        _status.setLayoutData(gd__status);
        container.setTabList(new Control[] {_passwd, emailAddressLabel, _email, passwordLabel, label, _status});

        return container;
    }

    /**
     * Create contents of the button bar
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        final Button button = createButton(parent, IDialogConstants.OK_ID, "Sign In",
                true);
        button.setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID,
                IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId != IDialogConstants.OK_ID) {
            super.buttonPressed(buttonId);

        } else {
            getButton(IDialogConstants.OK_ID).setEnabled(false);
            _status.setText("Logging in...");

            final String passwd = _passwd.getText();

            GUI.get().unsafeWork(new ISWTWorker()
            {
                @Override
                public void run()
                        throws Exception
                {
                    try {
                        UI.controller().updateStoredPassword(Cfg.user().toString(), passwd);
                    } catch (Exception e) {
                        ThreadUtil.sleepUninterruptable(UIParam.LOGIN_PASSWD_RETRY_DELAY);
                        throw e;
                    }
                }

                @Override
                public void error(Exception e)
                {
                    l.warn("login: " + Util.e(e));
                    if (e instanceof ExBadCredential) {
                        _status.setText(S.BAD_CREDENTIAL_CAP);
                        _passwd.setText("");
                        _passwd.setFocus();
                    } else {
                        // If it's not a bad credential, show the error
                        GUI.get()
                                .show(getShell(), MessageType.ERROR,
                                        S.PASSWORD_CHANGE_INTERNAL_ERROR + "\n" + UIUtil.e2msg(e));
                        _status.setText("");
                    }
                    getButton(IDialogConstants.OK_ID).setEnabled(true);
                }

                @Override
                public void okay()
                {
                    DlgLogin.super.buttonPressed(IDialogConstants.OK_ID);
                }
            });
        }
    }
}
