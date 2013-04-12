package com.aerofs.gui.sharing;

import java.util.Collection;
import java.util.List;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.gui.GUIUtil;
import com.aerofs.base.acl.Role;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ex.ExNoStripeCustomerID;
import com.aerofs.lib.rocklog.EventProperty;
import com.aerofs.lib.rocklog.EventType;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.proto.Sp.PBAuthorizationLevel;
import com.aerofs.ui.UIUtil;
import org.slf4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.aerofs.gui.CompEmailAddressTextBox;
import com.aerofs.gui.CompEmailAddressTextBox.IInputChangeListener;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.base.acl.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.google.common.collect.Lists;

public class CompInviteUsers extends Composite implements IInputChangeListener
{
    private static final Logger l = Loggers.getLogger(CompInviteUsers.class);
    private final Button _btnOk;
    private final Text _txtNote;

    private final Path _path;
    private static final String CONVERT_TO_SHARED_FOLDER = "Converting to a shared folder...";
    private String _fromPerson;

    private final Label _lblStatus;
    private final CompSpin _compSpin;
    private final CompEmailAddressTextBox _compAddresses;
    private final boolean _newSharedFolder;

    static public CompInviteUsers createForExistingSharedFolder(Composite parent, Path path)
    {
        return new CompInviteUsers(parent, path, false);
    }

    static public CompInviteUsers createForNewSharedFolder(Composite parent, Path path)
    {
        return new CompInviteUsers(parent, path, true);
    }

    private CompInviteUsers(Composite parent, Path path, boolean newSharedFolder)
    {
        super(parent, SWT.NONE);
        _path = path;
        _newSharedFolder = newSharedFolder;

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        setLayout(glShell);

        Label lblTypeEmailAddresses = new Label(this, SWT.NONE);
        lblTypeEmailAddresses.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        lblTypeEmailAddresses.setText(S.TYPE_EMAIL_ADDRESSES);

        _compAddresses = new CompEmailAddressTextBox(this, SWT.NONE);
        GridData gd__compAddresses = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd__compAddresses.heightHint = 60;
        _compAddresses.setLayoutData(gd__compAddresses);
        _compAddresses.addInputChangeListener(this);

        Composite composite_1 = new Composite(this, SWT.NONE);
        composite_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        GridLayout gl_composite_1 = new GridLayout(1, false);
        gl_composite_1.marginTop = GUIParam.MAJOR_SPACING - glShell.verticalSpacing;
        gl_composite_1.marginWidth = 0;
        gl_composite_1.marginHeight = 0;
        composite_1.setLayout(gl_composite_1);

        Label lbl = new Label(composite_1, SWT.NONE);
        lbl.setAlignment(SWT.RIGHT);
        lbl.setText("Personal note (optional):");

        _txtNote = new Text(this, SWT.BORDER | SWT.WRAP | SWT.MULTI);
        GridData gdTextNote = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gdTextNote.heightHint = 60;
        _txtNote.setLayoutData(gdTextNote);

        Composite composite1 = new Composite(this, SWT.NONE);
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        glComposite.marginTop = GUIParam.MAJOR_SPACING - glShell.verticalSpacing;
        composite1.setLayout(glComposite);
        composite1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        _compSpin = new CompSpin(composite1, SWT.NONE);

        _lblStatus = new Label(composite1, SWT.NONE);
        GridData gridData = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
        gridData.widthHint = GUIUtil.getExtent(_lblStatus, CONVERT_TO_SHARED_FOLDER).x;
        _lblStatus.setLayoutData(gridData);

        Composite composite = new Composite(composite1, SWT.NONE);
        FillLayout fl = new FillLayout(SWT.HORIZONTAL);
        fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        composite.setLayout(fl);

        _btnOk = new Button(composite, SWT.NONE);
        _btnOk.setText(IDialogConstants.OK_LABEL);
        _btnOk.setEnabled(false);
        _btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                work();
            }
        });

        Button btnCancel = new Button(composite, SWT.NONE);
        btnCancel.setText(IDialogConstants.CANCEL_LABEL);
        btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });

        final Shell shell = getShell();

        new Thread(new Runnable() {
            @Override
            public void run()
            {
                SPBlockingClient sp = SPClientFactory.newBlockingClient(Cfg.user());
                try {
                    sp.signInRemote();
                    _fromPerson = sp.getUserPreferences(Cfg.did().toPB()).getFirstName();
                } catch (Exception e) {
                    l.warn("cannot load user name: " + e);
                    _fromPerson = Cfg.user().getString();
                }

                GUI.get().safeAsyncExec(shell, new Runnable() {
                    @Override
                    public void run()
                    {
                        setAsyncFields();
                    }
                });
            }
        }).start();
    }

    public static String getDefaultInvitationNote(String folderName, String fromPerson)
    {
        return "I'd like to share \"" + folderName + "\" with you.\r\n\r\n-- " + fromPerson;
    }

    private void setAsyncFields()
    {
        if (_fromPerson == null) return;

        String note = getDefaultInvitationNote(_path.last(), _fromPerson);
        _txtNote.setText(note);
        _txtNote.setEditable(true);

        updateStatus();
    }

    private void updateStatus()
    {
        Collection<UserID> userIDs = _compAddresses.getValidUserIDs();
        int invalid = _compAddresses.getInvalidUserIDCount();

        // do not change the size of the dialog otherwise it would grow as
        // the user types addresses
        //getShell().pack();

        _btnOk.setEnabled(_fromPerson != null && userIDs.size() > 0 && invalid == 0);
    }

    private void setStatusText(String msg)
    {
        _lblStatus.setText(msg);
        _lblStatus.pack();
        _lblStatus.getParent().layout();
    }

    private void enableAll(boolean b)
    {
        _compAddresses.setEnabled(b);
        _txtNote.setEnabled(b);
        _btnOk.setEnabled(b);
    }

    private void work()
    {
        _compSpin.start();

        if (_newSharedFolder) {
            setStatusText(CONVERT_TO_SHARED_FOLDER);
        } else {
            setStatusText(S.INVITING);
        }

        final List<UserID> subjects = _compAddresses.getValidUserIDs();
        final String note = _txtNote.getText().trim();

        enableAll(false);

        GUI.get().safeWork(getShell(), new ISWTWorker() {

            @Override
            public void error(Exception e)
            {
                enableAll(true);
                _compSpin.stop();
                setStatusText("");

                String msg;
                if (e instanceof ExChildAlreadyShared) {
                    msg = "You can't share a folder that contains a shared folder.";
                } else if (e instanceof ExParentAlreadyShared) {
                    msg = "You can't share a folder under an already shared folder.";
                } else if (e instanceof ExNoPerm) {
                    msg = "You don't have permission to invite users to this folder";
                } else if (e instanceof ExNoStripeCustomerID) {
                    msg = null;
                    showPaymentDialog();
                } else {
                    l.warn(Util.e(e));
                    msg = S.COULDNT_SEND_INVITATION + " " + S.TRY_AGAIN_LATER + "\n\n" +
                            "Error message: " + UIUtil.e2msgSentenceNoBracket(e);
                }

                if (msg != null) GUI.get().show(getShell(), MessageType.ERROR, msg);
            }

            @Override
            public void okay()
            {
                getShell().close();
                UI.get().notify(MessageType.INFO, S.INVITATION_WAS_SENT);
            }

            @Override
            public void run() throws Exception
            {
                RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
                try {
                    PBPath pbpath = _path.toPB();
                    List<PBSubjectRolePair> srps = Lists.newArrayList();
                    for (UserID subject : subjects) {
                        srps.add(new SubjectRolePair(subject, Role.EDITOR).toPB());
                    }
                    ritual.shareFolder(pbpath, srps, note);
                } finally {
                    ritual.close();
                }

                RockLog.newEvent(EventType.FOLDER_INVITE_SENT)
                        .addProperty(EventProperty.COUNT, Integer.toString(subjects.size()))
                        .sendAsync();
            }

            void showPaymentDialog()
            {
                if (isAdmin()) {
                    // Note: the following messages should be consistent with the messages in
                    // shared_folder.mako:credit_card_modal:html
                    if (GUI.get().ask(getShell(), MessageType.INFO,
                            "The free plan allows one external collaborator per shared" +
                            " folder. If you'd like to invite unlimited external" +
                            " collaborators, please upgrade to the paid plan" +
                            " ($10/team member/month).\n\n" +
                            "Do you want to upgrade now?",
                            "Upgrade Now", "Cancel")) {
                        GUIUtil.launch(WWW.UPGRADE_URL.get());
                    }
                } else {
                    // Note: the following messages should be consistent with the messages in
                    // shared_folder_modals.mako.
                    if (GUI.get().ask(getShell(), MessageType.INFO,
                            "To add more collaborators to this folder, a paid plan is" +
                            " required for your team. Please contact your team administrator to" +
                            " upgrade the plan.",
                            "Email Admin with Instructions...", "Close")) {
                        String subject =
                                "Upgrade our AeroFS plan";
                        String body =
                                "Hi,\n\nI would like to invite more external collaborators to a shared" +
                                " folder, which requires a paid AeroFS plan. Could we upgrade" +
                                " the plan for our team?" +
                                " We can upgrade through this link:\n\n" +
                                WWW.UPGRADE_URL.get() +
                                "\n\nThank you!";
                        String url = "mailto:?subject=" + Util.urlEncode(subject) + "&body=" +
                                Util.urlEncode(body);
                        GUIUtil.launch(url);
                    }
                }
            }

            boolean isAdmin()
            {
                SPBlockingClient sp = SPClientFactory.newBlockingClient(Cfg.user());
                try {
                    sp.signInRemote();
                    return sp.getAuthorizationLevel().getLevel() == PBAuthorizationLevel.ADMIN;
                } catch (Exception e) {
                    // In most common cases the user is an admin of his own team.
                    return true;
                }
            }
        });
    }

    @Override
    public void inputChanged()
    {
        updateStatus();
    }
}
