/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserPermissionsAndState;
import com.aerofs.sp.common.SharedFolderState;

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
        // the only reason to maintain a reference to the factory is so we can check if this is the
        // local user
        private final Factory _factory;

        public final UserID _userID;
        private final String _firstname;
        private final String _lastname;

        User(Factory factory, UserID userID, String firstName, String lastName,
                Permissions permissions, SharedFolderState state)
        {
            super(permissions, state);

            _factory = factory;
            _userID = userID;
            _firstname = firstName;
            _lastname = lastName;
        }

        @Override
        public boolean isLocalUser()
        {
            return _factory._localUser.get().equals(_userID);
        }

        @Override
        public String getSubject()
        {
            return _userID.getString();
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

    // the only reason to have a factory is so we can inject a CfgLocalUser and check if a shared
    // folder member is the local user.
    public static class Factory
    {
        private final CfgLocalUser _localUser;

        public Factory(CfgLocalUser localUser)
        {
            _localUser = localUser;
        }

        public User fromPB(PBUserPermissionsAndState urs)
                throws ExBadArgs
        {
            String firstname = urs.getUser().getFirstName();
            String lastname = urs.getUser().getLastName();
            UserID userID = createUserID(urs.getUser().getUserEmail());
            Permissions permissions = Permissions.fromPB(urs.getPermissions());
            SharedFolderState state = SharedFolderState.fromPB(urs.getState());

            return new User(this, userID, firstname, lastname, permissions, state);
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
