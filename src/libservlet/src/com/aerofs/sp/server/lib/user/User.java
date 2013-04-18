/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.id.DID;
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
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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

        @Inject
        public Factory(UserDatabase udb, OrganizationInvitationDatabase odb,
                Device.Factory factDevice, Organization.Factory factOrg,
                OrganizationInvitation.Factory factOrgInvite, SharedFolder.Factory factSharedFolder)
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
                throws ExEmptyEmailAddress
        {
            return create(UserID.fromExternal(str));
        }

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

        public ResultSet getDefaultOrgUsers()
                throws SQLException
        {
            return _udb.getDefaultOrgUsers();
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
        return Arrays.equals(_f._udb.getShaedSP(_id), shaedSP);
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
            throws SQLException, ExNotFound, ExNoAdminOrOwner
    {
        _f._udb.setLevel(_id, auth);

        if (!auth.covers(AuthorizationLevel.ADMIN)) getOrganization().throwIfNoAdmin();
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(FullName fullName) throws SQLException
    {
        _f._udb.setName(_id, fullName);
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
            Collection<User> users = sharedFolder.getMembers();

            for (User user : users) {
                peerUsers.add(user);
            }
        }

        // Ensure calling user is in the list.
        peerUsers.add(this);

        // From the list of peer users, find the list of devices.
        List<Device> peerDevices = Lists.newLinkedList();

        for (User peerUser : peerUsers) {
            Collection<Device> userDevices = peerUser.getDevices();

            for (Device userDevice : userDevices) {
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
        Collection<User> peerUsers = sharedFolder.getMembers();
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
     * Create a new organization, add the user to the organization as an admin
     * @param shaedSP sha256(scrypt(p|u)|passwdSalt)
     * @throws ExAlreadyExist if the user ID already exists.
     */
    public void save(byte[] shaedSP, FullName fullName)
            throws ExAlreadyExist, SQLException
    {
        saveImpl(shaedSP, fullName, _f._factOrg.save(), AuthorizationLevel.ADMIN);
    }

    public void saveImpl(byte[] shaedSP, FullName fullName, Organization org, AuthorizationLevel level)
            throws SQLException, ExAlreadyExist
    {
        _f._udb.insertUser(_id, fullName, shaedSP, org.id(), level);

        addRootStoreAndCheckForCollision();
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
            rootStore.delete();
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
     * @throws ExBadCredential if the user doesn't exist of the credential is incorrect.
     */
    public void signIn(byte[] shaedSP)
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
    public void signInWithCertificate(CertificateAuthenticator certauth, Device device)
            throws SQLException, ExBadCredential, ExNotFound
    {
        // Team servers use certificates (in this case the passed credentials don't matter).
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
    public Collection<UserID> setOrganization(Organization org, AuthorizationLevel level)
            throws SQLException, ExNotFound, ExAlreadyExist, ExNoAdminOrOwner
    {
        Organization orgOld = getOrganization();
        AuthorizationLevel levelOld = getLevel();

        Collection<SharedFolder> sfs = getSharedFolders();

        Set<UserID> users = Sets.newHashSet();

        if (orgOld.id().getInt() != 0) {
            for (SharedFolder sf : sfs) users.addAll(sf.deleteTeamServerACL(this));
        }

        _f._udb.setOrganizationID(_id, org.id());

        setLevel(level);

        for (SharedFolder sf : sfs) users.addAll(sf.addTeamServerACL(this));

        if (orgOld.countUsers() == 0) {
            // TODO (WW) delete orgOld
        } else if (orgOld.id().getInt() != 0 && levelOld.covers(AuthorizationLevel.ADMIN)) {
            // There must be one admin left for a non-empty team
            orgOld.throwIfNoAdmin();
        }

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

    public boolean isInvitedToSignUp()
            throws SQLException
    {
        return _f._udb.isInvitedToSignUp(_id);
    }
}
