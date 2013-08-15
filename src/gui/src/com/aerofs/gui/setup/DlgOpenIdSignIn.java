/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.InstallActor;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.GUIOpenId;
import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.singleuser.SingleuserDlgSetupAdvanced;
import com.aerofs.labeling.L;
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
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ConnectException;

import static org.eclipse.jface.dialogs.IDialogConstants.CANCEL_ID;
import static org.eclipse.jface.dialogs.IDialogConstants.CANCEL_LABEL;
import static org.eclipse.jface.dialogs.IDialogConstants.DETAILS_ID;
import static org.eclipse.jface.dialogs.IDialogConstants.OK_ID;

// FIXME: this class is a travesty. Refactoring needed:
//  - extract the entire stack for the signin button / status spinner
//    into a nested class with enable/disable state transitions
//  - make the entire bottom composite reusable, and use it from the other classes that
//    need this.
//  In general, this needs to be merged with DlgCredentialSignIn.
//
public class DlgOpenIdSignIn extends AeroFSTitleAreaDialog
{
    public DlgOpenIdSignIn(Shell parentShell)
            throws Exception
    {
        super(null, parentShell, false, shouldAlwaysOnTop(), false);

        GetSetupSettingsReply defaults = UIGlobals.controller().getSetupSettings();

        setTitleImage(Images.get(Images.IMG_SETUP));

        _model = new SetupModel()
                .setSignInActor(new GUIOpenId())
                .setInstallActor(new InstallActor.SingleUser());
        _model._localOptions._rootAnchorPath = defaults.getRootAnchor();
        _model.setDeviceName(defaults.getDeviceName());
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
     * Create contents of the dialog
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Control area = super.createDialogArea(parent);
        Composite container = createContainer(area, 2);

        createBottomComposite(container);

        setTitle("Setup " + L.product());

        container.setFocus();

        return area;
    }

    private Composite createContainer(Control dialogArea, int columns)
    {
        final Composite container = new Composite((Composite) dialogArea, SWT.NONE);

        final GridLayout gridLayout = new GridLayout();
        gridLayout.marginTop = 35;
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
        Label welcomeText = new Label(container, SWT.WRAP);
        welcomeText.setText("To begin using " + L.product()
                            + ", please sign in with your OpenId Provider:");

        GridData welcomeLayout = new GridData();
        welcomeLayout.widthHint = 300;
        welcomeText.setLayoutData(welcomeLayout);

        Composite signinComposite = new Composite(container, SWT.NONE);
        GridLayout glComp = new GridLayout(2, false);
        glComp.horizontalSpacing = 0;
        glComp.verticalSpacing = 12;
        glComp.marginHeight = 0;
        glComp.marginWidth = 0;
        signinComposite.setLayout(glComp);
        signinComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true, 2, 1));

        _compStack = new Composite(signinComposite, SWT.NONE);
        _compStack.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 2, 1));
        _compStack.setSize(306, 28);
        _compStack.setLayout(_layoutStack);

        _compSignInLink = new Composite(_compStack, SWT.NONE);
        GridLayout signinLayout= new GridLayout(2, false);
        signinLayout.marginHeight = 0;
        signinLayout.marginWidth = 0;
        _compSignInLink.setLayout(signinLayout);

        _signinButton = createButton(_compSignInLink, OK_ID, "Sign in with OpenId", true);

        _compBlank = new Composite(_compStack, SWT.NONE);
        _compBlank.setLayout(new GridLayout(1, false));

        _layoutStack.topControl = _compSignInLink;

        _lblStatus = new Label(signinComposite, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        _compSpin = new CompSpin(signinComposite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
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
        createButton(parent, DETAILS_ID, S.BTN_ADVANCED, false);

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

    private void setInProgressStatus()
    {
        _compSpin.start();
        setStatusImpl("", "Waiting for your OpenId provider...");
    }

    private void setErrorStatus(String error)
    {
        _compSpin.stop();
        setStatusImpl(error, "");
    }

    private void setStatusImpl(String error, String status)
    {
        if (!error.isEmpty()) GUI.get().show(getShell(), MessageType.ERROR, error);
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

    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.OK_ID) {
            work();
        } else {
            super.buttonPressed(buttonId);
        }
    }

    private void work()
    {
        _inProgress = true;

        setControlState(false);

        setInProgressStatus();

        GUI.get().safeWork(_signinButton, new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                setup();
            }

            @Override
            public void error(Exception e)
            {
                l.error("Setup error", e);
                String msg = null;
                if (e instanceof ConnectException) {
                    msg = "Sorry, couldn't connect to the server. Please try again later.";
                } else if (e instanceof ExUIMessage) {
                    msg = e.getMessage();
                } else if (e instanceof ExBadCredential) {
                    msg = S.BAD_CREDENTIAL_CAP + ".";
                }

                // TODO: Catch ExAlreadyExist and ExNoPerm here, and ask the user if he wants us to
                // move the anchor root. See CLISetup.java.

                if (msg == null) msg = "Sorry, " + ErrorMessages.e2msgNoBracketDeprecated(e) + '.';
                setErrorStatus(msg);

                _inProgress = false;

                setControlState(true);
            }

            @Override
            public void okay()
            {
                _okay = true;
                close();
            }
        });
    }

    private void setControlState(boolean enabled)
    {
        getButton(DETAILS_ID).setEnabled(enabled);

        if (!enabled) _layoutStack.topControl = _compBlank;
        else _layoutStack.topControl = _compSignInLink;
        _compStack.layout();
    }

    @Override
    public boolean isCancelled()
    {
        return !_okay;
    }

    /**
     * This method is called in a non-GUI thread
     */
    private void setup() throws Exception
    {
        _model.doSignIn();
        _model.doInstall();

        setupShellExtension();
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

    protected static final Logger l = Loggers.getLogger(DlgOpenIdSignIn.class);

    private CompSpin _compSpin;
    private Composite _compSignInLink;
    private Composite _compBlank;
    private Composite _compStack;
    private Label _lblStatus;
    private final StackLayout _layoutStack = new StackLayout();

    private boolean _okay;
    private boolean _inProgress;

    private SetupModel _model;
    private Button _signinButton;
}