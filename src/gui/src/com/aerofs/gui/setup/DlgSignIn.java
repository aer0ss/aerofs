/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.base.ex.ExInternalError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.controller.InstallActor;
import com.aerofs.controller.Setup;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.CredentialActor;
import com.aerofs.controller.SignInActor.OpenIdGUIActor;
import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.singleuser.SingleUserDlgSecondFactor;
import com.aerofs.gui.singleuser.SingleuserDlgSetupAdvanced;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
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
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.util.LinkedList;
import java.util.List;

import static com.aerofs.gui.GUIUtil.getNewText;
import static org.eclipse.jface.dialogs.IDialogConstants.CANCEL_ID;
import static org.eclipse.jface.dialogs.IDialogConstants.CANCEL_LABEL;
import static org.eclipse.jface.dialogs.IDialogConstants.DETAILS_ID;
import static org.eclipse.jface.dialogs.IDialogConstants.OK_ID;

public class DlgSignIn extends AeroFSTitleAreaDialog
{
    public DlgSignIn(Shell parentShell, SetupModel model) throws Exception
    {
        super(null, parentShell, false, shouldAlwaysOnTop(), false);

        setTitleImage(Images.get(Images.IMG_SETUP));

        _model = model;
        _model._localOptions._rootAnchorPath = Setup.getDefaultAnchorRoot();
        _model.setInstallActor(new InstallActor.SingleUser());
        _model.setDeviceName(Setup.getDefaultDeviceName());
        _showOpenIdDialog = (Identity.AUTHENTICATOR == Authenticator.OPENID);

        _helper = new APIAccessSetupHelper();
    }

    @Override
    protected void configureShell(final Shell newShell)
    {
        super.configureShell(newShell);

        newShell.addTraverseListener(new TraverseListener()
        {
            @Override
            public void keyTraversed(TraverseEvent e)
            {
                if (e.keyCode == SWT.ESC && _inProgress) e.doit = false;
            }
        });

        newShell.addListener(SWT.Show, new Listener()
        {
            @Override
            public void handleEvent(Event arg0)
            {
                if (!shouldAlwaysOnTop()) GUIUtil.forceActive(newShell);
            }
        });
    }

    /**
     * Create the outermost dialog area (containing composites for the credential
     * login piece and optionally the OpenId signin button).
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite area = new Composite(parent, SWT.NONE);

        setTitle("Setup " + L.product());

        if (_showOpenIdDialog) {
            createOpenIdComposite(area)
                    .setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

            createDivider(area, "OR")
                    .setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        }

        createCredentialComposite(area)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        area.setLayout(layout);
        area.setLayoutData(new GridData(GridData.FILL_BOTH));

        if (_defaultControl == null) { _defaultControl = _txtUserID; }
        _defaultControl.setFocus();

        return area;
    }

    /**
     * Create a composite with a label and a sign-in button.
     * Add the composite and a horizontal separator to the container.
     */
    private Composite createOpenIdComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        Button signInButton = GUIUtil.createButton(composite, SWT.PUSH);
        signInButton.setText("Sign in with " + Identity.SERVICE_IDENTIFIER);
        // Capture parent
        signInButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                _model.setSignInActor(new OpenIdGUIActor());

                setInProgressStatus();

                GUI.get().safeWork(getShell(), new SignInWorker());
            }
        });

        _controls.add(signInButton);
        _defaultControl = signInButton;

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginHeight = GUIParam.MARGIN;
        layout.marginTop = GUIParam.MARGIN;
        layout.marginBottom = 0;
        layout.center = true;
        layout.fill = true;
        composite.setLayout(layout);
        signInButton.setLayoutData(new RowData(240, 50));

        return composite;
    }

    private Composite createDivider(Composite parent, String label)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        new Label(composite, SWT.HORIZONTAL | SWT.SEPARATOR)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(composite, SWT.NONE).setText(label);
        new Label(composite, SWT.HORIZONTAL | SWT.SEPARATOR)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 0;
        composite.setLayout(layout);

        return composite;
    }

    private Composite createCredentialComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        if (_showOpenIdDialog) {
            Label label = new Label(composite, SWT.NONE);
            label.setText(L.product() + " user without " + Identity.SERVICE_IDENTIFIER + " accounts?");
            label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 3, 1));
        }

        Label lblEmail = new Label(composite, SWT.NONE);
        lblEmail.setText(S.SETUP_USER_ID + ": ");
        lblEmail.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        _txtUserID = new Text(composite, SWT.BORDER);
        _txtUserID.addVerifyListener(new VerifyListener()
        {
            @Override
            public void verifyText(VerifyEvent verifyEvent)
            {
                verify(getNewText(_txtUserID.getText(), verifyEvent));
            }
        });
        _txtUserID.setLayoutData(createTextBoxLayoutData());
        _controls.add(_txtUserID);

        Label lblPassword = new Label(composite, SWT.NONE);
        lblPassword.setText(S.SETUP_PASSWD + ": ");
        lblPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        // N.B. because MacOSX can't handle password fields' verify events
        // correctly, we have to use ModifyListeners
        _txtPasswd = new Text(composite, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                verify(null);
            }
        });
        _txtPasswd.setLayoutData(createTextBoxLayoutData());
        _controls.add(_txtPasswd);

       if (_helper._showAPIAccess) {
            new Label(composite, SWT.NONE);

           _helper.createCheckbox(composite);
           _helper.readFromModel(_model);
           _helper._chkAPIAccess.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
           _controls.add(_helper._chkAPIAccess);

           _helper.createLink(composite);
           _helper._lnkAPIAccess.setLayoutData(
                   _helper.createLinkLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false)));
           _controls.add(_helper._lnkAPIAccess);
        }

        new Label(composite, SWT.NONE);

        Link lnkForgotPassword = new Link(composite, SWT.NONE);
        lnkForgotPassword.setText("<a>Forgot your password?</a>");
        lnkForgotPassword.setToolTipText("");
        lnkForgotPassword.addSelectionListener(
                GUIUtil.createUrlLaunchListener(WWW.PASSWORD_RESET_REQUEST_URL));
        lnkForgotPassword.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
        _controls.add(lnkForgotPassword);

        new Label(composite, SWT.NONE);
        createStatusComposite(composite).setLayoutData(
                new GridData(SWT.LEFT, SWT.BOTTOM, true, false, 2, 1));

        GridLayout layout = new GridLayout(3, false);
        layout.marginHeight = 0;
        layout.marginTop = _showOpenIdDialog ? GUIParam.MARGIN : 2 * GUIParam.MARGIN;
        layout.marginWidth = 4 * GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        layout.horizontalSpacing = 0;
        composite.setLayout(layout);

        return composite;
    }

    private GridData createTextBoxLayoutData()
    {
        GridData layoutData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        if (OSUtil.isOSX()) {
            layoutData.horizontalIndent = 2;
        }
        return layoutData;
    }

    private Composite createStatusComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        _lblStatus = new Label(composite, SWT.NONE);
        _compSpin = new CompSpin(composite, SWT.NONE);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.center = true;
        composite.setLayout(layout);

        return composite;
    }

    static private boolean shouldAlwaysOnTop()
    {
        // On 10.5 the cocoasudo dialog goes behind the setup dialog if it's always on top.
        return !(OSUtil.isOSX() && OSUtil.getOSVersion().startsWith("10.5"));
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        createButton(parent, CANCEL_ID, CANCEL_LABEL, false);
        _controls.add(createButton(parent, DETAILS_ID, S.BTN_ADVANCED, false));
        _controls.add(createButton(parent, OK_ID, "Finish", true));

        getButton(OK_ID).setEnabled(false);
        getButton(DETAILS_ID).addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                AbstractDlgSetupAdvanced advanced = createAdvancedSetupDialog();
                if (advanced.open() != IDialogConstants.OK_ID) return;
                _model.setDeviceName(advanced.getDeviceName());
                _model._localOptions._rootAnchorPath = advanced.getAbsoluteRootAnchor();
            }
        });
    }

    // TODO (WW) use ModifyListener instead of VerifyListener throughout the codebase.
    // FIXME: not clear why the email string is sometimes passed in (instead of using _txtUserId)

    /**
     * Turn on the "Ok" button and set the okay status if isReady() is true.
     * If the email param is not provided, _txtUserID is checked.
     */
    private void verify(@Nullable String email)
    {
        getButton(OK_ID).setEnabled(isReady(email));
    }

    /**
     * Returns true if the email address looks like it might be email,
     * AND the password is non-empty.
     * If the email param is not provided, _txtUserID is checked.
     */
    private boolean isReady(@Nullable String email)
    {
        String trimmed = ((email == null) ? _txtUserID.getText() : email).trim();

        return !_txtPasswd.getText().isEmpty() && Util.isValidEmailAddress(trimmed);
    }

    private void setInProgressStatus()
    {
        _inProgress = true;
        setControlState(false);
        _compSpin.start();
        setStatusImpl(S.SETUP_INSTALL_MESSAGE + "...");
    }

    private void clearInProgressStatus()
    {
        _inProgress = false;
        setControlState(true);
        _compSpin.stop();
        setStatusImpl("");
    }

    private void setStatusImpl(String status)
    {
        // the following code is becuase the parent component has
        // horizontalSpace == 0 so the icon can be perfectly aligned
        if (!status.isEmpty()) status = status + " ";

        String prevStatus = _lblStatus.getText();
        _lblStatus.setText(status);

        if (!prevStatus.equals(status)) {
            getShell().layout(new Control[] { _lblStatus });
        }
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            _model.setUserID(_txtUserID.getText().trim());
            _model.setPassword(_txtPasswd.getText());
            _model.setSignInActor(new CredentialActor());

            _helper.writeToModel(_model);

            setInProgressStatus();

            GUI.get().safeWork(getShell(), new SignInWorker());

        } else {
            super.buttonPressed(buttonId);
        }
    }

    private static String formatExceptionMessage(Exception e)
    {
        if (e instanceof ConnectException) return S.SETUP_ERR_CONN;
        else if (e instanceof ExBadCredential) return S.BAD_CREDENTIAL_CAP + ".";
        else if (e instanceof ExExternalAuthFailure) return S.OPENID_AUTH_BAD_CRED;
        else if (e instanceof ExUIMessage) return e.getMessage();
        else if (e instanceof ExTimeout) return S.OPENID_AUTH_TIMEOUT;
        else if (e instanceof ExInternalError) return S.SERVER_INTERNAL_ERROR;
        else return S.SETUP_DEFAULT_SIGNIN_ERROR;
    }

    class SignInWorker implements ISWTWorker
    {
        @Override
        public void run() throws Exception
        {
            _model.doSignIn();
        }

        @Override
        public void error(Exception e)
        {
            l.error("Setup error", e);
            ErrorMessages.show(getShell(), e, formatExceptionMessage(e));
            clearInProgressStatus();
            getButton(OK_ID).setText("Try Again");
        }

        @Override
        public void okay()
        {
            if (_model.getNeedSecondFactor()) {
                // Open modal dialog prompting for second factor
                SingleUserDlgSecondFactor modal = new SingleUserDlgSecondFactor(getShell(), _model);
                int res = modal.open();
                if (res == IDialogConstants.OK_ID) {
                    GUI.get().safeWork(getShell(), installWorker());
                } else {
                    // Two factor auth dialog was closed by quit button; quit
                    close();
                }
            } else {
                // No second factor needed?  Jump straight to running the install worker.
                GUI.get().safeWork(getShell(), installWorker());
            }
        }
    }

    public ISWTWorker installWorker()
    {
        return new ISWTWorker() {
            @Override
            public void run() throws Exception
            {
                _model.doInstall();
                setupShellExtension();
            }

            @Override
            public void okay()
            {
                _okay = true;
                close();
            }

            @Override
            public void error(Exception e)
            {
                l.error("Failed to register and setup device", e);
                ErrorMessages.show(getShell(), e, formatExceptionMessage(e));
                clearInProgressStatus();
            }
        };
    }


    private void setControlState(boolean enabled)
    {
        for (Control c : _controls) { c.setEnabled(enabled); }

        getButton(OK_ID).setEnabled(enabled && isReady(_txtUserID.getText()));
    }

    @Override
    public boolean isCancelled()
    {
        return !_okay;
    }

    private void setupShellExtension() throws ExNoConsole
    {
        while (true) {
            try {
                OSUtil.get().installShellExtension(false);
                break;
            } catch (SecurityException e) {
                if (!UI.get()
                        .ask(MessageType.QUESTION,
                                L.product() + " needs your authorization to install the " +
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

    private AbstractDlgSetupAdvanced createAdvancedSetupDialog()
    {
        return new SingleuserDlgSetupAdvanced(getShell(),
                _model.getDeviceName(), _model._localOptions._rootAnchorPath);
    }

    protected static final Logger l = Loggers.getLogger(DlgSignIn.class);

    // controls that should be enabled/disabled with setControlState:
    List<Control>   _controls = new LinkedList<Control>();
    Control         _defaultControl;

    private boolean _showOpenIdDialog;

    private final APIAccessSetupHelper _helper;

    private CompSpin _compSpin;
    private Label _lblStatus;

    private Text _txtUserID;
    private Text _txtPasswd;

    private boolean _okay;
    private boolean _inProgress;

    private SetupModel _model;
}
