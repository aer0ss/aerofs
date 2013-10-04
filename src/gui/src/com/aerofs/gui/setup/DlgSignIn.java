/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExInternalError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.controller.InstallActor;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.CredentialActor;
import com.aerofs.controller.SignInActor.OpenIdGUIActor;
import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.singleuser.SingleuserDlgSetupAdvanced;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
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

import static org.eclipse.jface.dialogs.IDialogConstants.CANCEL_ID;
import static org.eclipse.jface.dialogs.IDialogConstants.CANCEL_LABEL;
import static org.eclipse.jface.dialogs.IDialogConstants.DETAILS_ID;
import static org.eclipse.jface.dialogs.IDialogConstants.OK_ID;

public class DlgSignIn extends AeroFSTitleAreaDialog
{
    public DlgSignIn(Shell parentShell) throws Exception
    {
        super(null, parentShell, false, shouldAlwaysOnTop(), false);

        GetSetupSettingsReply defaults = UIGlobals.controller().getSetupSettings();

        setTitleImage(Images.get(Images.IMG_SETUP));

        _model = new SetupModel();
        _model._localOptions._rootAnchorPath = defaults.getRootAnchor();
        _model.setInstallActor(new InstallActor.SingleUser());
        _model.setDeviceName(defaults.getDeviceName());
        _showOpenIdDialog = (Identity.AUTHENTICATOR == Authenticator.OPENID);
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
        Control     area = super.createDialogArea(parent);
        Composite   areaComposite = (Composite)area;
        areaComposite.setLayout(new GridLayout());

        setTitle("Setup " + L.product());

        if (_showOpenIdDialog) {
            createOpenIdComposite(areaComposite);

            new Label(areaComposite, SWT.HORIZONTAL | SWT.SEPARATOR)
                    .setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        }

        createCredentialComposite(areaComposite);

        if (_defaultControl == null) { _defaultControl = _txtUserID; }
        _defaultControl.setFocus();

        return area;
    }

    /**
     * Create a composite with a label and a sign-in button.
     * Add the composite and a horizontal separator to the container.
     */
    private void createOpenIdComposite(Composite dialogContainer)
    {
        Composite composite = createContainer(dialogContainer, 1);
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        Button signInButton = GUIUtil.createButton(composite, SWT.PUSH);
        signInButton.setText("Sign in with " + OpenId.SERVICE_IDENTIFIER);
        GridData layoutData = new GridData(GridData.FILL_BOTH);
        layoutData.heightHint = 50;
        signInButton.setLayoutData(layoutData);

        signInButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                super.widgetSelected(e);

                _inProgress = true;
                _model.setSignInActor(new OpenIdGUIActor());
                setControlState(false);
                setInProgressStatus();

                GUI.get().safeWork(getShell(), new SignInWorker());
            }
        });

        _controls.add(signInButton);
        _defaultControl = signInButton;
    }

    private Composite createCredentialComposite(Composite dialogContainer)
    {
        Composite credentialBlock = createContainer(dialogContainer, 2);

        // row 1 (only exists if this is a hybrid OpenId/Credential screen)

        if (_showOpenIdDialog) {
            Label credIntro = new Label(credentialBlock, SWT.NONE);
            credIntro.setText(OpenId.SERVICE_EXTERNAL_HINT);
            credIntro.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 2, 1));
        }

        createUserIDInputLabelAndText(credentialBlock);

        // row 2

        createPasswordLabelAndText(credentialBlock);

        // row 3 (empty row)
        new Label(credentialBlock, SWT.NONE);
        new Label(credentialBlock, SWT.NONE);

        // row 4 (state dependent)

        createBottomComposite(credentialBlock);

        credentialBlock.setTabList(new Control[]{_txtUserID, _txtPasswd});

        return credentialBlock;
    }

    // purely procedural, create a standard composite with a grid layout
    private static Composite createContainer(Control dialogArea, int columns)
    {
        final Composite container = new Composite((Composite) dialogArea, SWT.NONE);
        final GridLayout gridLayout = new GridLayout();
        gridLayout.marginTop = 5;
        gridLayout.marginBottom = 5;
        gridLayout.marginRight = 38;
        gridLayout.marginLeft = 45;
        gridLayout.horizontalSpacing = 10;

        gridLayout.numColumns = columns;
        container.setLayout(gridLayout);
        container.setLayoutData(new GridData(GridData.FILL_BOTH));

        return container;
    }

    private void createBottomComposite(Composite container)
    {
        Composite comp = new Composite(container, SWT.NONE);
        GridLayout glComp = new GridLayout(3, false);
        glComp.horizontalSpacing = 0;
        glComp.verticalSpacing = 12;
        glComp.marginHeight = 0;
        glComp.marginWidth = 0;
        comp.setLayout(glComp);
        comp.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));

        _compStack = new Composite(comp, SWT.NONE);
        _compStack.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 3, 1));
        _compStack.setSize(306, 28);
        _compStack.setLayout(_layoutStack);

        _compForgotPassword = new Composite(_compStack, SWT.NONE);
        GridLayout gl_compForgotPassword= new GridLayout(1, false);
        gl_compForgotPassword.marginHeight = 0;
        gl_compForgotPassword.marginWidth = 0;
        _compForgotPassword.setLayout(gl_compForgotPassword);

        Link linkForgotPassword = new Link(_compForgotPassword, SWT.NONE);
        linkForgotPassword.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 1, 1));
        linkForgotPassword.setToolTipText("");
        linkForgotPassword.setText("Forgot your password? <a>Click here to reset it.</a>");
        linkForgotPassword.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                GUIUtil.launch(WWW.PASSWORD_RESET_REQUEST_URL);
            }
        });

        _compBlank = new Composite(_compStack, SWT.NONE);
        _compBlank.setLayout(new GridLayout(1, false));

        _layoutStack.topControl = _compForgotPassword;

        createStatusComponents(comp);
    }

    static private boolean shouldAlwaysOnTop()
    {
        // On 10.5 the cocoasudo dialog goes behind the setup dialog if it's always on top.
        return !(OSUtil.isOSX() && System.getProperty("os.version").startsWith("10.5"));
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

    private void createUserIDInputLabelAndText(Composite container)
    {
        final Label emailAddressLabel = new Label(container, SWT.NONE);
        emailAddressLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        emailAddressLabel.setText(S.SETUP_USER_ID + ":");

        _txtUserID = new Text(container, SWT.BORDER);
        GridData gdEmail = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gdEmail.widthHint = 30;
        _txtUserID.setLayoutData(gdEmail);

        _txtUserID.addVerifyListener(new VerifyListener()
        {
            @Override
            public void verifyText(final VerifyEvent ev)
            {
                verify(GUIUtil.getNewText(_txtUserID.getText(), ev));
            }
        });
        _controls.add(_txtUserID);
    }

    private void createPasswordLabelAndText(Composite container)
    {
        final Label lblPass = new Label(container, SWT.NONE);
        lblPass.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblPass.setText(S.SETUP_PASSWD + ":");

        // N.B. because MacOSX can't handle password fields' verify events
        // correctly, we have to use ModifyListeners
        _txtPasswd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _txtPasswd.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                verify(null);
            }
        });
        _controls.add(_txtPasswd);
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
        clearInProgressStatus();
    }

    /**
     * Returns true if the email address looks like it might be email,
     * AND the password is non-empty.
     * If the email param is not provided, _txtUserID is checked.
     */
    private boolean isReady(@Nullable String email)
    {
        String trimmed = ((email == null) ? _txtUserID.getText() : email)
                .trim();

        if (trimmed.isEmpty() || _txtPasswd.getText().isEmpty()) {
            return false;
        } else return Util.isValidEmailAddress(trimmed);
    }

    private void setInProgressStatus()
    {
        _compSpin.start();
        setStatusImpl(S.SETUP_INSTALL_MESSAGE);
    }

    private void clearInProgressStatus()
    {
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
            _lblStatus.pack();
            _lblStatus.getParent().layout();
            _lblStatus.getShell().pack();
        }
    }

    private void createStatusComponents(Composite composite)
    {
        _lblStatus = new Label(composite, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        _compSpin = new CompSpin(composite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

        Label placeHolder = new Label(composite, SWT.NONE);
        placeHolder.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            _inProgress = true;

            _model.setUserID(_txtUserID.getText().trim());
            _model.setPassword(_txtPasswd.getText());
            _model.setSignInActor(new CredentialActor());

            setControlState(false);
            setInProgressStatus();

            GUI.get().safeWork(getShell(), new SignInWorker());

        } else {
            super.buttonPressed(buttonId);
        }
    }

    class SignInWorker implements ISWTWorker
    {
        @Override
        public void run() throws Exception
        {
            _model.doSignIn();
            _model.doInstall();

            setupShellExtension();
        }
        @Override
        public void error(Exception e)
        {
            l.error("Setup error", e);
            ErrorMessages.show(getShell(), e, formatExceptionMessage(e));
            clearInProgressStatus();

            _inProgress = false;

            getButton(OK_ID).setText("Try Again");
            setControlState(true);
        }

        @Override
        public void okay()
        {
            _okay = true;
            close();
        }

        // FIXME: if IdentityServlet threw an error return S.OPENID_AUTH_TIMEOUT;
        // (need to not throw ExBadCredential)
        protected String formatExceptionMessage(Exception e)
        {
            if (e instanceof ConnectException) return S.SETUP_ERR_CONN;
            else if (e instanceof ExBadCredential) return S.BAD_CREDENTIAL_CAP + ".";
            else if (e instanceof ExUIMessage) return e.getMessage();
            else if (e instanceof ExTimeout) return S.OPENID_AUTH_TIMEOUT;
            else if (e instanceof ExInternalError) return S.SERVER_INTERNAL_ERROR;
            else return S.SETUP_DEFAULT_SIGNIN_ERROR;
        }
    };

    private void setControlState(boolean enabled)
    {
        for (Control c : _controls) { c.setEnabled(enabled); }

        getButton(OK_ID).setEnabled(enabled && isReady(_txtUserID.getText()));

        _layoutStack.topControl = enabled ? _compForgotPassword : _compBlank;
        _compStack.layout();
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
    private boolean _showOpenIdDialog = true;//FIX THIS
    Control         _defaultControl;

    private CompSpin _compSpin;
    private Composite _compForgotPassword;
    private Composite _compBlank;
    private Composite _compStack;
    private Label _lblStatus;
    private final StackLayout _layoutStack = new StackLayout();

    private Text _txtUserID;
    private Text _txtPasswd;

    private boolean _okay;
    private boolean _inProgress;

    private SetupModel _model;
}