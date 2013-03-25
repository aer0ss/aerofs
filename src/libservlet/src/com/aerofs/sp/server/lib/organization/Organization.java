package com.aerofs.sp.server.lib.organization;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ex.ExNoAdmin;
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

        public Organization create(int id)
        {
            return create(new OrganizationID(id));
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
                OrganizationID orgID = new OrganizationID(Util.rand().nextInt());
                try {
                    _odb.insert(orgID);
                } catch (ExAlreadyExist e) {
                    // Ideally we should use return value rather than exceptions on expected
                    // conditions.
                    l.info("duplicate organization id " + orgID + ". trying a new one.");
                    continue;
                }

                Organization org = create(orgID);
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

    public void setStripeCustomerID(@Nonnull String stripeCustomerID)
            throws SQLException
    {
        _f._odb.setStripeCustomerID(_id, stripeCustomerID);
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

    public User getTeamServerUser()
    {
        return _f._factUser.create(id().toTeamServerUserID());
    }

    public int countUsers() throws SQLException
    {
        return _f._odb.countUsers(_id);
    }

    public int countUsersAtLevel(AuthorizationLevel authLevel)
            throws SQLException
    {
        return _f._odb.countUsersAtLevel(authLevel, _id);
    }

    public ImmutableList<User> listUsers(int maxResults, int offset)
            throws SQLException, ExBadArgs
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : _f._odb.listUsers(_id, offset, maxResults)) {
            builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }

    public void throwIfNoAdmin()
            throws SQLException, ExNoAdmin
    {
        if (countUsersAtLevel(AuthorizationLevel.ADMIN) == 0) {
            // There must be at least one admin for an non-empty organization
            throw new ExNoAdmin(this.toString());
        }
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
            builder.add(_f._factOrgInvite.create(_f._factUser.create(userID), this));
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
