package com.aerofs.gui.misc;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import com.aerofs.lib.ThreadUtil;
import com.aerofs.base.id.UserID;
import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompEmailAddressTextBox;
import com.aerofs.gui.CompEmailAddressTextBox.IInputChangeListener;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIUtil;

public class DlgFolderlessInvite extends AeroFSDialog implements IInputChangeListener, ISWTWorker
{
    private static final Logger l = Util.l(DlgFolderlessInvite.class);

    private Button _btnOk;
    private CompEmailAddressTextBox _compAddresses;
    private Label _lblStatus;
    private List<UserID> _userIDs;

    public DlgFolderlessInvite(Shell parent)
    {
        super(parent, "Share the Love", false, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        shell.setSize(680, 303);

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        shell.setLayout(glShell);

        Label lblMsg = new Label(shell, SWT.NONE);
        lblMsg.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        lblMsg.setText(S.TYPE_EMAIL_ADDRESSES);

        _compAddresses = new CompEmailAddressTextBox(shell, SWT.NONE);
        GridData gd_composite = new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1);
        gd_composite.heightHint = 90;
        gd_composite.widthHint = 350;
        _compAddresses.setLayoutData(gd_composite);

        _compAddresses.addInputChangeListener(this);

        Composite composite = new Composite(shell, SWT.NONE);
        GridLayout gl_composite = new GridLayout(4, false);
        gl_composite.marginTop = GUIParam.MAJOR_SPACING - glShell.verticalSpacing;
        gl_composite.marginHeight = 0;
        gl_composite.marginWidth = 0;
        composite.setLayout(gl_composite);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        _compSpin = new CompSpin(composite, SWT.NONE);
        _compSpin.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        _lblStatus = new Label(composite, SWT.NONE);
        _lblStatus.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        Button btnCancel = new Button(composite, SWT.NONE);
        btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
        btnCancel.setText(" " + IDialogConstants.CANCEL_LABEL + " ");

        _btnOk = new Button(composite, SWT.NONE);
        _btnOk.setText("    " + IDialogConstants.OK_LABEL + "    ");
        _btnOk.setEnabled(false);
        _btnOk.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                _userIDs = _compAddresses.getValidUserIDs();
                assert _userIDs.size() > 0;

                enableAll(false);
                _compSpin.start();
                setStatusText(S.SENDING_INVITATION + "...", false);

                GUI.get().safeWork(getShell(), DlgFolderlessInvite.this);
            }
        });

        updateStatus();

        refreshQuota();
    }

    private void setStatusText(String msg, boolean packShell)
    {
        _lblStatus.setText(msg);
        _lblStatus.pack();
        _lblStatus.getParent().layout();
        if (packShell) getShell().pack();
    }

    private void refreshQuota()
    {
        ThreadUtil.startDaemonThread("refresh-quota", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
                    sp.signInRemote();
                    Cfg.db().set(Key.FOLDERLESS_INVITES, sp.getHeartInvitesQuota().getCount());

                    GUI.get().safeAsyncExec(getShell(), new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            updateStatus();
                        }
                    });
                } catch (Exception e) {
                    l.warn(Util.e(e));
                }
            }
        });
    }

    private void updateStatus()
    {
        assert GUI.get().isUIThread();

        Collection<UserID> userIDs = _compAddresses.getValidUserIDs();
        int invalid = _compAddresses.getInvalidUserIDCount();
        int left = Cfg.db().getInt(Key.FOLDERLESS_INVITES) - userIDs.size() - invalid;

        if (left < 0) {
            setStatusText("Remove " + -left + " address" + (left != -1 ? "es" : ""), true);
            _compSpin.error();
        } else {
            if (left > 0) {
                setStatusText(left + " invite" + (left != 1 ? "s" : "") + " left", false);
            } else {
                setStatusText("No invites left", false);
            }
            _compSpin.stop();
        }

        // do not change the size of the dialog otherwise it would grow as
        // the user types addresses
        //getShell().pack();

        _btnOk.setEnabled(left >= 0 && userIDs.size() > 0 && invalid == 0);
    }

    @Override
    public void inputChanged()
    {
        updateStatus();
    }

    private CompSpin _compSpin;

    private void enableAll(boolean b)
    {
        _compAddresses.setEnabled(b);
        _btnOk.setEnabled(b);
    }

    @Override
    public void run() throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.signInRemote();

        List<String> userIdStrings = Lists.newArrayListWithCapacity(_userIDs.size());
        for (UserID userId : _userIDs) userIdStrings.add(userId.toString());

        sp.inviteUser(userIdStrings);
        SVClient.sendEventAsync(Sv.PBSVEvent.Type.FOLDERLESS_INVITE_SENT, Integer.toString(
                _userIDs.size()));
    }

    @Override
    public void okay()
    {
        closeDialog();
        GUI.get().notify(MessageType.INFO, S.INVITATION_WAS_SENT
                                 + " Thank you for sharing the love!");

        int cur = Cfg.db().getInt(Key.FOLDERLESS_INVITES);

        try {
            Cfg.db().set(Key.FOLDERLESS_INVITES, (cur - _userIDs.size()));
        } catch (SQLException e) {
            l.warn("ignored: " + Util.e(e));
        }
    }

    @Override
    public void error(Exception e)
    {
        enableAll(true);
        _compSpin.error();
        setStatusText(S.COULDNT_SEND_INVITATION + " " + UIUtil.e2msg(e), true);
        l.warn("send invite: " + Util.e(e));
    }
}