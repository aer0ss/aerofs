package com.aerofs.gui.sharing.manage;

import com.aerofs.base.acl.Role;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import javax.annotation.Nullable;

public class RoleMenu
{
    private final Menu _menu;

    private RoleChangeListener _listener;

    public RoleMenu(Control parent, Role selfRole, SharedFolderMember member)
    {
        _menu = new Menu(parent);

        final UserID subject = member._userID;
        Role subjectRole = member._role;

        if (shouldShowUpdateACLMenuItems(selfRole)) {
            if (subjectRole != Role.OWNER) {
                MenuItem miOwner = new MenuItem(_menu, SWT.PUSH);
                miOwner.setText("Set as Owner");
                miOwner.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        select(subject, Role.OWNER);
                    }
                });
            }

            if (subjectRole != Role.EDITOR) {
                MenuItem miEditor = new MenuItem(_menu, SWT.PUSH);
                miEditor.setText("Set as Editor");
                miEditor.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        select(subject, Role.EDITOR);
                    }
                });
            }

            if (subjectRole != Role.VIEWER) {
                MenuItem miViewer = new MenuItem(_menu, SWT.PUSH);
                miViewer.setText("Set as Viewer");
                miViewer.addSelectionListener(new SelectionAdapter()
                {
                    @Override
                    public void widgetSelected(SelectionEvent e)
                    {
                        select(subject, Role.VIEWER);
                    }
                });
            }

            new MenuItem(_menu, SWT.SEPARATOR);
        }

        MenuItem miEmail = new MenuItem(_menu, SWT.PUSH);
        miEmail.setText("Email User");
        miEmail.addSelectionListener(GUIUtil.createUrlLaunchListener(
                "mailto:" + subject));

        if (shouldShowUpdateACLMenuItems(selfRole)) {
            MenuItem miKickout = new MenuItem(_menu, SWT.PUSH);
            miKickout.setText("Remove User");
            miKickout.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    if (GUI.get().ask(_menu.getShell(), MessageType.QUESTION,
                            // The text should be consistent with the text in shared_folders.mako
                            "Are you sure you want to remove " + subject +
                                    " from the shared folder?\n" +
                                    "\n" +
                                    "This will delete the folder from the person's computers." +
                                    " However, old content may be still accessible from the" +
                                    " person's sync history.")) {
                        select(subject, null);
                    }
                }
            });
        }
    }

    /**
     * open the menu at the current cursor location
     */
    public void open()
    {
        _menu.setVisible(true);
    }

    /**
     * @param role set to null to remove the user
     */
    private void select(UserID subject, @Nullable Role role)
    {
        _menu.dispose();

        if (_listener != null) _listener.onRoleChangeSelected(subject, role);
    }

    public static boolean hasContextMenu(SharedFolderMember member)
    {
        // we can always send e-mail, except for the local user
        return !member.isLocalUser();
    }

    /**
     * FIXME Edge case: Team Servers show the menu to update ACL even though the team server may
     * not necessarily have the permission to update the ACL.
     *
     * It occurs when the team server sees a particular shared folder because someone in the
     * organization is a member but none of the owners of the said shared folder is in the
     * organization.
     *
     * TODO: team servers should get "effective" ACLs from SP which would neatly solve this mess
     */
    private boolean shouldShowUpdateACLMenuItems(Role selfRole)
    {
        return L.isMultiuser()              // team server only
                || selfRole == Role.OWNER;  // regular client
    }

    public void setRoleChangeListener(RoleChangeListener listener)
    {
        _listener = listener;
    }

    public interface RoleChangeListener
    {
        void onRoleChangeSelected(UserID subject, Role role);
    }
}
