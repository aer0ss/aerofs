/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing.manage;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBSharedFolder.PBUserRoleAndState;
import com.aerofs.sp.common.SharedFolderState;

import javax.annotation.Nonnull;

import static com.aerofs.sp.common.SharedFolderState.JOINED;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.trim;

/**
 * This class is created to hold the data related to shared folder members for the GUI as well
 * as to provide member-related logic for GUI
 */
public class SharedFolderMember implements Comparable<SharedFolderMember>
{
    // the only reason to maintain a reference to the factory is so we can check if this is the
    // local user
    private final Factory _factory;

    public final UserID _userID;
    @Nonnull public final String _firstname;
    @Nonnull public final String _lastname;
    public final Role _role;
    public final SharedFolderState _state;

    SharedFolderMember(Factory factory, UserID userID, @Nonnull String firstname,
            @Nonnull String lastname, Role role, SharedFolderState state)
    {
        _factory = factory;
        _userID = userID;
        _firstname = firstname;
        _lastname = lastname;
        _role = role;
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

    private boolean hasName()
    {
        return !isBlank(getName());
    }

    private String getName()
    {
        return trim(trim(_firstname) + " " + trim(_lastname));
    }

    @Override
    public int compareTo(SharedFolderMember that)
    {
        return  (compareByIsLocalUser(this, that) != 0) ? compareByIsLocalUser(this, that) :
                (compareByState(this, that) != 0)       ? compareByState(this, that) :
                (compareByHavingNames(this, that) != 0) ? compareByHavingNames(this, that) :
                compareByLabel(this, that);
    }

    // local user < non-local users
    private int compareByIsLocalUser(SharedFolderMember a, SharedFolderMember b)
    {
        return compareHelper(a.isLocalUser(), b.isLocalUser());
    }

    // joined members < pending members | left members
    private int compareByState(SharedFolderMember a, SharedFolderMember b)
    {
        return compareHelper(a._state == JOINED, b._state == JOINED);
    }

    // members with names < members with only emails (hasn't signed up)
    private int compareByHavingNames(SharedFolderMember a, SharedFolderMember b)
    {
        return compareHelper(a.hasName(), b.hasName());
    }

    // alphabetical order of the label
    private int compareByLabel(SharedFolderMember a, SharedFolderMember b)
    {
        return a.getLabel().compareTo(b.getLabel());
    }

    // true < false
    private int compareHelper(boolean a, boolean b)
    {
        if (a && !b) return -1;
        else if (b && !a) return 1;
        else return 0;
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

        public SharedFolderMember fromPB(PBUserRoleAndState urs)
                throws ExBadArgs
        {
            String firstname = urs.getUser().getFirstName();
            String lastname = urs.getUser().getLastName();
            UserID userID = createUserID(urs.getUser().getUserEmail());
            Role role = Role.fromPB(urs.getRole());
            SharedFolderState state = SharedFolderState.fromPB(urs.getState());

            return new SharedFolderMember(this, userID, firstname, lastname, role, state);
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
