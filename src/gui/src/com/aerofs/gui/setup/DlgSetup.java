package com.aerofs.gui.setup;

import java.io.IOException;
import java.net.ConnectException;

import javax.annotation.Nullable;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
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
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;

public class DlgSetup extends AeroFSTitleAreaDialog
{
    private static final Logger l = Util.l(DlgSetup.class);

    private String _absRootAnchor;
    private String _deviceName;
    private String _invitedUser;
    private boolean _isTargetedInvite;

    private Button _btnIsExistingUser;
    private Text _txtUserID;
    private Text _txtPasswd;
    private Text _txtPasswd2;

    private Label _lblPasswd2;
    private boolean _okay;

    private Label _lblFirstName;
    private Label _lblLastName;
    private Text _txtFirstName;
    private Text _txtLastName;
    public boolean _forceInvite;

    private int _sequence;
    private boolean _inProgress;
    private Label _lblIC;
    private Text _txtIC;
    private final StackLayout _layoutStack = new StackLayout();

    // On 10.5 the cocoasudo dialog goes behind the setup dialog if it's always on top.
    private static final boolean s_alwaysOnTop =
        !(OSUtil.isOSX() && System.getProperty("os.version").startsWith("10.5"));
    private Composite _compStack;
    private Composite _compTOS;
    private Composite _compForgotPassword;
    private Composite _compBlank;
    private CompSpin _compSpin;
    private Label _lblError;
    private CompSpin _compSpinIC;
    private Label _lblStatus;
    private boolean _isExistingUser;

    public DlgSetup(Shell parentShell)
            throws Exception
    {
        super(null, parentShell, false, s_alwaysOnTop, false);
        _forceInvite = true;//!Cfg.staging(appRoot);

        GetSetupSettingsReply defaults = UI.controller().getSetupSettings();
        _absRootAnchor = defaults.getRootAnchor();
        _deviceName = defaults.getDeviceName();
    }

    /**
     * Create contents of the dialog
     */
    @SuppressWarnings("all")
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite area = (Composite) super.createDialogArea(parent);
        final Composite container = new Composite(area, SWT.NONE);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.marginTop = 35;
        gridLayout.marginBottom = 5;
        gridLayout.marginRight = 38;
        gridLayout.marginLeft = 45;
        gridLayout.horizontalSpacing = 10;
        gridLayout.numColumns = 3;
        container.setLayout(gridLayout);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));
        new Label(container, SWT.NONE);

        _btnIsExistingUser = new Button(container, SWT.CHECK);
        _btnIsExistingUser.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false,
                false, 1, 1));
        _btnIsExistingUser.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(final SelectionEvent ev)
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

                if (r) {
                    _layoutStack.topControl = _compForgotPassword;
                } else {
                    _layoutStack.topControl = _compTOS;
                    _txtUserID.setText(_isTargetedInvite ? _invitedUser : "");
                }
                _compStack.layout();

                if (r) {
                    _txtUserID.setEnabled(true);
                } else if (_forceInvite || _isTargetedInvite) {
                    _txtUserID.setEnabled(false);
                } else {
                    _txtUserID.setEnabled(true);
                }

                if (r) _txtUserID.setFocus();
                else _txtIC.setFocus();

                verify(null);
            }
        });

        _btnIsExistingUser.setText("I already have an " + S.PRODUCT + " account");

        if (_forceInvite && !_isTargetedInvite) {
            new Label(container, SWT.NONE);

            _lblIC = new Label(container, SWT.NONE);
            _lblIC.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            _lblIC.setText(S.SETUP_IC + ":");

            _txtIC = new Text(container, SWT.BORDER);
            _txtIC.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

            _txtIC.addVerifyListener(new VerifyListener() {
                public void verifyText(final VerifyEvent ev)
                {
                    // verify that the email field is unselected...
                    _txtUserID.setEnabled(false);

                    _compSpin.stop();
                    setStatus("", "");

                    assert _forceInvite;
                    _isTargetedInvite = false;
                    String text = GUIUtil.getNewText(_txtIC, ev).trim();
                    if (!text.isEmpty()) resolveInvitationCode(text);
                }
            });

            _compSpinIC = new CompSpin(container, SWT.NONE);

        } else {
            _compSpinIC = new CompSpin(container, SWT.NONE);
        }

        if (GUIUtil.isWindowBuilderPro()) // $hide$
            new Label(container, SWT.NONE);

        final Label emailAddressLabel = new Label(container, SWT.NONE);
        emailAddressLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER,
                false, false, 1, 1));
        emailAddressLabel.setText(S.SETUP_USER_ID + ":");

        _txtUserID = new Text(container, SWT.BORDER);
        GridData gdEmail = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gdEmail.widthHint = 30;
        _txtUserID.setLayoutData(gdEmail);

        if (_forceInvite || _isTargetedInvite) {
            _txtUserID.setEnabled(false);
        }

        if (_isTargetedInvite) {
            _txtUserID.setText(_invitedUser);
        }

        _txtUserID.addVerifyListener(new VerifyListener() {
            public void verifyText(final VerifyEvent ev)
            {
                verify(GUIUtil.getNewText(_txtUserID.getText(), ev));
            }
        });

        new Label(container, SWT.NONE);

        final Label enterPasswordLabel = new Label(container, SWT.NONE);
        enterPasswordLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER,
                false, false, 1, 1));
        enterPasswordLabel.setText(S.SETUP_PASSWD + ":");

        // N.B. because MacOSX can't handle password fields' verify events
        // correctly, we have to use ModifyListeners
        _txtPasswd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false,
                false, 1, 1));
        _txtPasswd.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent ev) {
                verify(null);
            }
        });
        new Label(container, SWT.NONE);

        _lblPasswd2 = new Label(container, SWT.NONE);
        _lblPasswd2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false,
                false, 1, 1));
        _lblPasswd2.setText(S.SETUP_RETYPE_PASSWD + ":");

        _txtPasswd2 = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd2.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent ev) {
                verify(null);
            }
        });
        _txtPasswd2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false,
                false));
        new Label(container, SWT.NONE);

        _lblFirstName = new Label(container, SWT.NONE);
        _lblFirstName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblFirstName.setText(S.SETUP_FIRST_NAME + ":");

        _txtFirstName = new Text(container, SWT.BORDER);
        _txtFirstName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        new Label(container, SWT.NONE);

        _lblLastName = new Label(container, SWT.NONE);
        _lblLastName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        _lblLastName.setText(S.SETUP_LAST_NAME + ":");

        _txtLastName = new Text(container, SWT.BORDER);
        _txtLastName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        FullName defaultName = UIUtil.getDefaultFullName();
        _txtFirstName.setText(defaultName._first);
        _txtLastName.setText(defaultName._last);

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        Composite composite = new Composite(container, SWT.NONE);
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.horizontalSpacing = 0;
        glComposite.verticalSpacing = 12;
        glComposite.marginHeight = 0;
        glComposite.marginWidth = 0;
        composite.setLayout(glComposite);
        composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));

        _compStack = new Composite(composite, SWT.NONE);
        _compStack.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 3, 1));
        _compStack.setSize(306, 28);
        _compStack.setLayout(_layoutStack);

        _compTOS = new Composite(_compStack, SWT.NONE);
        GridLayout gl_compTOS = new GridLayout(1, false);
        gl_compTOS.marginHeight = 0;
        gl_compTOS.marginWidth = 0;
        _compTOS.setLayout(gl_compTOS);
        _layoutStack.topControl = _compTOS;

        Link linkTOS = new Link(_compTOS, SWT.NONE);
        linkTOS.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 1, 1));
        linkTOS.setToolTipText("");
        linkTOS.setText("By proceeding you agree to the <a>Terms of Service</a>");
        linkTOS.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                Program.launch(S.TOS_URL);
            }
        });

        _compForgotPassword= new Composite(_compStack, SWT.NONE);
        GridLayout gl_compForgotPassword= new GridLayout(1, false);
        gl_compForgotPassword.marginHeight = 0;
        gl_compForgotPassword.marginWidth = 0;
        _compForgotPassword.setLayout(gl_compForgotPassword);

        Link linkForgotPassword = new Link(_compForgotPassword, SWT.NONE);
        linkForgotPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 1, 1));
        linkForgotPassword.setToolTipText("");
        linkForgotPassword.setText("Forgot your password? <a>Click here to reset it.</a>");
        linkForgotPassword.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                Program.launch(S.PASSWORD_RESET_REQUEST_URL);
            }
        });

        _compBlank = new Composite(_compStack, SWT.NONE);
        _compBlank.setLayout(new GridLayout(1, false));

        _lblStatus = new Label(composite, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        _compSpin = new CompSpin(composite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

        _lblError = new Label(composite, SWT.NONE);
        _lblError.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        new Label(container, SWT.NONE);

        setTitleImage(Images.get(Images.IMG_SETUP));

        setTitle("Setup " + S.PRODUCT + " (beta) " + (Cfg.staging() ? " staging" : ""));

        if (_forceInvite && !_isTargetedInvite) _txtIC.setFocus();
        else if (_txtUserID.getText().isEmpty()) _txtUserID.setFocus();
        else _txtPasswd.setFocus();

        container.setTabList(new Control[] {_btnIsExistingUser, _txtIC, _txtUserID, _txtPasswd,
                _txtPasswd2, _txtFirstName, _txtLastName });

        _txtFirstName.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent ev) {
                verify(null);
            }
        });

        _txtLastName.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent ev) {
                verify(null);
            }
        });

        return area;
    }

    private void setStatus(String error, String status)
    {
        // the following code is becuase the parent component has
        // horizontalSpace == 0 so the icon can be perfectly aligned
        if (!error.isEmpty()) error = " " + error;
        if (!status.isEmpty()) status = status + " ";

        String prevError = _lblError.getText();
        String prevStatus = _lblStatus.getText();
        _lblError.setText(error);
        _lblStatus.setText(status);

        if (!prevError.equals(error) || !prevStatus.equals(status)) {
            _lblStatus.pack();
            _lblError.pack();
            _lblError.getParent().layout();
            _lblError.getParent().pack();
            getShell().pack();
        }
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
                    _isTargetedInvite = !_invitedUser.isEmpty();
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

                if (_isTargetedInvite) {
                    // resolved to a direct invitation code
                    _txtUserID.setText(_invitedUser);
                } else {
                    // nope - batch invitation code
                    _txtUserID.setText("");
                    _txtUserID.setEnabled(true);
                    _txtUserID.setFocus();
                }

                _compSpinIC.stop();
                _compSpin.stop();
                setStatus("", "");
            }

            @Override
            public void error(Exception e)
            {
                // if another resolveIC worker has started
                if (sequence != _sequence) return;

                Util.l(DlgSetup.class).warn("verify invitation code: " + Util.e(e));

                _txtUserID.setText("");
                _compSpinIC.stop();
                _compSpin.error();

                setStatus(S.SETUP_CANT_VERIFY_IIC + UIUtil.e2msg(e), "");
            }
        });
    }

    @Override
    protected void configureShell(final Shell newShell)
    {
        super.configureShell(newShell);

        newShell.addTraverseListener(new TraverseListener() {
            @Override
            public void keyTraversed(TraverseEvent e)
            {
                if (e.keyCode == SWT.ESC && _inProgress) e.doit = false;
            }
        });

        newShell.addListener(SWT.Show, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                if (!s_alwaysOnTop) GUIUtil.forceActive(newShell);
            }
        });
    }

    private void verify(@Nullable String email)
    {
        if (email == null) email = _txtUserID.getText();
        String passwd = _txtPasswd.getText();
        String passwd2 = _txtPasswd2.getText();
        String firstName = _txtFirstName.getText().trim();
        String lastName = _txtLastName.getText().trim();
        boolean isExistingUser = _btnIsExistingUser.getSelection();

        email = email.trim();

        boolean ready = true;
        String err = null;

        if (email.isEmpty()) {
            ready = false;
        } else if (!Util.isValidEmailAddress(email)) {
            ready = false;
        } else if (passwd.isEmpty()) {
            ready = false;
        } else if (!isExistingUser) {
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
            _compSpin.stop();
            setStatus("", "");
        } else {
            _compSpin.error();
            setStatus(err, "");
        }
    }

    /**
     * Create contents of the button bar
     *
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, IDialogConstants.OK_ID, "Finish", true).setEnabled(false);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        createButton(parent, IDialogConstants.DETAILS_ID, S.BTN_ADVANCED, false)
            .addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0)
                {
                    DlgSetupAdvanced advanced = new DlgSetupAdvanced(getShell(),
                            _deviceName, _absRootAnchor);
                    if (advanced.open() != IDialogConstants.OK_ID) return;

                    _deviceName = advanced.getDeviceName();
                    _absRootAnchor = advanced.getAbsoluteRootAnchor();
                }
            });
    }

    private void work()
    {
        _inProgress = true;

        final String userID = _txtUserID.getText().toLowerCase().trim();
        final char[] passwd = _txtPasswd.getText().toCharArray();
        final String firstName = _txtFirstName.getText().trim();
        final String lastName = _txtLastName.getText().trim();
        final String ic = _txtIC.getText().trim();
        _isExistingUser = _btnIsExistingUser.getSelection();

        setDlgElementsState(false);

        _compSpin.start();

        _layoutStack.topControl = _compBlank;
        _compStack.layout();

        GUI.get().safeAsyncExec(getShell(), new Runnable() {
            @Override
            public void run()
            {
                setStatus("", "Performing magic...");
            }
        });

        GUI.get().unsafeWork(new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                if (_isExistingUser) {
                    UI.controller()
                            .setupExistingUser(userID, new String(passwd), _absRootAnchor,
                                    _deviceName, null);
                } else {
                    UI.controller()
                            .setupNewUser(userID, new String(passwd), _absRootAnchor, _deviceName,
                                    ic, firstName, lastName, null);
                }

                // setup shell extension
                while (true) {
                    try {
                        OSUtil.get().installShellExtension(false);
                        break;
                    } catch (SecurityException e) {
                        if (!UI.get()
                                .ask(MessageType.QUESTION,
                                        S.PRODUCT + " needs your authorization to install the " +
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
            public void error(Exception e)
            {
                if (e instanceof ExAborted) {
                    // waitForTransportsAndSPReady may throw this
                    DlgSetup.super.buttonPressed(IDialogConstants.CANCEL_ID);

                } else {
                    l.error("Setup error", e);
                    String msg = null;
                    if (e instanceof ConnectException) {
                        msg = "Sorry, couldn't connect to the server. Please try again later.";
                    }

                    // TODO: Catch ExAlreadyExist and ExNoPerm here, and ask the user if he wants us to
                    // move the anchor root. See CLISetup.java.

                    if (msg == null) msg = "Sorry, " + UIUtil.e2msgNoBracket(e) + '.';
                    _compSpin.error();
                    setStatus(msg, "");

                    _inProgress = false;
                    _layoutStack.topControl = _compBlank;
                    _compStack.layout();

                    getButton(IDialogConstants.OK_ID).setText("Try Again");
                    setDlgElementsState(true);
                }
            }

            @Override
            public void okay()
            {
                _okay = true;
                DlgSetup.super.buttonPressed(IDialogConstants.OK_ID);
            }
        });
    }

    public boolean isCancelled()
    {
        return !_okay;
    }

    private void setDlgElementsState(boolean b)
    {
        getButton(IDialogConstants.OK_ID).setEnabled(b);
        getButton(IDialogConstants.CANCEL_ID).setEnabled(b);
        getButton(IDialogConstants.DETAILS_ID).setEnabled(b);
        _txtFirstName.setEnabled(b);
        _txtLastName.setEnabled(b);
        if (_btnIsExistingUser.getSelection() || (!_forceInvite && !_isTargetedInvite)) {
            _txtUserID.setEnabled(b);
        }
        _txtPasswd.setEnabled(b);
        _txtPasswd2.setEnabled(b);
        _btnIsExistingUser.setEnabled(b);
        if (_txtIC != null) _txtIC.setEnabled(b);
    }

    public boolean isExistingUser()
    {
        return _isExistingUser;
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            work();
        } else {
            super.buttonPressed(buttonId);
        }
    }
}
