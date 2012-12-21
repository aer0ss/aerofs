/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.singleuser;

import java.io.IOException;

import javax.annotation.Nullable;

import com.aerofs.gui.setup.DlgSetupCommon;
import com.aerofs.gui.setup.DlgSetupCommon.IDlgSetupCommonCallbacks;
import com.aerofs.gui.setup.IDlgSetup;
import com.aerofs.labeling.L;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
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
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Param;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;

public class SingleuserDlgSetup extends AeroFSTitleAreaDialog
        implements IDlgSetup, IDlgSetupCommonCallbacks
{
    private static final Logger l = Util.l(SingleuserDlgSetup.class);
    private final DlgSetupCommon _common;

    private String _invitedUser;
    private boolean _isTargetedInvite;

    private Button _btnIsExistingUser;
    private Text _txtPasswd2;

    private Label _lblPasswd2;

    private Label _lblFirstName;
    private Label _lblLastName;
    private Text _txtFirstName;
    private Text _txtLastName;
    public boolean _forceInvite;

    private int _sequence;
    private Label _lblIC;
    private Text _txtIC;

    private Composite _compTOS;
    private CompSpin _compSpinIC;

    public SingleuserDlgSetup(Shell parentShell)
            throws Exception
    {
        super(null, parentShell, false, DlgSetupCommon.shouldAlwaysOnTop(), false);
        _common = new DlgSetupCommon(this);

        _forceInvite = true;//!Cfg.staging(appRoot);
    }

    /**
     * Create contents of the dialog
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Control area = super.createDialogArea(parent);
        Composite container = _common.createContainer(area, 3);

        // row 1

        new Label(container, SWT.NONE);

        _btnIsExistingUser = new Button(container, SWT.CHECK);
        _btnIsExistingUser.setText("I already have an " + L.PRODUCT + " account");
        _btnIsExistingUser.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
        _btnIsExistingUser.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(final SelectionEvent ev)
            {
                layoutUIForNewOrExistingUser();
            }
        });

        if (_forceInvite && !_isTargetedInvite) {
            new Label(container, SWT.NONE);

            // row 2

            _lblIC = new Label(container, SWT.NONE);
            _lblIC.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            _lblIC.setText(S.SETUP_IC + ":");

            _txtIC = new Text(container, SWT.BORDER);
            _txtIC.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

            _txtIC.addVerifyListener(new VerifyListener()
            {
                public void verifyText(final VerifyEvent ev)
                {
                    // verify that the email field is unselected...
                    _common.getUserIDText().setEnabled(false);

                    _common.setOkayStatus();

                    assert _forceInvite;
                    _isTargetedInvite = false;
                    String text = GUIUtil.getNewText(_txtIC, ev).trim();
                    if (!text.isEmpty()) resolveInvitationCode(text);
                }
            });
        }

        _compSpinIC = new CompSpin(container, SWT.NONE);

        // row 3

        if (GUIUtil.isWindowBuilderPro()) // $hide$
            new Label(container, SWT.NONE);

        _common.createUserIDInputLabelAndText(container);
        Text txtUserID = _common.getUserIDText();

        if (_forceInvite || _isTargetedInvite) txtUserID.setEnabled(false);

        if (_isTargetedInvite) txtUserID.setText(_invitedUser);

        new Label(container, SWT.NONE);

        // row 4

        _common.createPasswordLabelAndText(container);
        new Label(container, SWT.NONE);

        // row 5

        _lblPasswd2 = new Label(container, SWT.NONE);
        _lblPasswd2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblPasswd2.setText(S.SETUP_RETYPE_PASSWD + ":");

        _txtPasswd2 = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd2.addModifyListener(new ModifyListener()
        {
            public void modifyText(ModifyEvent ev)
            {
                verify(null);
            }
        });
        _txtPasswd2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
        new Label(container, SWT.NONE);

        // row 6

        _lblFirstName = new Label(container, SWT.NONE);
        _lblFirstName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblFirstName.setText(S.SETUP_FIRST_NAME + ":");

        _txtFirstName = new Text(container, SWT.BORDER);
        _txtFirstName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        FullName defaultName = UIUtil.getDefaultFullName();
        _txtFirstName.setText(defaultName._first);
        _txtFirstName.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                verify(null);
            }
        });

        new Label(container, SWT.NONE);

        // row 7

        _lblLastName = new Label(container, SWT.NONE);
        _lblLastName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblLastName.setText(S.SETUP_LAST_NAME + ":");

        _txtLastName = new Text(container, SWT.BORDER);
        _txtLastName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        _txtLastName.setText(defaultName._last);
        _txtLastName.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                verify(null);
            }
        });

        new Label(container, SWT.NONE);

        // row 8

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        // row 9

        _common.createBottomComposite(container);

        createTOSComposite();

        _common.setBottomCompositeTopControlForEnabledState();

        new Label(container, SWT.NONE);

        // done with rows

        setTitle(_common.getTitle());

        setTitleImage(Images.get(Images.IMG_SETUP));

        if (_forceInvite && !_isTargetedInvite) _txtIC.setFocus();
        else if (txtUserID.getText().isEmpty()) txtUserID.setFocus();
        else _common.getPasswordText().setFocus();

        container.setTabList(
                new Control[]{_btnIsExistingUser, _txtIC, txtUserID, _common.getPasswordText(),
                        _txtPasswd2, _txtFirstName, _txtLastName});

        return area;
    }

    private void createTOSComposite()
    {
        _compTOS = new Composite(_common.getStackComposite(), SWT.NONE);
        GridLayout gl_compTOS = new GridLayout(1, false);
        gl_compTOS.marginHeight = 0;
        gl_compTOS.marginWidth = 0;
        _compTOS.setLayout(gl_compTOS);

        Link linkTOS = new Link(_compTOS, SWT.NONE);
        linkTOS.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 1, 1));
        linkTOS.setToolTipText("");
        linkTOS.setText("By proceeding you agree to the <a>Terms of Service</a>");
        linkTOS.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                GUIUtil.launch(S.TOS_URL);
            }
        });
    }

    @Override
    public void openDialog()
    {
        open();
    }

    private void layoutUIForNewOrExistingUser()
    {
        boolean r = _btnIsExistingUser.getSelection();
        _txtPasswd2.setVisible(!r);
        _lblPasswd2.setVisible(!r);
        _txtFirstName.setVisible(!r);
        _txtLastName.setVisible(!r);
        _lblFirstName.setVisible(!r);
        _lblLastName.setVisible(!r);

        if (_lblIC != null) {
            _lblIC.setVisible(!r);
            _txtIC.setVisible(!r);
        }

        _compSpinIC.setVisible(!r);

        _common.setBottomCompositeTopControlForEnabledState();

        Text txtUserID = _common.getUserIDText();
        if (r) txtUserID.setText(_isTargetedInvite ? _invitedUser : "");

        if (!r && (_forceInvite || _isTargetedInvite)) {
            txtUserID.setEnabled(false);
        } else {
            txtUserID.setEnabled(true);
        }

        if (r) txtUserID.setFocus();
        else _txtIC.setFocus();

        verify(null);
    }

    private void resolveInvitationCode(final String ic)
    {
        final int sequence = ++_sequence;

        getButton(IDialogConstants.OK_ID).setEnabled(false);
        _compSpinIC.start();

        GUI.get().unsafeWork(new ISWTWorker()
        {

            @Override
            public void run()
                    throws Exception
            {
                try {
                    _invitedUser = UI.controller().resolveSignUpCode(ic).getEmail();
                    _isTargetedInvite = true;
                } catch (Exception e) {
                    _isTargetedInvite = false;
                    throw e;
                }
            }

            @Override
            public void okay()
            {
                // if another resolveIC worker has started
                if (sequence != _sequence) return;

                Text txtUserID = _common.getUserIDText();

                if (_isTargetedInvite) {
                    // resolved to a direct invitation code
                    txtUserID.setText(_invitedUser);
                } else {
                    // nope - batch invitation code
                    txtUserID.setText("");
                    txtUserID.setEnabled(true);
                    txtUserID.setFocus();
                }

                _compSpinIC.stop();
                _common.setOkayStatus();
            }

            @Override
            public void error(Exception e)
            {
                // if another resolveIC worker has started
                if (sequence != _sequence) return;

                Util.l(SingleuserDlgSetup.class).warn("verify invitation code: " + Util.e(e));

                _compSpinIC.stop();
                _common.setErrorStatus(S.SETUP_CANT_VERIFY_IIC + UIUtil.e2msg(e));
            }
        });
    }

    @Override
    protected void configureShell(final Shell newShell)
    {
        super.configureShell(newShell);
        _common.configureShell(newShell);
    }

    @Override
    public void verify(@Nullable String email)
    {
        boolean ready = _common.isReady(email);

        String passwd = _common.getPasswordText().getText();
        String passwd2 = _txtPasswd2.getText();
        String firstName = _txtFirstName.getText().trim();
        String lastName = _txtLastName.getText().trim();
        boolean isExistingUser = _btnIsExistingUser.getSelection();

        String err = null;

        if (ready && !isExistingUser) {
            if (passwd.length() < Param.MIN_PASSWD_LENGTH) {
                ready = false;
                err = S.SETUP_PASSWD_TOO_SHORT;
            } else if (!Util.isValidPassword(passwd.toCharArray())) {
                ready = false;
                err = S.SETUP_PASSWD_INVALID;
            } else if (!passwd.equals(passwd2)) {
                ready = false;
            } else if (firstName.isEmpty() || lastName.isEmpty()) {
                ready = false;
            }
        }

        getButton(IDialogConstants.OK_ID).setEnabled(ready);

        if (err == null) {
            _common.setOkayStatus();
        } else {
            _common.setErrorStatus(err);
        }
    }

    /**
     * Create contents of the button bar
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        _common.createButtonBarButtons(parent);
    }

    private FullName _fullName;
    private String _ic;
    private boolean _isExistingUser;

    @Override
    public void createButtonBarButton(Composite parent, int id, String text, boolean setDefault)
    {
        createButton(parent, id, text, setDefault);
    }

    @Override
    public Button getButtonBarButton(int id)
    {
        return getButton(id);
    }

    @Override
    public void preSetup()
    {
        _fullName = new FullName(_txtFirstName.getText().trim(), _txtLastName.getText().trim());
        _ic = _txtIC.getText().trim();
        _isExistingUser = _btnIsExistingUser.getSelection();
    }

    @Override
    public void runSetup(String userID, char[] passwd)
            throws Exception
    {
        String absRootAnchor = _common.getAbsRootAnchor();
        String deviceName = _common.getDeviceName();

        if (_isExistingUser) {
            UI.controller().setupExistingUser(userID, new String(passwd), absRootAnchor, deviceName,
                    null);
        } else {
            UI.controller().setupNewUser(userID, new String(passwd), absRootAnchor, deviceName, _ic,
                    _fullName._first, _fullName._last, null);
        }

        // setup shell extension
        while (true) {
            try {
                OSUtil.get().installShellExtension(false);
                break;
            } catch (SecurityException e) {
                if (!UI.get()
                        .ask(MessageType.QUESTION,
                                L.PRODUCT + " needs your authorization to install the " +
                                        OSUtil.get().getShellExtensionName() + ".\n\n" +
                                        "Would you like to retry entering your password?\n" +
                                        "If you click Cancel, the " +
                                        OSUtil.get().getShellExtensionName() +
                                        " won't be available.", IDialogConstants.OK_LABEL,
                                IDialogConstants.CANCEL_LABEL)) {
                    break;
                }
            } catch (IOException e) {
                l.warn("Installing shell extension failed: " + Util.e(e));
                break;
            }
        }
    }

    @Override
    public void postSetup()
    {
    }

    @Override
    public void closeDialog()
    {
        close();
    }

    @Override
    public boolean isCancelled()
    {
        return _common.isCanelled();
    }

    @Override
    public Composite getBottomCompositeTopControlWhenEnabled()
    {
        return _btnIsExistingUser.getSelection() ? null : _compTOS;
    }

    @Override
    public void setControlState(boolean enabled)
    {
        _txtFirstName.setEnabled(enabled);
        _txtLastName.setEnabled(enabled);
        if (_btnIsExistingUser.getSelection() || (!_forceInvite && !_isTargetedInvite)) {
            _common.getUserIDText().setEnabled(enabled);
        }
        _txtPasswd2.setEnabled(enabled);
        _btnIsExistingUser.setEnabled(enabled);
        if (_txtIC != null) _txtIC.setEnabled(enabled);
    }

    @Override
    public boolean isExistingUser()
    {
        return _isExistingUser;
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            _common.work();
        } else {
            super.buttonPressed(buttonId);
        }
    }
}
