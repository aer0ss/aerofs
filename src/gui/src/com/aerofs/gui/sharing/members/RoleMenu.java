package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.members.SharedFolderMember.Group;
import com.aerofs.gui.sharing.members.SharedFolderMember.User;
import com.aerofs.labeling.L;
import com.aerofs.lib.Util;
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

    public RoleMenu(Control parent, @Nullable Permissions selfPermissions,
            SharedFolderMember member)
    {
        // N.B. since the menu items are subject to many conditionals, the easiest way to manage
        // section separators is to aggressively add separators at the end of each section and then
        // simply remove the last menu item at the end if it's a separator.
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

            new MenuItem(_menu, SWT.SEPARATOR);
        }

        if (shouldShowUpdateACLMenuItems(selfPermissions)) {
            final String label;
            final String description;

            if (member instanceof User) {
                label = "Remove User";
                description = "This will delete the folder from the person's computers. " +
                        "However, old content may still be accessible from the person's sync" +
                        "history.";
            } else if (member instanceof Group) {
                label = "Remove Group";
                description = "This will delete the folder from the computers of every person " +
                        "in the group. However, old content may still be accessible from each " +
                        "person's sync history.";
            } else {
                // not supported
                return;
            }

            MenuItem miKickout = new MenuItem(_menu, SWT.PUSH);
            miKickout.setText(label);
            miKickout.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    if (GUI.get().ask(_menu.getShell(), MessageType.QUESTION,
                            "Are you sure you want to remove " + member.getDescription() +
                                    " from the shared folder?\n" +
                                    "\n" + description)) {
                        select(null);
                    }
                }
            });

            new MenuItem(_menu, SWT.SEPARATOR);
        }

        // the last menu item could be a separator because we aggressively add separators at the
        // end of each section. In which case, simply dispose it.
        if (_menu.getItemCount() > 0) {
            MenuItem lastItem = _menu.getItem(_menu.getItemCount() - 1);
            if (Util.test(lastItem.getStyle(), SWT.SEPARATOR)) {
                lastItem.dispose();
            }
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

    public static boolean hasContextMenu(SharedFolderMember member,
            @Nullable Permissions selfPermissions)
    {
        return !member.isLocalUser() &&
                (member instanceof User || shouldShowUpdateACLMenuItems(selfPermissions));
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
    private static boolean shouldShowUpdateACLMenuItems(@Nullable Permissions selfPermissions)
    {
        return L.isMultiuser()  // Team Server only
                                // regular client
                || (selfPermissions != null && selfPermissions.covers(Permission.MANAGE));
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
