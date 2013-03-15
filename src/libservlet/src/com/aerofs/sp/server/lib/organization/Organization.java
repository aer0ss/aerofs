package com.aerofs.sp.server.lib.organization;

import com.aerofs.base.Loggers;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class Organization
{
    private final static Logger l = Loggers.getLogger(Organization.class);

    public static class Factory
    {
        private OrganizationDatabase _odb;
        private OrganizationInvitationDatabase _oidb;
        private User.Factory _factUser;
        private SharedFolder.Factory _factSharedFolder;
        private OrganizationInvitation.Factory _factOrgInvite;

        @Inject
        public void inject(OrganizationDatabase odb, OrganizationInvitationDatabase oidb,
                User.Factory factUser, SharedFolder.Factory factSharedFolder,
                OrganizationInvitation.Factory factOrgInvite)
        {
            _odb = odb;
            _oidb = oidb;
            _factUser = factUser;
            _factSharedFolder = factSharedFolder;
            _factOrgInvite = factOrgInvite;
        }

        public Organization create(@Nonnull OrganizationID id)
        {
            return new Organization(this, id);
        }

        /**
         * Add a new organization as well as its team server account to the DB
         */
        public Organization save()
                throws SQLException, ExAlreadyExist
        {
            while (true) {
                // Use a random ID only to prevent competitors from figuring out total number of
                // orgs. It is NOT a security measure.
                OrganizationID organizationID = new OrganizationID(Util.rand().nextInt());
                try {
                    _odb.insert(organizationID);
                } catch (ExAlreadyExist e) {
                    // Ideally we should use return value rather than exceptions on expected
                    // conditions.
                    l.info("duplicate organization id " + organizationID + ". trying a new one.");
                    continue;
                }

                Organization org = create(organizationID);
                _factUser.saveTeamServerUser(org);
                l.info(org + " created");
                return org;
            }
        }
    }

    private final OrganizationID _id;
    private final Factory _f;

    private Organization(Factory f, OrganizationID id)
    {
        _f = f;
        _id = id;
    }

    public OrganizationID id()
    {
        return _id;
    }

    public String getName()
            throws ExNotFound, SQLException
    {
        return _f._odb.getName(_id);
    }

    public void setName(String name)
            throws SQLException
    {
        _f._odb.setName(_id, name);
    }

    public void setContactPhone(String contactPhone)
            throws SQLException
    {
        _f._odb.setContactPhone(_id, contactPhone);
    }

    @Override
    public int hashCode()
    {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _id.equals(((Organization)o)._id));
    }

    @Override
    public String toString()
    {
        return "org #" + _id;
    }

    public class UsersAndQueryCount
    {
        private final ImmutableList<User> _users;
        private final int _count;

        public UsersAndQueryCount(Collection<UserID> userIDs, int count)
        {
            Builder<User> builder = ImmutableList.builder();
            for (UserID userID : userIDs) builder.add(_f._factUser.create(userID));
            _users = builder.build();
            _count = count;
        }

        public ImmutableList<User> users()
        {
            return _users;
        }

        public int count()
        {
            return _count;
        }
    }

    /**
     * @param search Null or empty string when we want to find all the users.
     */
    public UsersAndQueryCount listUsersAuth(@Nullable String search,
            AuthorizationLevel authLevel, int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";

        assert offset >= 0;

        List<UserID> userIDs;
        int count;
        if (search.isEmpty()) {
            userIDs = _f._odb.listUsersWithAuthorization(_id, offset, maxResults, authLevel);
            count = _f._odb.listUsersWithAuthorizationCount(authLevel, _id);
        } else {
            assert !search.isEmpty();
            userIDs = _f._odb.searchUsersWithAuthorization(_id, offset, maxResults, authLevel, search);
            count = _f._odb.searchUsersWithAuthorizationCount(authLevel, _id, search);
        }

        return new UsersAndQueryCount(userIDs, count);
    }

    public User getTeamServerUser()
    {
        return _f._factUser.create(id().toTeamServerUserID());
    }

    public int countUsers() throws SQLException
    {
        return _f._odb.countUsers(_id);
    }

    public int countUsers(AuthorizationLevel authLevel)
            throws SQLException
    {
        return _f._odb.listUsersWithAuthorizationCount(authLevel, _id);
    }

    public UsersAndQueryCount listUsers(@Nullable String search, int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";

        List<UserID> userIDs;
        int count;
        if (search.isEmpty()) {
            userIDs = _f._odb.listUsers(_id, offset, maxResults);
            count = countUsers();
        } else {
            userIDs = _f._odb.searchUsers(_id, offset, maxResults, search);
            count = _f._odb.searchUsersCount(_id, search);
        }
        return new UsersAndQueryCount(userIDs, count);
    }

    /**
     * @return the list of users who have been invited to the organization but haven't accepted the
     * invite.
     */
    public ImmutableCollection<OrganizationInvitation> getOrganizationInvitations()
            throws SQLException
    {
        ImmutableCollection.Builder<OrganizationInvitation> builder = ImmutableList.builder();
        for (UserID userID : _f._oidb.getInvitedUsers(_id)) {
            builder.add(_f._factOrgInvite.create(userID, _id));
        }
        return builder.build();
    }

    public int countOrganizationInvitations()
            throws SQLException
    {
        return _f._oidb.countInvitations(_id);
    }

    public int countSharedFolders()
            throws SQLException
    {
        return _f._odb.countSharedFolders(_id);
    }

    public Collection<SharedFolder> listSharedFolders(int maxResults, int offset)
            throws SQLException
    {
        Builder<SharedFolder> builder = ImmutableList.builder();
        for (SID sid : _f._odb.listSharedFolders(_id, maxResults, offset)) {
            builder.add(_f._factSharedFolder.create(sid));
        }
        return builder.build();
    }

    /**
     * Gets the Stripe Customer ID used to make Stripe API calls
     *
     * @return null if the organization doesn't have a Stripe Customer ID
     * @throws ExNotFound if the organization doesn't exist
     */
    @Nullable
    public StripeCustomerID getStripeCustomerIDNullable() throws SQLException, ExNotFound
    {
        return _f._odb.getStripeCustomerIDNullable(_id);
    }

    /**
     * @throws ExNotFound if the organization doesn't exist
     */
    public String getContactPhone() throws SQLException, ExNotFound
    {
        return _f._odb.getContactPhone(_id);
    }
}
