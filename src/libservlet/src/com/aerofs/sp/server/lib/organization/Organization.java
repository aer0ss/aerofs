package com.aerofs.sp.server.lib.organization;

import com.aerofs.lib.FullName;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase.SharedFolderInfo;
import com.aerofs.sp.server.lib.OrganizationDatabase.UserInfo;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
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

    private final OrgID _id;
    private final OrganizationDatabase _db;

    public static class Factory
    {
        private final Organization _default = create(OrgID.DEFAULT);

        private OrganizationDatabase _db;
        private User.Factory _factUser;

        @Inject
        public void inject(OrganizationDatabase db, User.Factory factUser)
        {
            _db = db;
            _factUser = factUser;
        }

        public Organization create(@Nonnull OrgID id)
        {
            return new Organization(_db, id);
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
        public Organization createNewOrganization(@Nonnull String name)
                throws SQLException, ExNoPerm, IOException, ExNotFound
        {
            while (true) {
                // Use a random ID only to prevent competitors from figuring out total number of
                // orgs. It is NOT a security measure.
                OrgID orgID = new OrgID(Util.rand().nextInt());
                try {
                    _db.add(orgID, name);
                    Organization org = create(orgID);
                    createNewTeamServerUser(org);
                    l.info("org #" + orgID + " created");
                    return org;
                } catch (ExAlreadyExist e) {
                    // Ideally we should use return value rather than exceptions on expected
                    // conditions.
                    l.info("duplicate ord id " + orgID + ". try a new one.");
                }
            }
        }

        private void createNewTeamServerUser(Organization org)
                throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
        {
            User tsUser = _factUser.create(org.id().toTeamServerUserID());

            // Use an invalid password hash to prevent attackers from logging in as Team Server
            // using _any_ password. Also see C.TEAM_SERVER_LOCAL_PASSWORD.
            tsUser.createNewUser(new byte[0], new FullName("Team", "Server"), org);
        }
    }

    private Organization(OrganizationDatabase db, OrgID id)
    {
        _id = id;
        _db = db;
    }

    public OrgID id()
    {
        return _id;
    }

    public boolean isDefault()
    {
        return _id.equals(OrgID.DEFAULT);
    }

    public String getName()
            throws ExNotFound, SQLException
    {
        return _db.getName(_id);
    }

    public void setName(String name)
            throws SQLException
    {
        _db.setName(_id, name);
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

    public static class UserListAndQueryCount
    {
        public final List<UserInfo> _userInfoList;
        public final int _count;

        public UserListAndQueryCount(List<UserInfo> userInfoList, int count)
        {
            _userInfoList = userInfoList;
            _count = count;
        }
    }

    /**
     * @param search Null or empty string when we want to find all the users.
     */
    public UserListAndQueryCount listUsersAuth(@Nullable String search,
            AuthorizationLevel authLevel, int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";
        checkOffset(offset);
        checkMaxResults(maxResults);

        assert offset >= 0;

        List<UserInfo> users;
        int count;
        if (search.isEmpty()) {
            users = _db.listUsersWithAuthorization(_id, offset, maxResults, authLevel);
            count = _db.listUsersWithAuthorizationCount(authLevel, _id);
        }
        else {
            assert !search.isEmpty();
            users = _db.searchUsersWithAuthorization(
                    _id, offset, maxResults, authLevel, search);
            count = _db.searchUsersWithAuthorizationCount(authLevel, _id, search);
        }
        return new UserListAndQueryCount(users, count);
    }

    public int totalUserCount() throws SQLException
    {
        return _db.listUsersCount(_id);
    }

    public int totalUserCount(AuthorizationLevel authLevel)
            throws SQLException
    {
        return _db.listUsersWithAuthorizationCount(authLevel, _id);
    }

    public UserListAndQueryCount listUsers(@Nullable String search, int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        if (search == null) search = "";
        checkOffset(offset);
        checkMaxResults(maxResults);

        assert offset >= 0;

        List<UserInfo> users;
        int count;
        if (search.isEmpty()) {
            users = _db.listUsers(_id, offset, maxResults);
            count = _db.listUsersCount(_id);
        } else {
            assert !search.isEmpty();
            users = _db.searchUsers(_id, offset, maxResults, search);
            count = _db.searchUsersCount(_id, search);
        }
        return new UserListAndQueryCount(users, count);
    }

    private static void checkOffset(int offset)
            throws ExBadArgs
    {
        if (offset < 0) throw new ExBadArgs("offset is negative");
    }

    // To avoid DoS attacks, do not permit listUsers queries to exceed 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    private static void checkMaxResults(int maxResults)
            throws ExBadArgs
    {
        if (maxResults > ABSOLUTE_MAX_RESULTS) throw new ExBadArgs("maxResults is too big");
        else if (maxResults < 0) throw new ExBadArgs("maxResults is a negative number");
    }

    public int countSharedFolders()
            throws SQLException
    {
        return _db.countSharedFolders(_id);
    }

    /**
     * TODO (WW) return a collection of SharedFolder objects instead.
     */
    public Collection<SharedFolderInfo> listSharedFolders(int maxResults, int offset)
            throws SQLException
    {
        return _db.listSharedFolders(_id, maxResults, offset);
    }

}
