package com.aerofs.gui.sharing;

import com.aerofs.base.acl.Permissions;
import com.aerofs.ui.UIUtil;
import org.eclipse.swt.widgets.Shell;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.manage.CompManageUsers;
import com.aerofs.gui.sharing.manage.CompUserList.ILoadListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Composite;

import javax.annotation.Nullable;

public class DlgManageSharedFolder extends AeroFSDialog
{
    private final Path _path;
    private CompManageUsers _compManageUsers;

    /**
     * @param path the path to the folder to be shared, relative to the root anchor path.
     */
    public DlgManageSharedFolder(Shell parent, Path path)
    {
        super(parent, "Manage Shared Folder " + Util.quote(UIUtil.sharedFolderName(path, null)),
                false, true);
        _path = path;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        FillLayout fillLayout = new FillLayout(SWT.HORIZONTAL);
        fillLayout.marginWidth = 12;
        fillLayout.marginHeight = 12;
        shell.setLayout(fillLayout);

        TabFolder tabFolder = new TabFolder(shell, SWT.NONE);

        TabItem tbtmInvite = new TabItem(tabFolder, SWT.NONE);
        tbtmInvite.setText("Invite Others");

        Composite compInvite = CompInviteUsers.createForExistingSharedFolder(tabFolder, _path);
        tbtmInvite.setControl(compInvite);

        final TabItem tbtmManage = new TabItem(tabFolder, SWT.NONE);
        tbtmManage.setText("Members");

        _compManageUsers = new CompManageUsers(tabFolder, _path, new ILoadListener() {
            @Override
            public void loaded(int membersCounts, @Nullable Permissions localUserPermissions)
            {
                tbtmManage.setText("Members (" + membersCounts + ")");
            }
        });

        tbtmManage.setControl(_compManageUsers);
    }
}
