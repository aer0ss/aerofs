package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.members.SharedFolderMember.User;
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

    public RoleMenu(Control parent, Permissions selfPermissions, SharedFolderMember member)
    {
        _menu = new Menu(parent);

        if (shouldShowUpdateACLMenuItems(selfPermissions)) {
            for (final Permissions r : Permissions.ROLE_NAMES.keySet()) {
                if (!member._permissions.equals(r)) {
                    MenuItem mi = new MenuItem(_menu, SWT.PUSH);
                    mi.setText("Set as " + r.roleName());
                    mi.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e)
                        {
                            select(r);
                        }
                    });
                }
            }
            new MenuItem(_menu, SWT.SEPARATOR);
        }

        if (member instanceof User) {
            final UserID subject = ((User)member)._userID;

            MenuItem miEmail = new MenuItem(_menu, SWT.PUSH);
            miEmail.setText("Email User");
            miEmail.addSelectionListener(GUIUtil.createUrlLaunchListener(
                    "mailto:" + subject));
        }

        // TODO (AT): handle the logic for when it's not an user
        // N.B. this is fine for now because all members are users. When we implement groups,
        // we'll have to consider what to do with the label "Remove User" and the label to display
        if (shouldShowUpdateACLMenuItems(selfPermissions) && member instanceof User) {
            MenuItem miKickout = new MenuItem(_menu, SWT.PUSH);
            miKickout.setText("Remove User");
            miKickout.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    if (GUI.get().ask(_menu.getShell(), MessageType.QUESTION,
                            // The text should be consistent with the text in shared_folders.mako
                            "Are you sure you want to remove " + ((User) member)._userID +
                                    " from the shared folder?\n" +
                                    "\n" +
                                    "This will delete the folder from the person's computers." +
                                    " However, old content may be still accessible from the" +
                                    " person's sync history.")) {
                        select(null);
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
     * @param permissions set to null to remove the user
     */
    private void select(@Nullable Permissions permissions)
    {
        _menu.dispose();

        if (_listener != null) _listener.onRoleChangeSelected(permissions);
    }

    public static boolean hasContextMenu(SharedFolderMember member)
    {
        // we can always send e-mail, except for the local user
        return !member.isLocalUser();
    }

    /**
     * FIXME Edge case: Team Servers show the menu to update ACL even though the Team Server may
     * not necessarily have the permission to update the ACL.
     *
     * It occurs when the Team Server sees a particular shared folder because someone in the
     * organization is a member but none of the owners of the said shared folder is in the
     * organization.
     *
     * TODO: Team Servers should get "effective" ACLs from SP which would neatly solve this mess
     */
    private boolean shouldShowUpdateACLMenuItems(Permissions selfPermissions)
    {
        return L.isMultiuser()                                  // Team Server only
                || selfPermissions.covers(Permission.MANAGE);   // regular client
    }

    public void setRoleChangeListener(RoleChangeListener listener)
    {
        _listener = listener;
    }

    public interface RoleChangeListener
    {
        void onRoleChangeSelected(Permissions permissions);
    }
}
