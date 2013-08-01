/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.error.ErrorMessages;
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
import org.eclipse.swt.widgets.Listener;
import org.slf4j.Logger;

import java.net.ConnectException;

public abstract class AbstractSignInPage extends AbstractSetupPage
{
    protected boolean   _inProgress;
    protected Button    _btnContinue;
    protected Button    _btnQuit;

    protected CompSpin  _compSpin;
    private Logger      l = Loggers.getLogger(PageOpenIdSignIn.class);

    public AbstractSignInPage(Composite parent)
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

    abstract protected Composite createContent(Composite parent);

    abstract protected boolean isInputValid();

    protected void setControlState(boolean enabled)
    {
        _btnQuit.setEnabled(enabled);
        _btnContinue.setEnabled(enabled);
    }

    private void createPage()
    {
        Composite header = createHeader(this);
        Composite content = createContent(this);
        Composite buttonBar = createButtonBar(this);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);

        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        content.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
    }

    private Composite createHeader(Composite parent)
    {
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setBackgroundMode(SWT.INHERIT_FORCE);
        composite.setBackground(SWTResourceManager.getColor(0xFF, 0xFF, 0xFF));

        Label title = new Label(composite, SWT.NONE);
        title.setText(S.SETUP_TITLE);
        GUIUtil.changeFont(title, 16, SWT.BOLD);

        // n.b. when rendering images on a label, setImage clears the alignment bits,
        //   hence we have to call setAlignment AFTER setImage to display image on the right
        Label logo = new Label(composite, SWT.NONE);
        logo.setImage(Images.get(Images.IMG_SETUP));

        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        composite.setLayout(layout);

        GridData titleLayout = new GridData(SWT.LEFT, SWT.TOP, false, true);
        titleLayout.verticalIndent = 20;
        titleLayout.horizontalIndent = 20;
        title.setLayoutData(titleLayout);
        logo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true));

        return composite;
    }

    private Composite createButtonBar(Composite parent)
    {
        Composite buttonBar = new Composite(parent, SWT.NONE);

        _compSpin = new CompSpin(buttonBar, SWT.NONE);

        _btnQuit = GUIUtil.createButton(buttonBar, SWT.NONE);
        _btnQuit.setText(S.BTN_QUIT);

        _btnContinue = GUIUtil.createButton(buttonBar, SWT.NONE);
        _btnContinue.setText(S.BTN_CONTINUE);

        RowLayout layout = new RowLayout(SWT.HORIZONTAL);
        layout.marginTop = 0;
        layout.marginBottom = GUIParam.SETUP_PAGE_MARGIN_HEIGHT;
        layout.marginLeft = 0;
        layout.marginRight = 40;
        layout.center = true;
        buttonBar.setLayout(layout);

        _btnQuit.setLayoutData(new RowData(100, SWT.DEFAULT));
        _btnContinue.setLayoutData(new RowData(100, SWT.DEFAULT));

        _btnContinue.setFocus();

        return buttonBar;
    }

    protected void validateInput()
    {
        boolean valid = isInputValid();
        _btnContinue.setEnabled(valid && !_inProgress);
        if (_btnContinue.getEnabled()) getShell().setDefaultButton(_btnContinue);
    }

    final protected void doWork()
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
}
