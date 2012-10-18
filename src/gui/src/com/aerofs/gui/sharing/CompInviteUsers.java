package com.aerofs.gui.sharing;

import java.util.Collection;
import java.util.List;

import com.aerofs.ui.UIUtil;
import org.apache.log4j.Logger;
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
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.Path;
import com.aerofs.lib.Role;
import com.aerofs.lib.S;
import com.aerofs.lib.SubjectRolePair;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.aerofs.proto.Sv;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.google.common.collect.Lists;

public class CompInviteUsers extends Composite implements IInputChangeListener
{
    private static final Logger l = Util.l(CompInviteUsers.class);
    private final Button _btnOk;
    private final Text _txtNote;

    private final Path _path;
    private String _fromPerson;

    private final Label _lblStatus;
    private final CompSpin _compSpin;
    private final Button _btnCancel;
    private final Composite _composite;
    private final CompEmailAddressTextBox _compAddresses;
    private final Label lblTypeEmailAddresses;
    private final Composite composite_1;
    private final Composite composite;

    public CompInviteUsers(Composite parent, Path path)
    {
        super(parent, SWT.NONE);
        _path = path;

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        setLayout(glShell);

        lblTypeEmailAddresses = new Label(this, SWT.NONE);
        lblTypeEmailAddresses.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        lblTypeEmailAddresses.setText(S.TYPE_EMAIL_ADDRESSES);

        _compAddresses = new CompEmailAddressTextBox(this, SWT.NONE);
        GridData gd__compAddresses = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd__compAddresses.heightHint = 60;
        _compAddresses.setLayoutData(gd__compAddresses);
        _compAddresses.addInputChangeListener(this);

        composite_1 = new Composite(this, SWT.NONE);
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

        _composite = new Composite(this, SWT.NONE);
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        glComposite.marginTop = GUIParam.MAJOR_SPACING - glShell.verticalSpacing;
        _composite.setLayout(glComposite);
        _composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        _compSpin = new CompSpin(_composite, SWT.NONE);

        _lblStatus = new Label(_composite, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));

        composite = new Composite(_composite, SWT.NONE);
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

        _btnCancel = new Button(composite, SWT.NONE);
        _btnCancel.setText(IDialogConstants.CANCEL_LABEL);
        _btnCancel.addSelectionListener(new SelectionAdapter() {
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
                SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
                try {
                    sp.signInRemote();
                    _fromPerson = sp.getPreferences(Cfg.did().toPB()).getFirstName();
                } catch (Exception e) {
                    l.warn("cannot load user name: " + e);
                    _fromPerson = Cfg.user();
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
        Collection<String> addresses = _compAddresses.getValidAddresses();
        int invalid = _compAddresses.getInvalidAddressesCount();

        // do not change the size of the dialog otherwise it would grow as
        // the user types addresses
        //getShell().pack();

        _btnOk.setEnabled(_fromPerson != null && addresses.size() > 0 && invalid == 0);
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
        setStatusText(S.SENDING_INVITATION + "...");

        final List<String> subjects = _compAddresses.getValidAddresses();
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
                    // Note: we can get an ExNoPerm from Ritual or SP
                    // TODO (GS): This error message should be improved if ExNoPerm because user
                    // tried to invite outside the organization and org is closed sharing
                    // (in this case should say something about sharing outside not allowed)
                    msg = "You don't have permission to invite users to this folder";
                } else {
                    msg = S.COULDNT_SEND_INVITATION + " " + S.TRY_AGAIN_LATER + "\n\n" +
                            "Error message: " + UIUtil.e2msgSentenceNoBracket(e);
                }

                l.warn(Util.e(e));
                GUI.get().show(getShell(), MessageType.ERROR, msg);
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
                    for (String subject : subjects) {
                        srps.add(new SubjectRolePair(subject, Role.EDITOR).toPB());
                    }
                    ritual.shareFolder(Cfg.user(), pbpath, srps, note);
                } finally {
                    ritual.close();
                }

                SVClient.sendEventAsync(Sv.PBSVEvent.Type.INVITE_SENT,Integer.toString(subjects.size()));
            }
        });
    }

    @Override
    public void inputChanged()
    {
        updateStatus();
    }
}
