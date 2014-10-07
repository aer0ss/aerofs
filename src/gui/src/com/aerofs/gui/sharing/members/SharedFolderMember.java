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

import javax.annotation.Nonnull;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.trim;

/**
 * This class is created to hold the data related to shared folder members for the GUI as well
 * as to provide member-related logic for GUI
 */
public class SharedFolderMember
{
    // the only reason to maintain a reference to the factory is so we can check if this is the
    // local user
    private final Factory _factory;

    @Nonnull public final UserID _userID; // used as a key, do not mutate
    @Nonnull public String _firstname;
    @Nonnull public String _lastname;
    @Nonnull public Permissions _permissions;
    @Nonnull public SharedFolderState _state;

    SharedFolderMember(Factory factory, @Nonnull UserID userID, @Nonnull String firstname,
            @Nonnull String lastname, @Nonnull Permissions permissions, @Nonnull SharedFolderState state)
    {
        _factory = factory;
        _userID = userID;
        _firstname = firstname;
        _lastname = lastname;
        _permissions = permissions;
        _state = state;
    }

    // returns the subject label
    public String getLabel()
    {
        return  isLocalUser() ? "me" :
                hasName() ? getName() :
                _userID.getString();
    }

    public boolean isLocalUser()
    {
        return _factory._localUser.get().equals(_userID);
    }

    protected boolean hasName()
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
                (o instanceof SharedFolderMember && _userID.equals(((SharedFolderMember)o)._userID));
    }

    @Override
    public int hashCode()
    {
        return _userID.hashCode();
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

        public SharedFolderMember create(@Nonnull UserID userID, @Nonnull String firstName,
                @Nonnull String lastName, @Nonnull Permissions permissions, @Nonnull SharedFolderState state)
        {
            return new SharedFolderMember(this, userID, firstName, lastName, permissions, state);
        }

        public SharedFolderMember fromPB(PBUserPermissionsAndState urs)
                throws ExBadArgs
        {
            String firstname = urs.getUser().getFirstName();
            String lastname = urs.getUser().getLastName();
            UserID userID = createUserID(urs.getUser().getUserEmail());
            Permissions permissions = Permissions.fromPB(urs.getPermissions());
            SharedFolderState state = SharedFolderState.fromPB(urs.getState());

            return new SharedFolderMember(this, userID, firstname, lastname, permissions, state);
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