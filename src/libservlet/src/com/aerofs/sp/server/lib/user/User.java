/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.ex.ExLicenseLimit;
import com.aerofs.base.id.DID;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.lib.FullName;
import com.aerofs.lib.SystemUtil;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class User
{
    private final static Logger l = Loggers.getLogger(User.class);

    public static class Factory
    {
        private final UserDatabase _udb;
        private final OrganizationInvitationDatabase _odb;

        private final Device.Factory _factDevice;
        private final Organization.Factory _factOrg;
        private final OrganizationInvitation.Factory _factOrgInvite;
        private final SharedFolder.Factory _factSharedFolder;
        private final License _license;

        @Inject
        public Factory(UserDatabase udb, OrganizationInvitationDatabase odb,
                Device.Factory factDevice, Organization.Factory factOrg,
                OrganizationInvitation.Factory factOrgInvite, SharedFolder.Factory factSharedFolder,
                License license)
        {
            _udb = udb;
            _odb = odb;
            _factDevice = factDevice;
            _factOrg = factOrg;
            _factOrgInvite = factOrgInvite;
            _factSharedFolder = factSharedFolder;
            _license = license;
        }

        public User create(@Nonnull UserID id)
        {
            return new User(this, id);
        }

        public User createFromExternalID(@Nonnull String str)
                throws ExEmptyEmailAddress
        {
            return create(UserID.fromExternal(str));
        }

        /**
         * This method should be called by Organization.save() only
         */
        public User saveTeamServerUser(Organization org)
                throws SQLException, ExAlreadyExist
        {
            User tsUser = org.getTeamServerUser();

            // Use an invalid password hash to prevent attackers from logging in as Team Server
            // using _any_ password. Also see C.MULTIUSER_LOCAL_PASSWORD.
            tsUser.saveImpl(new byte[0], new FullName("Team", "Server"), org,
                    AuthorizationLevel.USER);
            return tsUser;
        }

        /**
         * @return whether the system has one or more users
         */
        public boolean hasUsers() throws SQLException
        {
            return _udb.hasUsers();
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
            throw new ExNoPerm(this + " does not have administrator privileges");
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
        return "user " + _id.getString();
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

    public long getSignupDate()
            throws SQLException, ExNotFound
    {
        return _f._udb.getSignupDate(_id);
    }

    public boolean isCredentialCorrect(byte[] shaedSP)
            throws ExNotFound, SQLException
    {
        return BaseSecUtil.constantTimeIsEqual(_f._udb.getShaedSP(_id), shaedSP);
    }

    public AuthorizationLevel getLevel()
            throws ExNotFound, SQLException
    {
        return _f._udb.getLevel(_id);
    }

    public boolean isWhitelisted()
            throws SQLException, ExNotFound
    {
        return _f._udb.isWhitelisted(_id);
    }

    public boolean isAdmin()
            throws SQLException, ExNotFound
    {
        return getLevel().covers(AuthorizationLevel.ADMIN);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setLevel(AuthorizationLevel auth)
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        _f._udb.setLevel(_id, auth);

        if (!auth.covers(AuthorizationLevel.ADMIN)) getOrganization().throwIfNoAdmin();
    }

    public void setWhitelisted(boolean whitelisted)
            throws SQLException
    {
        _f._udb.setWhitelisted(_id, whitelisted);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(FullName fullName) throws SQLException
    {
        _f._udb.setName(_id, fullName);
    }

    public boolean belongsTo(Organization org)
            throws SQLException, ExNotFound
    {
        return getOrganization().equals(org);
    }

    public boolean isAdminOf(User user) throws SQLException, ExNotFound
    {
        return isAdmin() && user.belongsTo(getOrganization());
    }

    /**
     * Peer devices are all devices that you sync with, including your own devices.
     */
    public Collection<Device> getPeerDevices()
            throws SQLException, ExFormatError
    {
        // Get shared folders and all users who sync those shared folders.
        Collection<SharedFolder> sharedFolders = getSharedFolders();
        Set<User> peerUsers = Sets.newHashSet();

        for (SharedFolder sharedFolder : sharedFolders) {
            for (User user : sharedFolder.getJoinedUsers()) {
                peerUsers.add(user);
            }
        }

        // Ensure calling user is in the list.
        peerUsers.add(this);

        // From the list of peer users, find the list of devices.
        List<Device> peerDevices = Lists.newLinkedList();

        for (User peerUser : peerUsers) {
            for (Device userDevice : peerUser.getDevices()) {
                peerDevices.add(userDevice);
            }
        }

        return peerDevices;
    }

    /**
     * Peer devices are all devices that you sync with, including your own devices, for a given
     * shared folder.
     */
    public Collection<Device> getPeerDevices(SharedFolder sharedFolder)
            throws SQLException, ExFormatError
    {
        Collection<User> peerUsers = sharedFolder.getJoinedUsers();
        List<Device> peerDevices = Lists.newLinkedList();

        for (User peerUser : peerUsers) {
            Collection<Device> userDevices = peerUser.getDevices();

            for (Device userDevice : userDevices) {
                peerDevices.add(userDevice);
            }
        }

        // If we're haven't already, add our devices.
        if (!peerUsers.contains(this)) {
            for (Device device : getDevices()) {
                peerDevices.add(device);
            }
        }

        return peerDevices;
    }

    public ImmutableList<Device> getDevices()
            throws SQLException, ExFormatError
    {
        ImmutableList.Builder<Device> builder = ImmutableList.builder();

        for (DID did : _f._udb.getDevices(id())) {
            builder.add(_f._factDevice.create(did));
        }

        return builder.build();
    }

    public List<OrganizationInvitation> getOrganizationInvitations()
            throws SQLException, ExNotFound
    {
        Organization organization = getOrganization();

        List<OrganizationInvitation> result = Lists.newLinkedList();
        for (OrganizationID orgID : _f._odb.getInvitedOrganizations(id())) {
            if (!organization.id().equals(orgID)) {
                result.add(_f._factOrgInvite.create(this, _f._factOrg.create(orgID)));
            }
        }

        return result;
    }

    /**
     * In public deployment: create a new organization, add the user to the organization as an admin.
     * In private deployment: join the user to the private organization. Create the org if it
     * doesn't exist. When this method concludes, the organiztion invitation associated with this
     * user will no longer exist in the database.
     *
     * @param shaedSP sha256(scrypt(p|u)|passwdSalt)
     * @throws ExAlreadyExist if the user ID already exists.
     * @throws ExLicenseLimit if the user doesn't exist and the organization is
     *                        at or above their license's seat limit
     */
    public void save(byte[] shaedSP, FullName fullName)
            throws ExAlreadyExist, SQLException, ExNotFound, ExLicenseLimit
    {
        if (PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT) {
            // Private deployment: all users are created in the same organization (the "private
            // organization").
            Organization privateOrg = _f._factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);
            AuthorizationLevel authLevel = AuthorizationLevel.USER;

            // But if the private organization doesn't exist yet, create it and make the user an
            // admin
            if (!privateOrg.exists()) {
                privateOrg = _f._factOrg.save(OrganizationID.PRIVATE_ORGANIZATION);
                authLevel = AuthorizationLevel.ADMIN;
            }

            // Verify valid license
            if (!_f._license.isValid()) {
                throw new ExLicenseLimit("No valid license available - refusing to create a user");
            }

            // Enforce seat limits
            if (privateOrg.countUsers() >= _f._license.seats()) {
                throw new ExLicenseLimit("Adding a user would exceed the organization's "
                         + _f._license.seats() + "-seat limit");
            }

            saveImpl(shaedSP, fullName, privateOrg, authLevel);

            // Remove organization invitations if any. Why we do this:
            //
            // Even though there is only one private organization, the system uses the same code
            // path as in the public deployemnt when the organization's admin invites users
            // (either external or internal) to the organization. Once the user joins the org, the
            // user should disappear from the member management Web interface. The interface calls
            // SP.listOrganizationInvitedUsers() to list all the invitations belonging to that org.
            //
            OrganizationInvitation oi = _f._factOrgInvite.create(this, privateOrg);
            if (oi.exists()) oi.delete();

        } else {
            // Public deployment: create a new organization for this user and make them an admin
            saveImpl(shaedSP, fullName, _f._factOrg.save(), AuthorizationLevel.ADMIN);

            // Because a brand new org is create, we don't need to delete any organization invite as
            // what we do in the case of private deployment (see the other conditional branch).
        }
    }

    private void saveImpl(byte[] shaedSP, FullName fullName, Organization org,
            AuthorizationLevel level)
            throws SQLException, ExAlreadyExist
    {
        _f._udb.insertUser(_id, fullName, shaedSP, org.id(), level);

        addRootStoreAndCheckForCollision();
    }

    /**
     * Deactivate a user
     *
     * All ACLs for this user are deleted.
     * All devices for this user are marked as unlinked and their certificates revoked.
     *
     * If the user being deactivated is the only remaining owner of a shared folder with remaining
     * members:
     *      * if {@paramref newOwner} is non-null then takes ownership of the folder
     *      * if {@paramref newOwner} is null then throw ExNoAdminOrOwner
     *
     */
    public ImmutableSet<UserID> deactivate(ImmutableSet.Builder<Long> revokedSerials,
            @Nullable User newOwner)
            throws SQLException, ExNotFound, ExFormatError, ExNoAdminOrOwner
    {
        Preconditions.checkArgument(newOwner == null || !_id.equals(newOwner.id()));

        // revoke certs and mark devices as unlinked
        for (Device device : getDevices()) {
            revokedSerials.addAll(device.delete());
        }

        // delete ACLs
        ImmutableSet.Builder<UserID> affected = ImmutableSet.builder();
        for (SharedFolder sf : getSharedFolders()) {
            affected.addAll(sf.removeUserAndTransferOwnership(this, newOwner));
        }

        // to prevent the user from signing up again using old codes
        deleteAllSignUpCodes();

        _f._udb.deactivate(id());
        return ImmutableSet.copyOf(affected.build());
    }

    /**
     * Add the root store for the user, to:
     *
     * 1. include the team server to the root store.
     * 2. avoid attackers hijacking existing users' root store with intentional store ID collisions.
     */
    private void addRootStoreAndCheckForCollision()
            throws SQLException
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
            rootStore.destroy();
        }

        // Ignore the return value and do not publish verkehr notifications, as this newly added
        // user mustn't have any daemon running at this moment.
        try {
            rootStore.save("root store: " + _id, this);
        } catch (ExNotFound e) {
            // The method throws ExNotFound only if the owner doesn't exist
            SystemUtil.fatal(e);
        } catch (ExAlreadyExist e) {
            // The method throws ExAlreadyExist only if the store already exists
            SystemUtil.fatal(e);
        }
    }

    /**
     * Attemp to sign in using the credential provided by the user.
     *
     * @throws ExBadCredential if the user doesn't exist or the credential is incorrect.
     */
    public void throwIfBadCredential(byte[] shaedSP)
            throws SQLException, ExBadCredential
    {
        l.warn("SI: " + toString());

        try {
            if (!isCredentialCorrect(shaedSP)) {
                l.warn(toString() + ": bad password.");
                throw new ExBadCredential();
            }
        } catch (ExNotFound e) {
            // Throw a bad credential as opposed to a not found to prevent brute force guessing of
            // user IDs.
            l.warn(toString() + ": not found.");
            throw new ExBadCredential();
        }
    }

    /**
     * Attempt to sign in using the certificate provided by the user.
     *
     * @param certauth the certificate authenticator object for this specific session.
     * @param device the device the user claims this request is originating from.
     *
     * @throws ExBadCredential if the sign in failed due to expired certificate, missing device,
     * or missing user.
     */
    public void throwIfBadCertificate(CertificateAuthenticator certauth, Device device)
            throws SQLException, ExBadCredential, ExNotFound
    {
        if (!certauth.isAuthenticated())
        {
            l.warn(toString() + ": cert not authenticated");
            throw new ExBadCredential();
        }

        l.warn("SI (cert): " + toString() + ":" + device.id().toStringFormal());

        String actualCName = certauth.getCName();
        String expectedCName = BaseSecUtil.getCertificateCName(id(), device.id());

        // Can happen if one user is impersonating another user.
        if (!actualCName.equals(expectedCName)) {
            l.error(toString() + ": wrong cname actual=" + actualCName + " expected=" + expectedCName);
            throw new ExBadCredential();
        }

        // Should never happen, check just for good measure.
        if (certauth.getSerial() != device.certificate().serial()) {
            l.error(toString() + ": serial mismatch");
            throw new ExBadCredential();
        }

        if (device.certificate().isRevoked()) {
            l.warn(toString() + ": cert revoked");
            throw new ExBadCredential();
        }

        if (!device.getOwner().equals(this)) {
            l.warn(toString() + ": device does not belong to user.");
            throw new ExBadCredential();
        }
    }

    /**
     * Move the user to a new organization, set appropriate auth level, and adjust ACLs of shared
     * folders for the team server.
     */
    public ImmutableCollection<UserID> setOrganization(Organization org, AuthorizationLevel level)
            throws SQLException, ExNotFound, ExAlreadyExist, ExNoAdminOrOwner
    {
        Organization orgOld = getOrganization();
        if (orgOld.equals(org)) {
            // no organization change? only set the level and skip the rest. This optimization is
            // important for private deployments, where everyone automatically becomes a member of
            // the same private org during signup, and therefore all calls to setOrganization are
            // unuseful.
            setLevel(level);
            return ImmutableList.of();
        }

        // Delete organization invitation if any
        OrganizationInvitation oi = _f._factOrgInvite.create(this, org);
        if (oi.exists()) oi.delete();

        Collection<SharedFolder> sfs = getSharedFolders();

        ImmutableSet.Builder<UserID> builder = ImmutableSet.builder();

        for (SharedFolder sf : sfs) builder.addAll(sf.removeTeamServerForUser(this));

        _f._udb.setOrganizationID(_id, org.id());

        // Set the level _after_ moving to the new org; otherwise setLevel() may fail if the user
        // was the last member of the old org.
        AuthorizationLevel levelOld = getLevel();
        setLevel(level);

        for (SharedFolder sf : sfs) builder.addAll(sf.addTeamServerForUser(this));

        //noinspection StatementWithEmptyBody
        if (orgOld.countUsers() == 0) {
            // TODO (WW) delete the old organization
        } else if (levelOld.covers(AuthorizationLevel.ADMIN)) {
            // There must be one admin left for a non-empty team
            orgOld.throwIfNoAdmin();
        }

        return builder.build();
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
     * Generate a signup invitation code and add it to the database.
     * @return the signup code
     * @pre the user doesn't exist
     */
    public String addSignUpCode()
            throws SQLException
    {
        assert !exists();

        String code = Base62CodeGenerator.generate();
        _f._udb.insertSignupCode(code, _id);
        return code;
    }

    public void deleteAllSignUpCodes()
            throws SQLException
    {
        _f._udb.deleteAllSignUpCodes(_id);
    }
}
