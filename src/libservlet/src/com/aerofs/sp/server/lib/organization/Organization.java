package com.aerofs.sp.server.lib.organization;

import com.aerofs.base.Loggers;
import com.aerofs.base.ParamFactory;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.proto.Sp.PBTwoFactorEnforcementLevel;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.GroupDatabase;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
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

    public static enum TwoFactorEnforcementLevel {
        // Only append to this list.  If you change ordinals, you will
        // change the meaning of existing DBs.  That would be bad.
        DISALLOWED,
        OPT_IN,
        MANDATORY;

        public static TwoFactorEnforcementLevel valueOf(int i) {
            return TwoFactorEnforcementLevel.values()[i];
        }
        public static TwoFactorEnforcementLevel fromPB(PBTwoFactorEnforcementLevel pblevel) {
            return valueOf(pblevel.getNumber());
        }

        public PBTwoFactorEnforcementLevel toPB() {
            return PBTwoFactorEnforcementLevel.valueOf(ordinal());
        }
    }

    public static class Factory
    {
        private OrganizationDatabase _odb;
        private OrganizationInvitationDatabase _oidb;
        private User.Factory _factUser;
        private SharedFolder.Factory _factSharedFolder;
        private OrganizationInvitation.Factory _factOrgInvite;
        private Group.Factory _factGroup;
        private GroupDatabase _gdb;

        @Inject
        public void inject(
                OrganizationDatabase odb,
                OrganizationInvitationDatabase oidb,
                User.Factory factUser,
                SharedFolder.Factory factSharedFolder,
                OrganizationInvitation.Factory factOrgInvite,
                Group.Factory factGroup,
                GroupDatabase gdb)
        {
            _odb = odb;
            _oidb = oidb;
            _factUser = factUser;
            _factSharedFolder = factSharedFolder;
            _factOrgInvite = factOrgInvite;
            _factGroup = factGroup;
            _gdb = gdb;
        }

        @ParamFactory
        public Organization _create(String orgIdHex)
        {
            return create(OrganizationID.fromHexString(orgIdHex));
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
         * Add a new organization with a random org id and its Team Server account to the DB.
         */
        public Organization save()
                throws SQLException
        {
            while (true) {
                // Use a random ID only to prevent competitors from figuring out total number of
                // orgs. It is NOT a security measure.
                OrganizationID orgID = new OrganizationID(Util.rand().nextInt());
                try {
                    return save(orgID);
                } catch (ExAlreadyExist e) {
                    l.info("duplicate organization id " + orgID + ". trying a new one.");
                }
            }
        }

        /**
         * Add a new organization with the given org id and its Team Server account to the DB.
         * @throws ExAlreadyExist if there already is an organization with this org id.
         */
        public Organization save(OrganizationID orgID)
                throws SQLException, ExAlreadyExist
        {
            _odb.insert(orgID);
            Organization org = create(orgID);
            _factUser.saveTeamServerUser(org);
            l.info(org + " created");
            return org;
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

    public boolean exists()
            throws SQLException
    {
        return _f._odb.exists(_id);
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

    /**
     * @param quota New quota value in bytes, or null to remove quota
     */
    public void setQuotaPerUser(@Nullable Long quota) throws SQLException
    {
        _f._odb.setQuotaPerUser(_id, quota);
    }

    /**
     * @return Quota value in bytes, or null if no quota is set
     */
    public @Nullable Long getQuotaPerUser() throws SQLException, ExNotFound
    {
        return _f._odb.getQuotaPerUser(_id);
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

    public int countInternalUsers() throws SQLException
    {
        return _f._odb.countInternalUsers(_id);
    }

    public int countExternalUsers() throws SQLException
    {
        return countUsers() - countInternalUsers();
    }

    public int countUsersWithPrefix(String searchPrefix) throws SQLException
    {
        return _f._odb.countUsersWithPrefix(_id, searchPrefix);
    }

    public int countUsersAtLevel(AuthorizationLevel authLevel)
            throws SQLException
    {
        return _f._odb.countUsersAtLevel(authLevel, _id);
    }

    public ImmutableList<User> listUsers(int maxResults, int offset)
            throws SQLException
    {
        return listUsers(maxResults, offset, null);
    }

    public ImmutableList<User> listUsers(int maxResults, int offset, @Nullable String searchPrefix)
            throws SQLException
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : _f._odb.listUsers(_id, offset, maxResults, searchPrefix)) {
            builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }

    public List<User.EmailAndName> searchAutocompleteUsers(int maxResults, int offset, String searchPrefix)
            throws SQLException
    {
        return _f._odb.searchAutocompleteUsers(_id, offset, maxResults, searchPrefix);
    }

    public ImmutableList<Group> listGroups(int maxResults, int offset)
            throws SQLException
    {
        return listGroups(maxResults, offset, null);
    }

    public ImmutableList<Group> listGroups(int maxResults, int offset,
            @Nullable String searchPrefix)
            throws SQLException
    {
        Builder<Group> builder = ImmutableList.builder();

        for (GroupID gid : _f._gdb.listGroups(_id, offset, maxResults, searchPrefix)) {
            builder.add(_f._factGroup.create(gid));
        }
        return builder.build();
    }

    public ImmutableList<User> listWhitelistedUsers()
            throws SQLException
    {
        Builder<User> builder = ImmutableList.builder();
        for (UserID userID : _f._odb.listWhitelistedUsers(_id)) {
            builder.add(_f._factUser.create(userID));
        }
        return builder.build();
    }

    public void throwIfNoAdmin()
            throws SQLException, ExNoAdminOrOwner
    {
        if (countUsersAtLevel(AuthorizationLevel.ADMIN) == 0) {
            // There must be at least one admin for an non-empty organization
            throw new ExNoAdminOrOwner(this.toString());
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
     * @throws ExNotFound if the organization doesn't exist
     */
    public String getContactPhone() throws SQLException, ExNotFound
    {
        return _f._odb.getContactPhone(_id);
    }

    public void setTwoFactorEnforcementLevel(TwoFactorEnforcementLevel level)
            throws SQLException
    {
        _f._odb.setTwoFactorEnforcementLevel(_id, level);
    }

    public TwoFactorEnforcementLevel getTwoFactorEnforcementLevel()
            throws SQLException, ExNotFound
    {
        return _f._odb.getTwoFactorEnforcementLevel(_id);
    }

    public List<String> getExternalIDs()
            throws SQLException
    {
        List<String> externalStrings = Lists.newLinkedList();
        for (byte[] externalBytes : _f._gdb.getExternalIDs()) {
            externalStrings.add(new String(externalBytes));
        }
        return externalStrings;
    }

}
