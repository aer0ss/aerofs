/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.users.CompUserList;
import com.aerofs.gui.sharing.users.CompUserList.ILoadListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.ui.UIUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * The member user list in the Manage Shared Folder dialog
 */
class MemberList extends Composite
{
    private static final Logger l = Loggers.getLogger(MemberList.class);

    private static class DlgInvite extends AeroFSDialog
    {
        private final Path _path;

        public DlgInvite(Shell parent, Path path, String defaultName)
        {
            super(parent, "Invite Members to "
                    + Util.quote(UIUtil.sharedFolderName(path, defaultName)), true, false);
            _path = path;
        }

        @Override
        protected void open(Shell shell)
        {
            if (GUIUtil.isWindowBuilderPro()) {
                shell = new Shell(getParent(), getStyle());
            }

            shell.setLayout(new FillLayout(SWT.HORIZONTAL));

            CompInviteUsers.createForExistingSharedFolder(shell, _path);
        }
    }

    private final CompUserList _userList;
    private final Composite _btnBar;
    private final Button _btnInvite;
    private @Nullable Path _path;
    private boolean _iAmAdmin;      // whether the local user is an admin of the shared folder.

    MemberList(Composite composite, int style)
    {
        super(composite, style);

        GridLayout gl = new GridLayout(1, false);
        gl.marginBottom = 0;
        gl.marginTop = 0;
        gl.marginRight = 0;
        gl.marginLeft = 0;
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        setLayout(gl);

        Label lbl = new Label(this, SWT.NONE);
        lbl.setText("Members:");
        lbl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Composite c = DlgManageSharedFolders.newTableWrapper(this);
        c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        _userList = new CompUserList(c);
        _userList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        _btnBar = GUIUtil.newPackedButtonContainer(c);
        _btnBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));

        CompSpin compSpin = new CompSpin(_btnBar, SWT.NONE);
        _userList.setSpinner(compSpin);

        _btnInvite = GUIUtil.createButton(_btnBar, SWT.PUSH);
        _btnInvite.setText("Invite Others...");
        _btnInvite.setToolTipText("Invite another user to join the shared folder");
        _btnInvite.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                showInvitationDialog();
            }
        });
    }

    private void setVisible(Control c, boolean visible)
    {
        c.setVisible(visible);
        ((GridData) c.getLayoutData()).exclude = !visible;
    }

    void setFolder(@Nullable Path path)
    {
        _path = path;
        refreshAsync();
    }

    /**
     * Refresh the member list asynchronously.
     */
    private void refreshAsync()
    {
        boolean valid = _path != null;
        setVisible(_userList, valid);
        setVisible(_btnBar, valid);

        if (valid) {
            _userList.setLoadListener(new ILoadListener() {
                @Override
                public void loaded(int membersCount, @Nullable Permissions localUserPermissions)
                {
                    // gray out invite button when not admin, except on Team Server where
                    // the ACL check is slightly more complicated...
                    // FIXME: TS needs effective ACL
                    _iAmAdmin = L.isMultiuser() ||
                            (localUserPermissions != null
                                     && localUserPermissions.covers(Permission.MANAGE));
                    _btnInvite.setEnabled(_iAmAdmin);
                }
            });
            _userList.load(_path);
        }

        layout(true, true);
    }

    void showInvitationDialog()
    {
        if (_path == null) {
            l.error("invite to a null folder?");

        } else if (_iAmAdmin) {
            new DlgInvite(getShell(), _path, null).openDialog();
            refreshAsync();

        } else {
            l.warn(ObfuscatingFormatters.formatPathMessage("non admin user attempts to invite {}",
                    _path)._obfuscated);
        }
    }
}
