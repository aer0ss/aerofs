/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExInternalError;
import com.aerofs.controller.InstallActor;
import com.aerofs.controller.SetupModel;
import com.aerofs.controller.SignInActor.OpenIdGUIActor;
import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
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
import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
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

        _model = new SetupModel()
                .setSignInActor(new OpenIdGUIActor())
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

        newShell.addListener(SWT.Show, new Listener()
        {
            @Override
            public void handleEvent(Event arg0)
            {
                if (!shouldAlwaysOnTop()) GUIUtil.forceActive(newShell);
            }
        });
    }

    @Override
    protected Point getInitialSize()
    {
        return new Point(400, 320);
    }

    @Override
    public boolean isCancelled()
    {
        return !_okay;
    }

    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite area = (Composite) super.createDialogArea(parent);

        Composite container = createContainer(area);
        container.setFocus();

        GridData containerLayout = new GridData(SWT.CENTER, SWT.CENTER, true, true);
        containerLayout.verticalIndent = GUIParam.MAJOR_SPACING;
        container.setLayoutData(containerLayout);

        setTitle("Setup " + L.product());
        setTitleImage(Images.get(Images.IMG_SETUP));

        return area;
    }

    private Composite createContainer(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NONE);

        Label lblWelcome = new Label(container, SWT.WRAP);
        lblWelcome.setText(S.OPENID_SETUP_MESSAGE + '.');

        _compStatus = createStatusComposite(container);
        _compStatus.setVisible(false);

        RowLayout layout = new RowLayout(SWT.VERTICAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.center = false;
        layout.pack = true;
        layout.spacing = GUIParam.MAJOR_SPACING;
        container.setLayout(layout);

        lblWelcome.setLayoutData(new RowData(300, SWT.DEFAULT));

        return container;
    }

    private Composite createStatusComposite(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);

        _lblStatus = new Label(composite, SWT.NONE);
        _lblStatus.setText(S.OPENID_AUTH_MESSAGE);

        _compSpin = new CompSpin(composite, SWT.NONE);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.center = true;
        layout.pack = true;
        layout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;

        composite.setLayout(layout);

        return composite;
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
        createButton(parent, OK_ID, "Sign In", true);
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        switch (buttonId) {
        case OK_ID:
            work();
            break;
        case DETAILS_ID:
            AbstractDlgSetupAdvanced advanced = new SingleuserDlgSetupAdvanced(getShell(),
                    _model.getDeviceName(), _model._localOptions._rootAnchorPath);

            if (advanced.open() != IDialogConstants.OK_ID) return;

            _model.setDeviceName(advanced.getDeviceName());
            _model._localOptions._rootAnchorPath = advanced.getAbsoluteRootAnchor();
            break;
        default:
            super.buttonPressed(buttonId);
        }
    }

    private void work()
    {
        setState(true, S.OPENID_AUTH_MESSAGE);

        GUI.get().safeWork(getShell(), new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                _model.doSignIn();

                GUI.get().asyncExec(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        setState(true, S.SETUP_INSTALL_MESSAGE);
                    }
                });

                _model.doInstall();

                setupShellExtension();
            }

            @Override
            public void error(Exception e)
            {
                l.error("Setup error", e);

                // TODO: Catch ExAlreadyExist and ExNoPerm here, and ask the user if he wants us to
                // move the anchor root. See CLISetup.java.
                ErrorMessages.show(getShell(), e, formatExceptionMessage(e));

                setState(false, "");
                getButton(OK_ID).setText(S.SETUP_TRY_AGAIN);
            }

            @Override
            public void okay()
            {
                _okay = true;
                close();
            }

            protected String formatExceptionMessage(Exception e)
            {
                if (e instanceof ConnectException) return S.SETUP_ERR_CONN;
                else if (e instanceof ExUIMessage) return e.getMessage();
                else if (e instanceof ExBadCredential) return S.OPENID_AUTH_TIMEOUT;
                else if (e instanceof ExInternalError) return S.SERVER_INTERNAL_ERROR;
                else return "Sorry, " + ErrorMessages.e2msgNoBracketDeprecated(e) + '.';
            }
        });
    }

    private void setState(boolean inProgress, String message)
    {
        _inProgress = inProgress;

        _compStatus.setVisible(!StringUtils.isBlank(message));
        _lblStatus.setText(message);

        if (inProgress) _compSpin.start();
        else _compSpin.stop();

        getButton(OK_ID).setEnabled(!inProgress);
        getButton(DETAILS_ID).setEnabled(!inProgress);

        getShell().layout(new Control[] { _lblStatus } );
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

    protected static final Logger l = Loggers.getLogger(DlgOpenIdSignIn.class);

    private Composite   _compStatus;
    private Label       _lblStatus;
    private CompSpin    _compSpin;

    private boolean _okay;
    private boolean _inProgress;

    private SetupModel _model;
}
