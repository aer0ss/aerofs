/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBSharedFolder.PBGroupPermissions;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.sp.common.SharedFolderState;

import static com.aerofs.base.acl.SubjectPermissions.getStringFromSubject;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.trim;

/**
 * This class is created to hold the data related to shared folder members for the GUI as well
 * as to provide member-related logic for GUI
 */
public abstract class SharedFolderMember
{
    public Permissions _permissions;
    public SharedFolderState _state;

    SharedFolderMember(Permissions permissions, SharedFolderState state)
    {
        _permissions = permissions;
        _state = state;
    }

    public abstract boolean isLocalUser();

    // string used for protobuf serialization
    public abstract String getSubject();

    // this method is exposed so that the comparator can compare by the member having a name or not.
    public abstract boolean hasName();

    // a short text to identify the member
    public abstract String getLabel();

    // a long text to describe the member in detail
    public abstract String getDescription();

    static class User extends SharedFolderMember
    {
        private final CfgLocalUser _localUser;

        public final UserID _userID;
        private final String _firstname;
        private final String _lastname;

        User(CfgLocalUser localUser, UserID userID, String firstName, String lastName,
                Permissions permissions, SharedFolderState state)
        {
            super(permissions, state);

            _localUser = localUser;
            _userID = userID;
            _firstname = firstName;
            _lastname = lastName;
        }

        @Override
        public boolean isLocalUser()
        {
            return _localUser.get().equals(_userID);
        }

        @Override
        public String getSubject()
        {
            return getStringFromSubject(_userID);
        }

        @Override
        public String getLabel()
        {
            return  isLocalUser() ? "me"
                    : hasName() ? getName()
                    : _userID.getString();
        }

        @Override
        public String getDescription()
        {
            return _userID.getString();
        }

        @Override
        public boolean hasName()
        {
            return !isBlank(getName());
        }

        private String getName()
        {
            return trim(trim(_firstname) + " " + trim(_lastname));
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o ||
                    (o instanceof User && _userID.equals(((User)o)._userID));
        }

        @Override
        public int hashCode()
        {
            return _userID.hashCode();
        }
    }

    static class Group extends SharedFolderMember
    {
        public final GroupID _groupID;
        private final String _name;

        Group(GroupID groupID, String name, Permissions permissions, SharedFolderState state)
        {
            super(permissions, state);

            _groupID = groupID;
            _name = name;
        }

        @Override
        public boolean isLocalUser()
        {
            return false;
        }

        @Override
        public String getSubject()
        {
            return getStringFromSubject(_groupID);
        }

        @Override
        public boolean hasName()
        {
            return true;
        }

        @Override
        public String getLabel()
        {
            return _name;
        }

        @Override
        public String getDescription()
        {
            return _name;
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || ((o instanceof Group) && ((Group)o)._groupID.equals(_groupID));
        }

        @Override
        public int hashCode()
        {
            return _groupID.hashCode();
        }
    }

    // the only reason to have a factory is so we can inject a CfgLocalUser and check if a shared
    // folder member is the local user.
    public static class Factory
    {
        private final CfgLocalUser _localUser;

        public Factory(CfgLocalUser localUser)
        {
            _localUser = localUser;
        }

        public User fromPB(PBUserPermissionsAndState pb)
                throws ExBadArgs
        {
            String firstname = pb.getUser().getFirstName();
            String lastname = pb.getUser().getLastName();
            UserID userID = createUserID(pb.getUser().getUserEmail());
            Permissions permissions = Permissions.fromPB(pb.getPermissions());
            SharedFolderState state = SharedFolderState.fromPB(pb.getState());

            return new User(_localUser, userID, firstname, lastname, permissions, state);
        }

        public Group fromPB(PBGroupPermissions pb)
                throws ExBadArgs
        {
            GroupID groupID = GroupID.fromExternal(pb.getGroup().getGroupId());
            String name = pb.getGroup().getCommonName();
            Permissions permissions = Permissions.fromPB(pb.getPermissions());

            // the state of a group is irrelevant.
            return new Group(groupID, name, permissions, SharedFolderState.JOINED);
        }

        // the purpose is to wrap ExEmptyEmailAddress and throw ExBadArgs instead
        private UserID createUserID(String email) throws ExBadArgs
        {
            try {
                return UserID.fromExternal(email);
            } catch (ExEmptyEmailAddress e) {
                throw new ExBadArgs("Invalid e-mail address.");
            }
        }
    }
}
