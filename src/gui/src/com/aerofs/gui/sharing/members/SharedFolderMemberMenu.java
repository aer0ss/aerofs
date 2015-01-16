/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.members.SharedFolderMember.Group;
import com.aerofs.gui.sharing.members.SharedFolderMember.User;
import com.aerofs.labeling.L;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import javax.annotation.Nullable;

public abstract class SharedFolderMemberMenu
{
    protected Populator _populator;
    protected Menu _menu;

    private RoleChangeListener _listener;

    public static SharedFolderMemberMenu get(@Nullable Permissions localUserPermissions,
            SharedFolderMember member)
    {
        if (member.isLocalUser()) {
            return new NoMenu();
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
        if ((localUserPermissions != null && localUserPermissions.covers(Permission.MANAGE))
                || L.isMultiuser()) {
            if (member instanceof User) {
                return new ManageUserMenu((User)member);
            } else if (member instanceof Group) {
                return new ManageGroupMenu((Group)member);
            }
        } else if (member instanceof User) {
            return new EmailUserMenu((User)member);
        }

        return new NoMenu();
    }

    public void open(Control parent)
    {
        _menu = new Menu(parent);
        _menu.addMenuListener(new MenuAdapter()
        {
            @Override
            public void menuHidden(MenuEvent e)
            {
                GUI.get().asyncExec(_menu::dispose);
            }
        });

        _populator = new Populator();

        onOpen();

        _menu.setVisible(true);
    }

    protected abstract void onOpen();
    public abstract boolean hasContextMenu();

    public void setRoleChangeListener(RoleChangeListener listener)
    {
        _listener = listener;
    }

    protected void notifyRoleChangeListener(Permissions permissions)
    {
        if (_listener != null) {
            _listener.onRoleChangeSelected(permissions);
        }
    }

    public interface RoleChangeListener
    {
        /**
         * @param permissions null if the subject should be removed instead.
         */
        void onRoleChangeSelected(Permissions permissions);
    }

    private class Populator
    {
        private MenuItem createMenuItem(String text, Runnable onSelect)
        {
            MenuItem mi = new MenuItem(_menu, SWT.PUSH);
            mi.setText(text);
            mi.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    onSelect.run();
                }
            });
            return mi;
        }

        public void addSeparator()
        {
            new MenuItem(_menu, SWT.SEPARATOR);
        }

        public void addSetPermissions(SharedFolderMember member)
        {
            Permissions.ROLE_NAMES.keySet()
                    .stream()
                    .filter(role -> !member._permissions.equals(role))
                    .forEach(role -> createMenuItem("Set as " + role.roleName(),
                            () -> notifyRoleChangeListener(role)));
        }

        public void addEmailUser(User user)
        {
            createMenuItem("Email User", () -> GUIUtil.launch("mailto:" + user._userID.getString()));
        }

        public void addRemoveUser(User user)
        {
            addRemoveSubject("Remove User",
                    String.format("Are you sure you want to remove %s from the shared folder?\n\n" +
                            "This will delete the folder from that person's computer(s). " +
                            "However, old content may still be accessible from that person's " +
                            "sync history.", user.getDescription()));
        }

        public void addRemoveGroup(Group group)
        {
            addRemoveSubject("Remove Group",
                    String.format("Are you sure you want to remove %s from the shared folder?\n\n" +
                            "This will delete the folder from the computers of every person in " +
                            "the group. However, old content may still be accessible from each " +
                            "person's sync history.", group.getDescription()));
        }

        private void addRemoveSubject(String label, String message)
        {
            createMenuItem(label, () -> {
                if (GUI.get().ask(_menu.getShell(), MessageType.QUESTION, message)) {
                    notifyRoleChangeListener(null);
                }
            });
        }
    }

    // when there are no menu items to be shown
    static class NoMenu extends SharedFolderMemberMenu
    {
        @Override
        protected void onOpen()
        {

        }

        @Override
        public boolean hasContextMenu()
        {
            return false;
        }
    }

    // when the local user has permissions to manage and the selected member is an user
    static class ManageUserMenu extends SharedFolderMemberMenu
    {
        private final User _user;

        public ManageUserMenu(User user)
        {
            _user = user;
        }

        @Override
        protected void onOpen()
        {
            _populator.addSetPermissions(_user);
            _populator.addSeparator();
            _populator.addEmailUser(_user);
            _populator.addSeparator();
            _populator.addRemoveUser(_user);
        }

        @Override
        public boolean hasContextMenu()
        {
            return true;
        }
    }

    // when the local user has permissions to manage and the selected member is a group
    static class ManageGroupMenu extends SharedFolderMemberMenu
    {
        private final Group _group;

        public ManageGroupMenu(Group group)
        {
            _group = group;
        }

        @Override
        protected void onOpen()
        {
            _populator.addSetPermissions(_group);
            _populator.addSeparator();
            _populator.addRemoveGroup(_group);
        }

        @Override
        public boolean hasContextMenu()
        {
            return true;
        }
    }

    // when the local user has no permissions to manage but the selected member is an user
    static class EmailUserMenu extends SharedFolderMemberMenu
    {
        private final User _user;

        public EmailUserMenu(User user)
        {
            _user = user;
        }

        @Override
        protected void onOpen()
        {
            _populator.addEmailUser(_user);
        }

        @Override
        public boolean hasContextMenu()
        {
            return true;
        }
    }
}
