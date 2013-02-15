package com.aerofs.sp.server.lib.organization;

import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class Organization
{
    private final static Logger l = Util.l(Organization.class);

    public static class Factory
    {
        private final Organization _default = create(OrganizationID.DEFAULT);

        private OrganizationDatabase _db;
        private User.Factory _factUser;
        private SharedFolder.Factory _factSharedFolder;

        @Inject
        public void inject(OrganizationDatabase db, User.Factory factUser,
                SharedFolder.Factory factSharedFolder)
        {
            _db = db;
            _factUser = factUser;
            _factSharedFolder = factSharedFolder;
        }

        public Organization create(@Nonnull OrganizationID id)
        {
            return new Organization(this, id);
        }

        /**
         * @return the default organization
         */
        public Organization getDefault()
        {
            return _default;
        }

        /**
         * Add a new organization as well as its team server account to the DB
         */
        public Organization save(@Nonnull String organizationName, String organizationPhone,
                StripeCustomerID stripeCustomer)
                throws SQLException, ExNoPerm, IOException, ExNotFound
        {
            while (true) {
                // Use a random ID only to prevent competitors from figuring out total number of
                // orgs. It is NOT a security measure.
                OrganizationID organizationID = new OrganizationID(Util.rand().nextInt());
                try {
                    _db.insert(organizationID, organizationName, organizationPhone, stripeCustomer);
                    Organization org = create(organizationID);
                    saveTeamServerUser(org);
                    l.info(org + " created");
                    return org;
                } catch (ExAlreadyExist e) {
                    // Ideally we should use return value rather than exceptions on expected
                    // conditions.
                    l.info("duplicate organization id " + organizationID + ". trying a new one.");
                }
            }
        }

        private void saveTeamServerUser(Organization org)
                throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
        {
            User tsUser = _factUser.create(org.id().toTeamServerUserID());

            // Use an invalid password hash to prevent attackers from logging in as Team Server
            // using _any_ password. Also see C.MULTIUSER_LOCAL_PASSWORD.
            tsUser.save(new byte[0], new FullName("Team", "Server"), org);
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

    public boolean isDefault()
    {
        return _id.isDefault();
    }

    public String getName()
            throws ExNotFound, SQLException
    {
        return _f._db.getName(_id);
    }

    public void setName(String name)
            throws SQLException
    {
        _f._db.setName(_id, name);
    }

    public void setContactPhone(String contactPhone)
            throws SQLException
    {
        _f._db.setContactPhone(_id, contactPhone);
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
            userIDs = _f._db.listUsersWithAuthorization(_id, offset, maxResults, authLevel);
            count = _f._db.listUsersWithAuthorizationCount(authLevel, _id);
        } else {
            assert !search.isEmpty();
            userIDs = _f._db.searchUsersWithAuthorization(_id, offset, maxResults, authLevel, search);
            count = _f._db.searchUsersWithAuthorizationCount(authLevel, _id, search);
        }

        return new UsersAndQueryCount(userIDs, count);
    }

    public int totalUserCount() throws SQLException
    {
        return _f._db.listUsersCount(_id);
    }

    public int totalUserCount(AuthorizationLevel authLevel)
            throws SQLException
    {
        return _f._db.listUsersWithAuthorizationCount(authLevel, _id);
    }

    public UsersAndQueryCount listUsers(@Nullable String search, int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";

        List<UserID> userIDs;
        int count;
        if (search.isEmpty()) {
            userIDs = _f._db.listUsers(_id, offset, maxResults);
            count = totalUserCount();
        } else {
            userIDs = _f._db.searchUsers(_id, offset, maxResults, search);
            count = _f._db.searchUsersCount(_id, search);
        }
        return new UsersAndQueryCount(userIDs, count);
    }

    public int countSharedFolders()
            throws SQLException, ExBadArgs
    {
        return _f._db.countSharedFolders(_id);
    }

    public Collection<SharedFolder> listSharedFolders(int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        Builder<SharedFolder> builder = ImmutableList.builder();
        for (SID sid : _f._db.listSharedFolders(_id, maxResults, offset)) {
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
        return _f._db.getStripeCustomerIDNullable(_id);
    }

    /**
     * @return null if the organization doesn't have a phone number
     * @throws ExNotFound if the organization doesn't exist
     */
    @Nullable
    public String getContactPhoneNullable() throws SQLException, ExNotFound
    {
        return _f._db.getContactPhoneNullable(_id);
    }
}
