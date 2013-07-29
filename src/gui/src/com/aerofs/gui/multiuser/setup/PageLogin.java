/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.controller.SetupModel;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.google.common.base.Objects;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import java.net.ConnectException;

public class PageLogin extends AbstractSetupPage
{
    private Logger l = Loggers.getLogger(PageLogin.class);

    private boolean _inProgress;

    private Composite   _compHeader;
    private Composite   _compContent;
    private Composite   _compButton;

    private Label       _lblTitle;
    private Label       _lblLogo;
    private Label       _lblMessage;
    private Label       _lblUserID;
    private Text        _txtUserID;
    private Label       _lblPasswd;
    private Text        _txtPasswd;
    private Link        _lnkPasswd;
    private Label       _lblDeviceName;
    private Text        _txtDeviceName;
    private CompSpin    _compSpin;
    private Button      _btnContinue;
    private Button      _btnQuit;

    public PageLogin(Composite parent)
    {
        super(parent, SWT.NONE);

        createPage();

        getShell().addListener(SWT.Close, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                if (_inProgress) event.doit = false;
            }
        });

        ModifyListener onTextChanged = new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                validateInput();
            }
        };

        _txtUserID.addModifyListener(onTextChanged);
        _txtPasswd.addModifyListener(onTextChanged);
        _txtDeviceName.addModifyListener(onTextChanged);

        _lnkPasswd.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                GUIUtil.launch(WWW.PASSWORD_RESET_REQUEST_URL.get());
            }
        });

        _btnQuit.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                traverse(SWT.TRAVERSE_PAGE_PREVIOUS);
            }
        });

        _btnContinue.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                doWork();
            }
        });
    }

    protected void createPage()
    {
        createHeader(this);
        createContent(this);
        createButtonBar(this);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);

        _compHeader.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _compContent.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        _compButton.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
    }

    protected void createHeader(Composite parent)
    {
        _compHeader = new Composite(parent, SWT.NONE);
        _compHeader.setBackgroundMode(SWT.INHERIT_FORCE);
        _compHeader.setBackground(SWTResourceManager.getColor(0xFF, 0xFF, 0xFF));

        _lblTitle = new Label(_compHeader, SWT.NONE);
        _lblTitle.setText(S.SETUP_TITLE);
        GUIUtil.changeFont(_lblTitle, 16, SWT.BOLD);

        // n.b. when rendering images on a label, setImage clears the alignment bits,
        //   hence we have to call setAlignment AFTER setImage to display image on the right
        _lblLogo = new Label(_compHeader, SWT.NONE);
        _lblLogo.setImage(Images.get(Images.IMG_SETUP));

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        _compHeader.setLayout(layout);

        GridData titleLayout = new GridData(SWT.LEFT, SWT.TOP, false, true);
        titleLayout.verticalIndent = 20;
        titleLayout.horizontalIndent = 20;
        _lblTitle.setLayoutData(titleLayout);
        _lblLogo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true));
    }

    protected void createContent(Composite parent)
    {
        _compContent = new Composite(parent, SWT.NONE);

        _lblMessage = new Label(_compContent, SWT.NONE);
        _lblMessage.setText(S.SETUP_MESSAGE);

        _lblUserID = new Label(_compContent, SWT.NONE);
        _lblUserID.setText(S.ADMIN_EMAIL + ':');

        _txtUserID = new Text(_compContent, SWT.BORDER);
        _txtUserID.setFocus();

        _lblPasswd = new Label(_compContent, SWT.NONE);
        _lblPasswd.setText(S.ADMIN_PASSWD + ':');

        _txtPasswd = new Text(_compContent, SWT.BORDER | SWT.PASSWORD);

        _lnkPasswd = new Link(_compContent, SWT.NONE);
        _lnkPasswd.setText(S.SETUP_LINK_FORGOT_PASSWD);

        _lblDeviceName = new Label(_compContent, SWT.NONE);
        _lblDeviceName.setText(S.SETUP_DEV_ALIAS + ':');

        _txtDeviceName = new Text(_compContent, SWT.BORDER);

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.horizontalSpacing = 10;
        layout.verticalSpacing = 10;
        _compContent.setLayout(layout);

        GridData messageLayoutData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        messageLayoutData.heightHint = 30;
        _lblMessage.setLayoutData(messageLayoutData);
        _lblUserID.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtUserID.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _lblPasswd.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtPasswd.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _lnkPasswd.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false, 2, 1));
        _lblDeviceName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtDeviceName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    protected void createButtonBar(Composite parent)
    {
        _compButton = new Composite(parent, SWT.NONE);

        _compSpin = new CompSpin(_compButton, SWT.NONE);

        _btnQuit = new Button(_compButton, SWT.NONE);
        _btnQuit.setText(S.BTN_QUIT);

        _btnContinue = new Button(_compButton, SWT.NONE);
        _btnContinue.setText(S.BTN_CONTINUE);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 0;
        layout.marginBottom = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.marginLeft = 0;
        layout.marginRight = 40;
        layout.center = true;
        _compButton.setLayout(layout);

        _btnQuit.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnContinue.setLayoutData(new RowData(100, SWT.DEFAULT));
    }

    @Override
    protected void readFromModel(SetupModel model)
    {
        // do not populate password from model
        _txtUserID.setText(Objects.firstNonNull(model.getUsername(), ""));
        _txtDeviceName.setText(Objects.firstNonNull(model.getDeviceName(), ""));
        validateInput();
    }

    @Override
    protected void writeToModel(SetupModel model)
    {
        model.setUserID(_txtUserID.getText().trim());
        model.setPassword(_txtPasswd.getText());
        model.setDeviceName(_txtDeviceName.getText().trim());
    }

    protected void validateInput()
    {
        boolean valid = isInputValid();
        _btnContinue.setEnabled(valid && !_inProgress);
        if (_btnContinue.getEnabled()) getShell().setDefaultButton(_btnContinue);
    }

    protected boolean isInputValid()
    {
        String userID = _txtUserID.getText().trim();
        String passwd = _txtPasswd.getText();
        String deviceName = _txtDeviceName.getText().trim();

        return !userID.isEmpty() && Util.isValidEmailAddress(userID)
                && !passwd.isEmpty() && !deviceName.isEmpty();
    }

    private void doWork()
    {
        setProgress(true);

        writeToModel(_model);

        GUI.get().safeWork(_btnContinue, new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                _model.doSignIn();
            }

            @Override
            public void okay()
            {
                setProgress(false);
                traverse(SWT.TRAVERSE_PAGE_NEXT);
            }

            @Override
            public void error(Exception e)
            {
                l.error("Setup error", e);
                GUI.get().show(getShell(), MessageType.ERROR, formatException(e));
                setProgress(false);
                _btnContinue.setText(S.SETUP_TRY_AGAIN);
            }

            private String formatException(Exception e)
            {
                if (e instanceof ConnectException) return S.SETUP_ERR_CONN;
                else if (e instanceof ExUIMessage) return e.getMessage();
                else if (e instanceof ExBadCredential) return S.BAD_CREDENTIAL_CAP + '.';
                else return "Sorry, " + ErrorMessages.e2msgNoBracketDeprecated(e) + '.';
            }
        });
    }

    private void setProgress(boolean inProgress)
    {
        _inProgress = inProgress;

        if (_inProgress) {
            _compSpin.start();
            setControlState(false);
        } else {
            _compSpin.stop();
            setControlState(true);
        }
    }

    private void setControlState(boolean enabled)
    {
        _txtUserID.setEnabled(enabled);
        _txtPasswd.setEnabled(enabled);
        _txtDeviceName.setEnabled(enabled);
        _btnQuit.setEnabled(enabled);
        _btnContinue.setEnabled(enabled);
        _lnkPasswd.setEnabled(enabled);
    }
}
