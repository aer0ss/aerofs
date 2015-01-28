/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.acl.Permissions;
import com.aerofs.gui.sharing.Subject.Group;
import com.aerofs.gui.sharing.Subject.User;
import com.aerofs.sp.common.SharedFolderState;
import org.eclipse.swt.graphics.Image;

import javax.annotation.Nullable;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.hash;

public interface SharedFolderMember
{
    public Subject getSubject();

    default public String getLabel()
    {
        return getSubject().getLabel();
    }

    default public String getDescription()
    {
        return getSubject().getDescription();
    }

    default public Image getImage()
    {
        return getSubject().getImage();
    }

    public Permissions getPermissions();

    public SharedFolderState getState();

    // returns null when the member has no parents
    public @Nullable SharedFolderMember getParent();

    public static interface CanSetPermissions extends SharedFolderMember
    {
        public void setPermissions(Permissions permissions);
    }

    public static class UserPermissionsAndState implements CanSetPermissions
    {
        public final User               _user;
        public Permissions              _permissions;
        public final SharedFolderState  _state;

        public UserPermissionsAndState(User user, Permissions permissions, SharedFolderState state)
        {
            _user           = user;
            _permissions    = permissions;
            _state          = state;
        }

        @Override
        public Subject getSubject()
        {
            return _user;
        }

        @Override
        public Permissions getPermissions()
        {
            return _permissions;
        }

        @Override
        public SharedFolderState getState()
        {
            return _state;
        }

        @Override
        public @Nullable SharedFolderMember getParent()
        {
            return null;
        }

        @Override
        public void setPermissions(Permissions permissions)
        {
            _permissions = permissions;
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o
                    || (o instanceof UserPermissionsAndState
                                && _user.equals(((UserPermissionsAndState)o)._user)
                                && _permissions.equals(((UserPermissionsAndState)o)._permissions)
                                && _state.equals(((UserPermissionsAndState)o)._state));
        }

        @Override
        public int hashCode()
        {
            return hash(_user, _permissions, _state);
        }
    }

    public static class GroupPermissions implements CanSetPermissions
    {
        public final Group                              _group;
        public Permissions                              _permissions;
        public final List<GroupPermissionUserAndState>  _children;

        public GroupPermissions(Group group, Permissions permissions)
        {
            _group          = group;
            _permissions    = permissions;
            _children       = newArrayList();
        }

        @Override
        public Subject getSubject()
        {
            return _group;
        }

        @Override
        public String getLabel()
        {
            return _group.getLabel() + " (" + _children.size() + ")";
        }

        @Override
        public Permissions getPermissions()
        {
            return _permissions;
        }

        @Override
        public SharedFolderState getState()
        {
            return SharedFolderState.JOINED;
        }

        @Override
        public @Nullable SharedFolderMember getParent()
        {
            return null;
        }

        @Override
        public void setPermissions(Permissions permissions)
        {
            _permissions = permissions;
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o
                    || (o instanceof GroupPermissions
                                && _group.equals(((GroupPermissions)o)._group)
                                && _permissions.equals(((GroupPermissions)o)._permissions));
        }

        @Override
        public int hashCode()
        {
            return hash(_group, _permissions);
        }
    }

    public static class GroupPermissionUserAndState implements SharedFolderMember
    {
        public final GroupPermissions   _parent;
        public final User               _user;
        public final SharedFolderState  _state;

        public GroupPermissionUserAndState(GroupPermissions parent, User user,
                SharedFolderState state)
        {
            _parent = parent;
            _user   = user;
            _state  = state;
        }

        @Override
        public Subject getSubject()
        {
            return _user;
        }

        @Override
        public Permissions getPermissions()
        {
            return _parent._permissions;
        }

        @Override
        public SharedFolderState getState()
        {
            return _state;
        }

        @Override
        public @Nullable SharedFolderMember getParent()
        {
            return _parent;
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o
                    || (o instanceof GroupPermissionUserAndState
                                && _parent.equals(((GroupPermissionUserAndState)o)._parent)
                                && _user.equals(((GroupPermissionUserAndState)o)._user)
                                && _state.equals(((GroupPermissionUserAndState)o)._state));
        }

        @Override
        public int hashCode()
        {
            return hash(_parent, _user, _state);
        }
    }
}
