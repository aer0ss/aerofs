/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.setup;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.gui.AeroFSTitleAreaDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.ui.IUI.MessageType;
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

public abstract class AbstractDlgSetup extends AeroFSTitleAreaDialog
{
    protected static final Logger l = Util.l(AbstractDlgSetup.class);

    /**
     * This method is called in a non-GUI thread
     */
    abstract protected void setup(String userID, char[] passwd) throws Exception;

    /**
     * This method is called in the GUI thread, after setup succeeds.
     */
    abstract protected void postSetup();

    private String _absRootAnchor;
    private String _deviceName;

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

    protected AbstractDlgSetup(Shell parentShell)
            throws Exception
    {
        super(null, parentShell, false, shouldAlwaysOnTop(), false);

        GetSetupSettingsReply defaults = UI.controller().getSetupSettings();
        _absRootAnchor = defaults.getRootAnchor();
        _deviceName = defaults.getDeviceName();
    }

    protected String getAbsRootAnchor()
    {
        return _absRootAnchor;
    }

    protected String getDeviceName()
    {
        return _deviceName;
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

        // row 1

        createUserIDInputLabelAndText(container);

        // row 2

        createPasswordLabelAndText(container);

        // row 3

        new Label(container, SWT.NONE);
        new Label(container, SWT.NONE);

        // row 4

        createBottomComposite(container);

        // done with rows

        setTitle("Setup " + L.PRODUCT + (L.get().isStaging() ? " STAGING" : ""));

        _txtUserID.setFocus();

        container.setTabList(new Control[]{_txtUserID, _txtPasswd});

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
        createButton(parent, OK_ID, "Finish", true);
        createButton(parent, CANCEL_ID, CANCEL_LABEL, false);
        createButton(parent, DETAILS_ID, S.BTN_ADVANCED, false);

        getButton(OK_ID).setEnabled(false);
        getButton(DETAILS_ID).addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent arg0)
            {
                AbstractDlgSetupAdvanced advanced = createAdvancedSetupDialog();
                if (advanced.open() != IDialogConstants.OK_ID) return;
                processAdvancedSettings();
                _deviceName = advanced.getDeviceName();
                _absRootAnchor = advanced.getAbsoluteRootAnchor();
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
    }

    private void createPasswordLabelAndText(Composite container)
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
                verify(null);
            }
        });
    }

    // TODO (WW) use ModifyListener instead of VerifyListener throughout the codebase.
    private void verify(@Nullable String email)
    {
        boolean ready = isReady(email);

        getButton(OK_ID).setEnabled(ready);

        setOkayStatus();
    }

    private boolean isReady(@Nullable String email)
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

    private void setInProgressStatus()
    {
        _compSpin.start();
        setStatusImpl("", "Performing magic...");

    }

    private void setOkayStatus()
    {
        _compSpin.stop();
        setStatusImpl("", "");
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
            work();
        } else {
            super.buttonPressed(buttonId);
        }
    }

    private void work()
    {
        _inProgress = true;

        final String userID = _txtUserID.getText().trim();
        final char[] passwd = _txtPasswd.getText().toCharArray();

        setControlState(false);

        setInProgressStatus();

        GUI.get().safeWork(_txtUserID, new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                setup(userID, passwd);
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

                if (msg == null) msg = "Sorry, " + UIUtil.e2msgNoBracket(e) + '.';
                setErrorStatus(msg);

                _inProgress = false;

                getButton(OK_ID).setText("Try Again");
                setControlState(true);
            }

            @Override
            public void okay()
            {
                _okay = true;
                close();

                postSetup();
            }
        });
    }

    private void setControlState(boolean enabled)
    {
        getButton(OK_ID).setEnabled(enabled);
        getButton(CANCEL_ID).setEnabled(enabled);
        getButton(DETAILS_ID).setEnabled(enabled);

        _txtUserID.setEnabled(enabled);
        _txtPasswd.setEnabled(enabled);

        if (!enabled) _layoutStack.topControl = _compBlank;
        else _layoutStack.topControl = _compForgotPassword;
        _compStack.layout();
    }

    public boolean isCancelled()
    {
        return !_okay;
    }

    abstract protected AbstractDlgSetupAdvanced createAdvancedSetupDialog();
    abstract protected void processAdvancedSettings();

}
