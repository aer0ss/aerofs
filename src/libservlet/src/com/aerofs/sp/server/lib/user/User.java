/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.base.id.DID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.DBSearchUtil;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class User
{
    private final static Logger l = Util.l(User.class);

    public static class Factory
    {
        private final UserDatabase _udb;
        private final OrganizationInvitationDatabase _odb;

        private final Device.Factory _factDevice;
        private final Organization.Factory _factOrg;
        private final OrganizationInvitation.Factory _factOrgInvite;
        private final SharedFolder.Factory _factSharedFolder;

        @Inject
        public Factory(UserDatabase udb, OrganizationInvitationDatabase odb, Device.Factory factDevice,
                Organization.Factory factOrg, OrganizationInvitation.Factory factOrgInvite,
                SharedFolder.Factory factSharedFolder)
        {
            _udb = udb;
            _odb = odb;
            _factDevice = factDevice;
            _factOrg = factOrg;
            _factOrgInvite = factOrgInvite;
            _factSharedFolder = factSharedFolder;
        }

        public User create(@Nonnull UserID id)
        {
            return new User(this, id);
        }

        public User createFromExternalID(@Nonnull String str)
        {
            return create(UserID.fromExternal(str));
        }
    }

    private final UserID _id;
    private final Factory _f;

    private User(Factory f, UserID id)
    {
        _f = f;
        _id = id;
    }

    public void throwIfNotAdmin()
            throws ExNoPerm, ExNotFound, SQLException
    {
        if (!getLevel().covers(AuthorizationLevel.ADMIN)) {
            throw new ExNoPerm("User " + id() + " does not have administrator privileges");
        }
    }

    @Override
    public int hashCode()
    {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && _id.equals(((User)o)._id));
    }

    @Override
    public String toString()
    {
        return "user " + _id.toString();
    }

    /**
     * Since the user ID can never be changed, unlike other accessors, this method doesn't follow
     * the "get*" naming convention
     */
    public UserID id()
    {
        return _id;
    }

    public boolean exists()
            throws SQLException
    {
        return _f._udb.hasUser(_id);
    }

    public void throwIfNotFound()
            throws ExNotFound, SQLException
    {
        if (!exists()) throw new ExNotFound("user " + this);
    }

    public Organization getOrganization()
            throws ExNotFound, SQLException
    {
        // Do not cache the created object in memory to avoid db/mem inconsistency
        return _f._factOrg.create(_f._udb.getOrganizationID(_id));
    }

    public FullName getFullName()
            throws ExNotFound, SQLException
    {
        return _f._udb.getFullName(_id);
    }

    /**
     * @return sha256(scrypt(p|u)|passwdSalt)
     */
    public byte[] getShaedSP()
            throws ExNotFound, SQLException
    {
        return _f._udb.getShaedSP(_id);
    }

    public boolean isVerified()
            throws ExNotFound, SQLException
    {
        return _f._udb.isVerified(_id);
    }

    public AuthorizationLevel getLevel()
            throws ExNotFound, SQLException
    {
        return _f._udb.getLevel(_id);
    }

    public boolean isAdmin()
            throws SQLException, ExNotFound
    {
        return getLevel().covers(AuthorizationLevel.ADMIN);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setLevel(AuthorizationLevel auth)
            throws SQLException
    {
        _f._udb.setLevel(_id, auth);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setVerified() throws SQLException
    {
        _f._udb.setVerified(_id);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(FullName fullName) throws SQLException
    {
        _f._udb.setName(_id, fullName);
    }

    public ImmutableList<Device> listDevices()
            throws SQLException, ExFormatError
    {
        ImmutableList.Builder<Device> builder = ImmutableList.builder();

        for (DID did : _f._udb.listDevices(id())) {
            builder.add(_f._factDevice.create(did));
        }

        return builder.build();
    }

    public DevicesAndQueryCount listDevices(String search, int maxResults, int offset)
            throws ExBadArgs, SQLException, ExFormatError
    {
        DBSearchUtil.throwOnInvalidOffset(offset);
        DBSearchUtil.throwOnInvalidMaxResults(maxResults);

        List<DID> devices;
        int count;

        if (search.isEmpty()) {
            devices = _f._udb.listDevices(_id, offset, maxResults);
            count = totalDeviceCount();
        } else {
            devices = _f._udb.searchDevices(_id, offset, maxResults, search);
            count = _f._udb.searchDecvicesCount(_id, search);
        }

        return new DevicesAndQueryCount(devices, count);
    }

    public int totalDeviceCount()
            throws SQLException
    {
        return _f._udb.listDevicesCount(_id);
    }

    public List<OrganizationInvitation> getOrganizationInvitations()
            throws SQLException, ExNotFound
    {
        Organization organization = getOrganization();

        List<OrganizationID> allInvitedOrganizations = _f._odb.getAllInvitedOrganizations(id());
        List<OrganizationInvitation> result = Lists.newLinkedList();

        for (OrganizationID orgID : allInvitedOrganizations) {
            if (organization.id().equals(orgID)) {
                continue;
            }

            OrganizationInvitation invite = _f._factOrgInvite.create(id(), orgID);
            result.add(invite);
        }

        return result;
    }

    /**
     * Add the user to the database
     * @throws ExAlreadyExist if the user ID already exists.
     */
    public void save(byte[] shaedSP, FullName fullName, Organization org)
            throws ExAlreadyExist, SQLException, IOException, ExNoPerm
    {
        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _f._udb.insertUser(_id, fullName, shaedSP, org.id(), AuthorizationLevel.USER);

        addRootStoreAndCheckForCollision();
    }

    /**
     * Add the root store for the user, to:
     *
     * 1. include the team server to the root store.
     * 2. avoid attackers hijacking existing users' root store with intentional store ID collisions.
     */
    private void addRootStoreAndCheckForCollision()
            throws SQLException, IOException, ExAlreadyExist, ExNoPerm
    {
        SharedFolder rootStore = _f._factSharedFolder.create(SID.rootSID(_id));

        if (rootStore.exists()) {
            /**
             * It is possible for a malicious user to create a shared folder whose SID collide with
             * the SID of somebody's root store. When a new user signs up we can check if such a
             * colliding folder exists and delete it to prevent malicious users from gaining access
             * to other users' root stores.
             *
             * NB: here we assume a malicious collision for eavesdropping intent however there is a
             * small but not necessarily negligible probability that real collision will occur. What
             * we should do in this case is left as an exercise to the reader.
             */
            l.warn("existing shared folder collides with root store id for " + this);
            rootStore.delete();
        }

        // Ignore the return value and do not publish verkehr notifications, as this newly added
        // user mustn't have any daemon running at this moment.
        try {
            rootStore.save("root store: " + _id, this);
        } catch (ExNotFound e) {
            // The method throws ExNotFound only if the store doesn't exist, which is guaranteed not
            // to happen by the above code.
            SystemUtil.fatal(e);
        }
    }

    /**
     * Attemp to sign in using the credential provided by the user.
     *
     * @throws ExBadCredential if the user doesn't exist of the credential is incorrect.
     */
    public void signIn(byte[] shaedSP)
            throws SQLException, ExBadCredential
    {
        try {
            if (!Arrays.equals(getShaedSP(), shaedSP)) {
                l.warn(this + ": bad password.");
                throw new ExBadCredential();
            }
        } catch (ExNotFound e) {
            // Throw a bad credential as opposed to a not found to prevent brute force guessing of
            // user IDs.
            l.warn(this + ": not found.");
            throw new ExBadCredential();
        }
    }

    /**
     * Add a new organization O, move the user to O, and set the user as O's admin.
     *
     * @throws ExNoPerm if the user is a non-admin in a non-default organization
     */
    public Set<UserID> addAndMoveToOrganization(String orgName)
            throws ExNoPerm, SQLException, ExNotFound, ExAlreadyExist, IOException
    {
        // TODO (WW) move permission check to the upper layer?

        // only users in the default organization or admins can add organizations.
        if (!getOrganization().isDefault() && getLevel() != AuthorizationLevel.ADMIN) {
            throw new ExNoPerm("you have no permission to create new teams");
        }

        Organization org = _f._factOrg.save(orgName);
        setLevel(AuthorizationLevel.ADMIN);
        return setOrganization(org);
    }

    /**
     * Move the user to a new organization, and adjust ACLs of shared folders for the team server.
     */
    public Set<UserID> setOrganization(Organization org)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        Collection<SharedFolder> sfs = getSharedFolders();

        Set<UserID> users = Sets.newHashSet();

        for (SharedFolder sf : sfs) users.addAll(sf.deleteTeamServerACL(this));

        _f._udb.setOrganizationID(_id, org.id());

        for (SharedFolder sf : sfs) users.addAll(sf.addTeamServerACL(this));

        return users;
    }

    public Collection<SharedFolder> getSharedFolders()
            throws SQLException
    {
        Collection<SID> sids = _f._udb.getSharedFolders(_id);
        List<SharedFolder> sfs = Lists.newArrayListWithCapacity(sids.size());
        for (SID sid : sids) {
            sfs.add(_f._factSharedFolder.create(sid));
        }
        return sfs;
    }

    public static class PendingSharedFolder
    {
        public final UserID _sharer;
        public final SharedFolder _sf;

        public PendingSharedFolder(UserID sharer, SharedFolder sf)
        {
            _sharer = sharer;
            _sf = sf;
        }
    }

    public Collection<PendingSharedFolder> getPendingSharedFolders()
            throws SQLException
    {
        Collection<UserDatabase.PendingSharedFolder> l = _f._udb.getPendingSharedFolders(_id);
        List<PendingSharedFolder> sfs = Lists.newArrayListWithCapacity(l.size());
        for (UserDatabase.PendingSharedFolder i : l) {
            sfs.add(new PendingSharedFolder(i._sharer, _f._factSharedFolder.create(i._sid)));
        }
        return sfs;
    }

    public long getACLEpoch() throws SQLException
    {
        return _f._udb.getACLEpoch(_id);
    }

    public long incrementACLEpoch() throws SQLException
    {
        return _f._udb.incrementACLEpoch(_id);
    }

    /**
     * generate a signup invitation code and add it to the database
     * @return the signup code
     * @pre the user doesn't exist
     */
    public String addSignUpInvitationCode(User inviter)
            throws SQLException
    {
        assert !exists();

        String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
        _f._udb.insertSignupCode(code, inviter.id(), _id);
        return code;
    }

    public int getSignUpInvitationsQuota()
            throws ExNotFound, SQLException
    {
        return _f._udb.getSignUpInvitationsQuota(_id);
    }

    public void setSignUpInvitationQuota(int quota)
            throws SQLException
    {
        _f._udb.setSignUpInvitationsQuota(_id, quota);
    }

    public boolean isInvitedToSignUp()
            throws SQLException
    {
        return _f._udb.isInvitedToSignUp(_id);
    }

    public class DevicesAndQueryCount
    {
        private final ImmutableList<Device> _devices;
        private final int _count;

        public DevicesAndQueryCount(Collection<DID> devices, int count)
        {
            Builder<Device> builder = ImmutableList.builder();
            for (DID did : devices) builder.add(_f._factDevice.create(did));
            _devices = builder.build();
            _count = count;
        }

        public ImmutableList<Device> devices()
        {
            return _devices;
        }

        public int count()
        {
            return _count;
        }
    }
}
