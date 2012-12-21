/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.setup;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import static org.eclipse.jface.dialogs.IDialogConstants.*;

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

import javax.annotation.Nullable;
import java.net.ConnectException;

public class DlgSetupCommon
{
    private static final Logger l = Util.l(DlgSetupCommon.class);

    static public interface IDlgSetupCommonCallbacks
    {
        void createButtonBarButton(Composite parent, int id, String text, boolean setDefault);

        Button getButtonBarButton(int id);

        /**
         * This method is called in the GUI thread, right before runSetup()
         */
        void preSetup();

        /**
         * This method is called in a non-GUI thread
         */
        void runSetup(String userID, char[] passwd) throws Exception;

        /**
         * This method is called in the GUI thread, after setup succeeds.
         */
        void postSetup();

        void closeDialog();

        Shell getShell();

        void setControlState(boolean enabled);

        void verify(@Nullable String newText);

        @Nullable Composite getBottomCompositeTopControlWhenEnabled();
    }

    private final IDlgSetupCommonCallbacks _callbacks;
    private String _absRootAnchor;
    private String _deviceName;

    private CompSpin _compSpin;
    private Label _lblError;
    private Composite _compForgotPassword;
    private Composite _compBlank;
    private Composite _compStack;
    private Label _lblStatus;
    private final StackLayout _layoutStack = new StackLayout();

    public Text getUserIDText()
    {
        return _txtUserID;
    }

    public Text getPasswordText()
    {
        return _txtPasswd;
    }

    private Text _txtUserID;
    private Text _txtPasswd;

    private boolean _okay;
    private boolean _inProgress;

    public DlgSetupCommon(IDlgSetupCommonCallbacks callbacks)
            throws Exception
    {
        _callbacks = callbacks;

        GetSetupSettingsReply defaults = UI.controller().getSetupSettings();
        _absRootAnchor = defaults.getRootAnchor();
        _deviceName = defaults.getDeviceName();
    }

    public String getAbsRootAnchor()
    {
        return _absRootAnchor;
    }

    public String getDeviceName()
    {
        return _deviceName;
    }

    public Composite getStackComposite()
    {
        return _compStack;
    }

    public void configureShell(final Shell newShell)
    {
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

    public Composite createContainer(Control dialogArea, int columns)
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

    public void createBottomComposite(Composite container)
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
                GUIUtil.launch(S.PASSWORD_RESET_REQUEST_URL);
            }
        });

        _compBlank = new Composite(_compStack, SWT.NONE);
        _compBlank.setLayout(new GridLayout(1, false));

        createStatusComponents(comp);
    }

    public void setBottomCompositeTopControlForEnabledState()
    {
        Composite top = _callbacks.getBottomCompositeTopControlWhenEnabled();
        _layoutStack.topControl = top == null ? _compForgotPassword : top;
        _compStack.layout();
    }

    static public boolean shouldAlwaysOnTop()
    {
        // On 10.5 the cocoasudo dialog goes behind the setup dialog if it's always on top.
        return !(OSUtil.isOSX() && System.getProperty("os.version").startsWith("10.5"));
    }

    public void createButtonBarButtons(final Composite parent)
    {
        _callbacks.createButtonBarButton(parent, OK_ID, "Finish", true);
        _callbacks.createButtonBarButton(parent, CANCEL_ID, CANCEL_LABEL, false);
        _callbacks.createButtonBarButton(parent, DETAILS_ID, S.BTN_ADVANCED, false);

        _callbacks.getButtonBarButton(OK_ID).setEnabled(false);
        _callbacks.getButtonBarButton(DETAILS_ID).addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                DlgSetupAdvanced advanced = new DlgSetupAdvanced(_callbacks.getShell(), _deviceName,
                        _absRootAnchor);
                if (advanced.open() != IDialogConstants.OK_ID) return;

                _deviceName = advanced.getDeviceName();
                _absRootAnchor = advanced.getAbsoluteRootAnchor();
            }
        });
    }

    public String getTitle()
    {
        return "Setup " + L.PRODUCT + " (beta) " + (Cfg.staging() ? "staging" : "");
    }

    public void createUserIDInputLabelAndText(Composite container)
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
                _callbacks.verify(GUIUtil.getNewText(_txtUserID.getText(), ev));
            }
        });
    }

    public void createPasswordLabelAndText(Composite container)
    {

        final Label lblPass = new Label(container, SWT.NONE);
        lblPass.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblPass.setText(S.SETUP_PASSWD + ":");

        // N.B. because MacOSX can't handle password fields' verify events
        // correctly, we have to use ModifyListeners
        _txtPasswd = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
                false, 1, 1));
        _txtPasswd.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent ev)
            {
                _callbacks.verify(null);
            }
        });
    }

    public boolean isReady(@Nullable String email)
    {
        if (email == null) email = _txtUserID.getText();
        String passwd = _txtPasswd.getText();
        email = email.trim();

        boolean ready = true;

        if (email.isEmpty()) {
            ready = false;
        } else if (!Util.isValidEmailAddress(email)) {
            ready = false;
        } else if (passwd.isEmpty()) {
            ready = false;
        }

        return ready;
    }

    public void setInProgressStatus()
    {
        _compSpin.start();
        setStatusImpl("", "Performing magic...");

    }

    public void setOkayStatus()
    {
        _compSpin.stop();
        setStatusImpl("", "");
    }

    public void setErrorStatus(String error)
    {
        _compSpin.error();
        setStatusImpl(error, "");
    }

    private void setStatusImpl(String error, String status)
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
            _lblStatus.getShell().pack();
        }
    }

    private void createStatusComponents(Composite composite)
    {
        _lblStatus = new Label(composite, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        _compSpin = new CompSpin(composite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

        _lblError = new Label(composite, SWT.NONE);
        _lblError.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
    }

    public void work()
    {
        _inProgress = true;

        final String userID = _txtUserID.getText().trim();
        final char[] passwd = _txtPasswd.getText().toCharArray();

        setControlState(false);

        setInProgressStatus();

        _callbacks.preSetup();

        GUI.get().safeWork(_txtUserID, new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                _callbacks.runSetup(userID, passwd);
            }

            @Override
            public void error(Exception e)
            {
                l.error("Setup error", e);
                String msg = null;
                if (e instanceof ConnectException) {
                    msg = "Sorry, couldn't connect to the server. Please try again later.";
                }

                // TODO: Catch ExAlreadyExist and ExNoPerm here, and ask the user if he wants us to
                // move the anchor root. See CLISetup.java.

                if (msg == null) msg = "Sorry, " + UIUtil.e2msgNoBracket(e) + '.';
                setErrorStatus(msg);

                _inProgress = false;

                _callbacks.getButtonBarButton(OK_ID).setText("Try Again");
                setControlState(true);
            }

            @Override
            public void okay()
            {
                _okay = true;
                _callbacks.closeDialog();

                _callbacks.postSetup();
            }
        });
    }

    private void setControlState(boolean enabled)
    {
        _callbacks.getButtonBarButton(OK_ID).setEnabled(enabled);
        _callbacks.getButtonBarButton(CANCEL_ID).setEnabled(enabled);
        _callbacks.getButtonBarButton(DETAILS_ID).setEnabled(enabled);

        _txtUserID.setEnabled(enabled);
        _txtPasswd.setEnabled(enabled);

        if (!enabled) {
            _layoutStack.topControl = _compBlank;
            _compStack.layout();
        } else {
            setBottomCompositeTopControlForEnabledState();
        }


        _callbacks.setControlState(enabled);
    }

    public boolean isCanelled()
    {
        return !_okay;
    }
}
