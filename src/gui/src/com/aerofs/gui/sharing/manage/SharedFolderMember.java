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
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

import static com.aerofs.sp.common.SharedFolderState.JOINED;
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

    @Nonnull public final UserID _userID;
    @Nonnull public final String _firstname;
    @Nonnull public final String _lastname;
    @Nonnull public final Role _role;
    @Nonnull public final SharedFolderState _state;

    SharedFolderMember(Factory factory, @Nonnull UserID userID, @Nonnull String firstname,
            @Nonnull String lastname, @Nonnull Role role, @Nonnull SharedFolderState state)
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

    public int compareToBySubject(SharedFolderMember that)
    {
        return aggregateComparisonResults(
                compareByIsLocalUser(this, that),
                compareByState(this, that),
                compareByHavingNames(this, that),
                compareByLabel(this, that));
    }

    public int compareToByRole(SharedFolderMember that)
    {
        return aggregateComparisonResults(
                compareByIsLocalUser(this, that),
                compareByState(this, that),
                compareByRole(this, that),
                compareByHavingNames(this, that),
                compareByLabel(this, that));
    }

    /**
     * This is intended to be used to aggregate comparison results. The intention is to use it like:
     *
     * aggregateComparisonResults(comparison1, comparison2, comparison3)
     *
     * which will, in effect, compare two objects using a series of comparisons where the earlier
     * comparisons have priorities over the later comparisons.
     */
    private int aggregateComparisonResults(int... values)
    {
        for (int value : values) if (value != 0) return value;
        return 0; // if we are here, all values must be 0
    }

    // local user < non-local users
    private static int compareByIsLocalUser(SharedFolderMember a, SharedFolderMember b)
    {
        return compareHelper(a.isLocalUser(), b.isLocalUser());
    }

    // joined members < pending members | left members
    private static int compareByState(SharedFolderMember a, SharedFolderMember b)
    {
        return compareHelper(a._state == JOINED, b._state == JOINED);
    }

    // members with names < members with only emails (hasn't signed up)
    private static int compareByHavingNames(SharedFolderMember a, SharedFolderMember b)
    {
        return compareHelper(a.hasName(), b.hasName());
    }

    // alphabetical order of the label
    private static int compareByLabel(SharedFolderMember a, SharedFolderMember b)
    {
        return a.getLabel().compareTo(b.getLabel());
    }

    // owner < editor < viewer
    private static int compareByRole(SharedFolderMember a, SharedFolderMember b)
    {
        return getRoleOrdinal(a._role) - getRoleOrdinal(b._role);
    }

    // order used in compareByRole
    private static int getRoleOrdinal(Role role)
    {
        switch (role) {
        case OWNER: return 0;
        case EDITOR: return 1;
        case VIEWER: return 2;
        // note that the input _should_ have already been sanitized at this point, that's why
        // we are throwing AssertionError instead of ExBadArgs.
        default: throw new AssertionError("Invalid role.");
        }
    }

    // true < false
    private static int compareHelper(boolean a, boolean b)
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

        public SharedFolderMember create(@Nonnull UserID userID, @Nonnull String firstName,
                @Nonnull String lastName, @Nonnull Role role, @Nonnull SharedFolderState state)
        {
            return new SharedFolderMember(this, userID, firstName, lastName, role, state);
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
