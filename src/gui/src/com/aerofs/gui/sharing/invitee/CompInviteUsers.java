/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.invitee;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.analytics.AnalyticsEvents.FolderInviteSentEvent;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.SharingRulesExceptionHandlers;
import com.aerofs.gui.sharing.invitee.InviteModel.Invitee;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.apache.commons.lang.StringUtils.isBlank;

public class CompInviteUsers extends Composite
{
    private static final Logger l = Loggers.getLogger(CompInviteUsers.class);

    private final Path                  _path;
    private final String                _name;
    private final boolean               _notifyOnSuccess;

    private final Composite             _cmpInvitees;
    private final Label                 _lblInvitees;
    private final StyledText            _txtInvitees;

    private final Composite             _cmpNote;
    private final Label                 _lblNote;
    private final Composite             _cmpRoles;
    private final Label                 _lblRoles;
    private final ComboRoles            _cmbRoles;
    private final Text                  _txtNote;

    private final Composite             _cmpBottom;
    private final CompSpin              _cmpSpin;
    private final Label                 _lblStatus;

    private final Composite             _cmpButtons;
    private final Button                _btnOK;
    private final Button                _btnCancel;

    private final InviteModel           _model;
    private final InviteeTextAdapter    _adapter;

    public CompInviteUsers(Composite parent, Path path, String name, boolean notifyOnSuccess)
    {
        super(parent, SWT.NONE);

        _path               = path;
        _name               = name;
        _notifyOnSuccess    = notifyOnSuccess;

        _cmpInvitees        = new Composite(this, SWT.NONE);
        _lblInvitees        = createLabel(_cmpInvitees, SWT.NONE);
        _txtInvitees        = new StyledText(_cmpInvitees, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);

        _cmpNote            = new Composite(this, SWT.NONE);
        _lblNote            = createLabel(_cmpNote, SWT.NONE);
        _cmpRoles           = new Composite(_cmpNote, SWT.NONE);
        _lblRoles           = createLabel(_cmpRoles, SWT.NONE);
        _cmbRoles           = new ComboRoles(GUIUtil.newPackedButtonContainer(_cmpRoles));
        _txtNote            = new Text(_cmpNote, SWT.MULTI | SWT.BORDER | SWT.WRAP);

        _cmpBottom          = new Composite(this, SWT.NONE);
        _cmpSpin            = new CompSpin(_cmpBottom, SWT.NONE);
        _lblStatus          = createLabel(_cmpBottom, SWT.NONE);
        _cmpButtons         = GUIUtil.newButtonContainer(_cmpBottom, false);
        _btnOK              = GUIUtil.createButton(_cmpButtons, SWT.PUSH);
        _btnCancel          = GUIUtil.createButton(_cmpButtons, SWT.PUSH);

        // N.B. we need this to be single threaded because concurrency is hard and not worth it in
        //   this case.
        ListeningExecutorService executor = listeningDecorator(newSingleThreadExecutor(
                runnable -> new Thread(runnable, "invite")));
        _model              = new InviteModel(new InjectableCfg(), newMutualAuthClientFactory(),
                UIGlobals.ritualClientProvider(), executor);
        _adapter            = new InviteeTextAdapter(_model, _txtInvitees);

        initializeControls();
        layoutControls();
        onLoad();
    }

    private void initializeControls()
    {
        _lblInvitees.setText(S.TYPE_EMAIL_ADDRESSES);
        _txtInvitees.setFocus();
        _txtInvitees.addModifyListener(e -> _btnOK.setEnabled(_adapter.isInputValid()));
        _lblRoles.setText(S.SHARE_INVITE_AS);
        _cmbRoles.selectRole(Permissions.EDITOR);
        _cmbRoles.setAlignment(SWT.CENTER);
        _lblNote.setText(S.SHARE_PERSONAL_NOTE);
        _btnOK.setText(IDialogConstants.OK_LABEL);
        _btnCancel.setText(IDialogConstants.CANCEL_LABEL);

        _btnOK.setEnabled(false);
        _btnOK.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                workImpl(_adapter.getValues(), _cmbRoles.getSelectedRole(),
                        _txtNote.getText().trim(), false);
            }
        });

        _btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });

        _adapter.installListeners();
    }

    private void layoutControls()
    {
        GridLayout layout = new GridLayout();
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        setLayout(layout);

        _cmpInvitees.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _cmpNote.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        _cmpBottom.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        GridLayout inviteesLayout = new GridLayout();
        inviteesLayout.marginWidth = 0;
        inviteesLayout.marginHeight = 0;
        _cmpInvitees.setLayout(inviteesLayout);

        _lblInvitees.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
        _txtInvitees.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        ((GridData)_txtInvitees.getLayoutData()).heightHint = 4 * _txtInvitees.getLineHeight(0) + 4;

        GridLayout noteLayout = new GridLayout(2, false);
        noteLayout.marginWidth = 0;
        noteLayout.marginHeight = 0;
        noteLayout.horizontalSpacing = 20;
        _cmpNote.setLayout(noteLayout);

        _lblNote.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        _cmpRoles.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _txtNote.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
        ((GridData)_txtNote.getLayoutData()).heightHint = 6 * _txtNote.getLineHeight() + 4;

        RowLayout rolesLayout = new RowLayout(SWT.HORIZONTAL);
        rolesLayout.marginTop = 0;
        rolesLayout.marginBottom = 0;
        rolesLayout.marginLeft = 0;
        rolesLayout.marginRight = 0;
        rolesLayout.pack = true;
        rolesLayout.center = true;
        rolesLayout.spacing = 5;
        _cmpRoles.setLayout(rolesLayout);

        GridLayout bottomLayout = new GridLayout(3, false);
        bottomLayout.marginWidth = 0;
        bottomLayout.marginHeight = 0;
        _cmpBottom.setLayout(bottomLayout);
        _cmpSpin.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        _lblStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        _cmpButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
    }

    private void onLoad()
    {
        addCallback(_model.getLocalUserFirstName(), new FutureCallback<String>()
        {
            @Override
            public void onSuccess(String firstName)
            {
                if (firstName == null) return;
                _txtNote.setText(getDefaultInvitationNote(_name, firstName));
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                l.warn("cannot load user name: " + throwable.toString());
                onSuccess(Cfg.user().getString());
            }
        }, new GUIExecutor(_txtNote));
    }

    public static String getDefaultInvitationNote(String folderName, String fromPerson)
    {
        return format("I'd like to share %s with you.\r\n\r\n-- %s",
                isBlank(folderName) ? "a folder" : Util.quote(folderName), fromPerson);
    }

    private void workImpl(java.util.List<Invitee> invitees, Permissions permissions, String notes,
            boolean suppressSFRWarnings)
    {
        setStatusText(S.INVITING);
        setInProgress(true);

        FutureCallback<Void> callback = new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v)
            {
                getShell().close();

                if (_notifyOnSuccess) {
                    UI.get().notify(MessageType.INFO, S.INVITATION_WAS_SENT);
                }

                UIGlobals.analytics().track(new FolderInviteSentEvent(invitees.size()));
            }

            @Override
            public void onFailure(Throwable t)
            {
                setStatusText("");
                setInProgress(false);

                if (SharingRulesExceptionHandlers.canHandle(t)) {
                    if (SharingRulesExceptionHandlers.promptUserToSuppressWarning(getShell(), t)) {
                        workImpl(invitees, permissions, notes, true);
                    }
                } else {
                    String exAlreadyExistMessage = "One or more invited people are already " +
                            "members of this folder.";
                    String exNoPermMessage = "You don't have permission to invite users to " +
                            "this folder.";

                    ErrorMessages.show(getShell(), t, L.brand() + " couldn't invite users.",
                            new ErrorMessage(ExAlreadyExist.class, exAlreadyExistMessage),
                            new ErrorMessage(ExChildAlreadyShared.class, S.CHILD_ALREADY_SHARED),
                            new ErrorMessage(ExParentAlreadyShared.class, S.PARENT_ALREADY_SHARED),
                            new ErrorMessage(ExNoPerm.class, exNoPermMessage));
                }
            }
        };

        addCallback(_model.invite(_path, invitees, permissions, notes, suppressSFRWarnings),
                callback, new GUIExecutor(getShell()));
    }

    private void setStatusText(String text)
    {
        _lblStatus.setText(text);
        _lblStatus.pack();
        _lblStatus.getParent().layout();
    }

    private void setInProgress(boolean inProgress)
    {
        if (inProgress) {
            _cmpSpin.start();
        } else {
            _cmpSpin.stop();
        }

        for (Control control : newArrayList(_txtInvitees, _txtNote, _btnOK, _cmbRoles)) {
            control.setEnabled(!inProgress);
        }
    }
}
