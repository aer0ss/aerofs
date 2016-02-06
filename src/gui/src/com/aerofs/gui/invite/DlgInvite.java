/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.invite;

import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.defects.Defects;
import com.aerofs.gui.*;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExAlreadyInvited;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.lib.Util.isValidEmailAddress;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static com.google.common.collect.Lists.newArrayList;

/**
 * This dialog is for inviting users to AeroFS and it is completely unrelated to inviting users to
 * a shared folder.
 */
public class DlgInvite extends AeroFSDialog
{
    public DlgInvite(Shell parent)
    {
        super(parent, S.INVITE_TITLE, false, true, true);
    }

    @Override
    protected void open(Shell shell)
    {
        new CompInvite(shell, SWT.NONE);

        shell.setLayout(new FillLayout(SWT.VERTICAL));
    }

    private class CompInvite extends Composite
    {
        private final ClickEvent INVITE_COWORKER_CLICKED
                = new ClickEvent(Action.INVITE_COWORKER, Source.DESKTOP_GUI);
        private final ClickEvent INVITE_COWORKER_SUCCEEDED
                = new ClickEvent(Action.INVITE_COWORKER_SUCCEEDED, Source.DESKTOP_GUI);
        private final ClickEvent INVITE_COWORKER_FAILED
                = new ClickEvent(Action.INVITE_COWORKER_FAILED, Source.DESKTOP_GUI);

        private final Composite _cmpInvite;
        private final Label     _lblInvite;
        private final Text      _txtInvite;

        private final Composite _cmpBottom;
        private final Label     _lblStatus;
        private final CompSpin  _cmpSpin;

        private final Composite _cmpButtons;
        private final Button    _btnClose;
        private final Button    _btnInvite;

        public CompInvite(Composite parent, int style)
        {
            super(parent, style);

            _cmpInvite  = new Composite(this, SWT.NONE);
            _lblInvite  = createLabel(_cmpInvite, SWT.WRAP);
            _txtInvite  = new Text(_cmpInvite, SWT.BORDER | SWT.SINGLE);

            _cmpBottom  = new Composite(this, SWT.NONE);
            _lblStatus  = createLabel(_cmpBottom, SWT.NONE);
            _cmpSpin    = new CompSpin(_cmpBottom, SWT.NONE);

            _cmpButtons = GUIUtil.newButtonContainer(_cmpBottom, false);
            _btnClose   = GUIUtil.createButton(_cmpButtons, SWT.PUSH);
            _btnInvite  = GUIUtil.createButton(_cmpButtons, SWT.PUSH);

            initializeControls();
            layoutControls();
        }

        private void initializeControls()
        {
            _lblInvite.setText(S.INVITE_LBL_INVITE);
            _txtInvite.setFocus();
            _btnClose.setText(IDialogConstants.CLOSE_LABEL);
            _btnInvite.setText(S.INVITE_BTN_INVITE);
            _btnInvite.setEnabled(false);
            getShell().setDefaultButton(_btnInvite);

            _txtInvite.addModifyListener(event ->
                    _btnInvite.setEnabled(isValidEmailAddress(getInput())));

            _btnClose.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    getShell().close();
                }
            });

            _btnInvite.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    UIGlobals.analytics().track(INVITE_COWORKER_CLICKED);

                    final String email = getInput();

                    setStatus(S.INVITE_STATUS_INVITING);
                    setInProgress(true);

                    GUI.get().safeWork(getShell(), new ISWTWorker()
                    {
                        @Override
                        public void run()
                                throws Exception
                        {
                            newMutualAuthClientFactory().create()
                                    .signInRemote()
                                    .inviteToOrganization(email);
                        }

                        @Override
                        public void okay()
                        {
                            UIGlobals.analytics().track(INVITE_COWORKER_SUCCEEDED);

                            Defects.newMetric("gui.invite.success")
                                    .sendAsync();

                            setStatus("Invitation sent.");
                            setInProgress(false);
                            _txtInvite.setText("");
                            _txtInvite.setFocus();
                        }

                        @Override
                        public void error(Exception e)
                        {
                            UIGlobals.analytics().track(INVITE_COWORKER_FAILED);

                            Defects.newMetric("gui.invite.failure")
                                    .setMessage(e.toString())
                                    .setException(e)
                                    .sendAsync();

                            ErrorMessages.show(getShell(), e, "Sorry, we failed to send the " +
                                            "invitation.",
                                    new ErrorMessage(ExAlreadyExist.class, "The user is " +
                                            "already a member of your organization."),
                                    new ErrorMessage(ExAlreadyInvited.class, "The user is " +
                                            "already invited to join your organization."),
                                    new ErrorMessage(ExNoPerm.class, e.getMessage()));

                            setStatus("");
                            setInProgress(false);
                            _txtInvite.setSelection(0, _txtInvite.getText().length());
                            _txtInvite.setFocus();
                        }
                    });
                }
            });
        }

        private void layoutControls()
        {
            GridLayout layout = new GridLayout();
            layout.marginWidth = GUIParam.MARGIN;
            layout.marginHeight = GUIParam.MARGIN;
            layout.verticalSpacing = GUIParam.MAJOR_SPACING;
            setLayout(layout);

            _cmpInvite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            ((GridData)_cmpInvite.getLayoutData()).widthHint = 350;
            ((GridData)_cmpInvite.getLayoutData()).heightHint = 100;
            _cmpBottom.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

            GridLayout inviteLayout = new GridLayout();
            inviteLayout.marginWidth = 0;
            inviteLayout.marginHeight = 0;
            inviteLayout.verticalSpacing = GUIParam.VERTICAL_SPACING;
            _cmpInvite.setLayout(inviteLayout);

            _lblInvite.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
            _txtInvite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

            GridLayout bottomLayout = new GridLayout(3, false);
            bottomLayout.marginWidth = 0;
            bottomLayout.marginHeight = 0;
            _cmpBottom.setLayout(bottomLayout);

            _lblStatus.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            _cmpSpin.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
            _cmpButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        }

        private void setStatus(String status)
        {
            _lblStatus.setText(status);
            _lblStatus.pack();
            _lblStatus.getParent().layout();
        }

        private void setInProgress(boolean isInProgress)
        {
            if (isInProgress) {
                _cmpSpin.start();
            } else  {
                _cmpSpin.stop();
            }

            for (Control control : newArrayList(_txtInvite, _btnInvite)) {
                control.setEnabled(!isInProgress);
            }
        }

        private String getInput()
        {
            return _txtInvite.getText().trim().toLowerCase();
        }
    }
}
