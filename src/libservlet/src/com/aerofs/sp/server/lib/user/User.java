/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ParamFactory;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExLicenseLimit;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.base.ex.ExSecondFactorSetupRequired;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.rest.auth.IUserAuthToken;
import com.aerofs.rest.auth.OAuthRequestFilter;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.authentication.TOTP;
import com.aerofs.sp.common.Base62CodeGenerator;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.GroupMembersDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.Organization.TwoFactorEnforcementLevel;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.organization.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.session.ISession.Provenance;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.twofactor.RecoveryCode;
import com.aerofs.sp.server.lib.twofactor.TwoFactorAuthDatabase;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sun.jersey.api.core.HttpContext;

public class User
{
    private final static Logger l = Loggers.getLogger(User.class);

    public static class Factory
    {
        private UserDatabase _udb;
        private OrganizationInvitationDatabase _odb;
        private TwoFactorAuthDatabase _tfdb;
        private GroupMembersDatabase _gmdb;

        private Device.Factory _factDevice;
        private Organization.Factory _factOrg;
        private OrganizationInvitation.Factory _factOrgInvite;
        private SharedFolder.Factory _factSharedFolder;
        private Group.Factory _factGroup;
        private License _license;

        @Inject
        public void inject(UserDatabase udb, OrganizationInvitationDatabase odb,
                TwoFactorAuthDatabase tfdb, GroupMembersDatabase gmdb,
                Device.Factory factDevice, Organization.Factory factOrg,
                OrganizationInvitation.Factory factOrgInvite, SharedFolder.Factory factSharedFolder,
                Group.Factory factGroup, License license)
        {
            _udb = udb;
            _odb = odb;
            _tfdb = tfdb;
            _gmdb = gmdb;
            _factDevice = factDevice;
            _factOrg = factOrg;
            _factOrgInvite = factOrgInvite;
            _factSharedFolder = factSharedFolder;
            _factGroup = factGroup;
            _license = license;
        }

        @ParamFactory
        public User create(String userid, HttpContext cxt)
        {
            if (userid.equals("me")) {
                IUserAuthToken token = (IUserAuthToken)cxt.getProperties().get(OAuthRequestFilter.OAUTH_TOKEN);
                return create(token.user());
            }
            return create(userid);
        }

        public User create(String userid)
        {
            try {
                return createFromExternalID(userid);
            } catch (ExBadArgs e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        public User create(@Nonnull UserID id)
        {
            return new User(this, id);
        }

        public User createFromExternalID(@Nonnull String str)
                throws ExBadArgs
        {
            try {
                return create(UserID.fromExternal(str));
            } catch (ExInvalidID e) {
                throw new ExBadArgs("invalid userid");
            }
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

        public List<UserID> listUsers(Integer limit, UserID startingAfter, UserID endingBefore)
                throws ExInvalidID, SQLException
        {
            return _udb.listUsers(limit, startingAfter, endingBefore);
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

    public void throwIfNotTeamServer()
            throws ExNoPerm, SQLException, ExNotFound
    {
        if (!id().isTeamServerID()) {
            throw new ExNoPerm("user " + id().getString() + " is not a TeamServer user");
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

    /**
     * @return the usage in bytes, or null if the value has not been set
     */
    public @Nullable Long getBytesUsed()
            throws SQLException, ExNotFound
    {
        return _f._udb.getBytesUsed(_id);
    }

    public void setBytesUsed(long bytesUsed)
            throws SQLException
    {
        _f._udb.setBytesUsed(_id, bytesUsed);
    }

    public boolean getUsageWarningSent()
            throws SQLException, ExNotFound
    {
        return _f._udb.getUsageWarningSent(_id);
    }

    public void setUsageWarningSent(boolean warningSent)
            throws SQLException
    {
        _f._udb.setUsageWarningSent(_id, warningSent);
    }

    public Timestamp getPasswordCreatedTS()
            throws ExNotFound, SQLException
    {
        return _f._udb.getPasswordCreatedTS(_id);
    }

    public byte[] getShaedSP(UserID userId)
            throws ExNotFound, SQLException
    {
        return _f._udb.getShaedSP(userId);
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
            throws SQLException, ExInvalidID
    {
        // Get joined shared folders and all users who sync those shared folders.
        Collection<SharedFolder> joinedSharedFolders = getJoinedFolders();
        Set<User> peerUsers = Sets.newHashSet();

        for (SharedFolder sharedFolder : joinedSharedFolders) {
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

    public ImmutableList<Device> getDevices()
            throws SQLException, ExInvalidID
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
     * join the user to the private organization. Create the org if it
     * doesn't exist. When this method concludes, the organization invitation associated with this
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
        // all users are created in the same organization (the "private organization").
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
        if (privateOrg.countInternalUsers() >= _f._license.seats()) {
            throw new ExLicenseLimit("Adding a user would exceed the organization's "
                     + _f._license.seats() + "-seat limit");
        }

        saveImpl(shaedSP, fullName, privateOrg, authLevel);

        // Remove organization invitations if any. Why we do this:
        //
        // Even though there is only one private organization, the system uses the same code
        // path as in the public deployment when the organization's admin invites users
        // (either external or internal) to the organization. Once the user joins the org, the
        // user should disappear from the member management Web interface. The interface calls
        // SP.listOrganizationInvitedUsers() to list all the invitations belonging to that org.
        //
        OrganizationInvitation oi = _f._factOrgInvite.create(this, privateOrg);
        if (oi.exists()) oi.delete();
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
            throws SQLException, ExNotFound, ExInvalidID, ExNoAdminOrOwner
    {
        Preconditions.checkArgument(newOwner == null || !_id.equals(newOwner.id()));

        // revoke certs and mark devices as unlinked
        for (Device device : getDevices()) {
            revokedSerials.addAll(device.delete());
        }

        // delete ACLs
        ImmutableSet.Builder<UserID> affected = ImmutableSet.builder();

        // N.B. remove the user from all groups first, so that the later getSharedFolders() call
        // will only return sharedFolders we're in a part of irrespective of group memberships
        for (Group group : getGroups()) {
            affected.addAll(group.removeMember(this, newOwner));
        }

        for (SharedFolder sf : getAllFolders()) {
            affected.addAll(sf.removeUserAndTransferOwnership(this, newOwner, null));
        }

        // to prevent the user from signing up again using old codes
        deleteAllOrganizationInvitations();
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

        // Ignore the return value and do not publish lipwig notifications, as this newly added
        // user mustn't have any daemon running at this moment.
        try {
            rootStore.save("root store: " + _id, this);
        } catch (ExNotFound | ExAlreadyExist e) {
            // The method throws ExNotFound if the owner doesn't exist and ExAlreadyExist if the
            // store already exists.
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
        l.info("SI (cred): " + toString());

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
            throws SQLException, ExBadCredential, ExNotFound, ExInvalidID
    {
        if (!certauth.isAuthenticated()) {
            l.warn("{}: cert not authenticated", toString());
            throw new ExBadCredential();
        }

        l.info("SI (cert): {}:{}", toString(), device.id().toStringFormal());

        String actualCName = certauth.getCName();
        String expectedCName = BaseSecUtil.getCertificateCName(id(), device.id());

        // Can happen if one user is impersonating another user.
        if (!actualCName.equals(expectedCName)) {
            l.error("{}: wrong cname actual={} epxected={}", toString(), actualCName, expectedCName);
            throw new ExBadCredential();
        }

        if (device.isUnlinked()) {
            l.warn("{}: device unlinked", toString());
            throw new ExBadCredential();
        }

        if (!device.getOwner().equals(this)) {
            l.warn("{}: device does not belong to user.", toString());
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
        // Delete organization invitation if any
        OrganizationInvitation oi = _f._factOrgInvite.create(this, org);
        if (oi.exists()) oi.delete();

        Organization orgOld = getOrganization();
        if (orgOld.equals(org)) {
            // no organization change? only set the level and skip the rest. This optimization is
            // important for private deployments, where everyone automatically becomes a member of
            // the same private org during signup, and therefore all calls to setOrganization are
            // unuseful.
            setLevel(level);
            // always update ACL epoch for Team Servers to make sure they are aware of the creation
            // of new users
            return ImmutableList.of(org.id().toTeamServerUserID());
        }

        Collection<SharedFolder> sfs = getJoinedFolders();

        ImmutableSet.Builder<UserID> builder = ImmutableSet.builder();

        for (SharedFolder sf : sfs) builder.addAll(sf.removeTeamServerForUser(this));

        // Since groups are only within an organization, leave all groups upon changing org
        for (Group g : getGroups()) {
            if (!org.equals(g.getOrganization())) builder.addAll(g.removeMember(this, null));
        }

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

    private Collection<SharedFolder> getFoldersFromSIDs(Collection<SID> sids)
    {
        List<SharedFolder> sfs = Lists.newArrayListWithCapacity(sids.size());
        for (SID sid : sids) {
            sfs.add(_f._factSharedFolder.create(sid));
        }
        return sfs;
    }

    public Collection<SharedFolder> getJoinedFolders()
            throws SQLException
    {
        Collection<SID> sids = _f._udb.getJoinedFolders(_id);
        return getFoldersFromSIDs(sids);
    }

    public int countSharedFolders()
            throws SQLException
    {
        return _f._udb.countSharedFolders(_id);
    }

    public int countSharedFoldersWithPrefix(String searchPrefix)
            throws SQLException
    {
        return _f._udb.countSharedFoldersWithPrefix(_id, searchPrefix);
    }

    public Collection<SharedFolder> getSharedFolders()
            throws SQLException
    {
        return getSharedFolders(null, null, null);
    }

    public Collection<SharedFolder> getSharedFolders(Integer maxResults, Integer offset, String searchPrefix)
            throws SQLException
    {
        Collection<SID> sids = _f._udb.getSharedFolders(_id, maxResults, offset, searchPrefix);
        return getFoldersFromSIDs(sids);
    }

    public Collection<SharedFolder> getAllFolders()
            throws SQLException
    {
        Collection<SID> sids = _f._udb.getAllFolders(_id);
        return getFoldersFromSIDs(sids);
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

    public String addSignUpCodeWithOrgInvite(User inviter)
            throws SQLException, ExNotFound
    {
        OrganizationInvitation invite =  _f._factOrgInvite.create(this, inviter.getOrganization());
        if (invite.exists()) {
            // don't make an extra invite, will fail the orginvite table's primary key constraint
            return invite.getCode();
        } else {
            String code = addSignUpCode();
            _f._factOrgInvite.save(inviter, this, inviter.getOrganization(), code);
            return code;
        }
    }

    private void deleteAllOrganizationInvitations()
            throws SQLException
    {
        _f._udb.deleteAllOrganizationInvitations(_id);
    }

    // N.B. the caller is responsible for ensuring all organization invites using these sign-up
    //   codes are deleted.
    public void deleteAllSignUpCodes()
            throws SQLException
    {
        _f._udb.deleteAllSignUpCodes(_id);
    }

    public boolean shouldEnforceTwoFactor()
            throws SQLException, ExNotFound
    {
        return _f._udb.getEnforceSecondFactor(_id);
    }

    /**
     * A function to get the user-dependent set of acceptable provenances for the specified
     * provenance group.  In particular, this is useful for determining if BASIC authentication is
     * sufficient for user actions, or if TWO_FACTOR should be required for that user.
     * @param group The context-specific provenance group
     * @return the list of acceptable provenances for that context for this user
     */
    @SuppressWarnings("fallthrough")
    public ImmutableList<Provenance> sufficientProvenances(ProvenanceGroup group)
            throws SQLException, ExNotFound
    {
        ImmutableList.Builder<Provenance> builder = ImmutableList.builder();
        TwoFactorEnforcementLevel level = getOrganization().getTwoFactorEnforcementLevel();
        switch (group) {
        case LEGACY:
            builder.add(Provenance.CERTIFICATE);
            //fall-through intentional - INTERACTIVE is LEGACY without CERTIFICATE
        case INTERACTIVE:
            switch (level) {
                case DISALLOWED:
                    // Basic is always sufficient for organizations with 2FA disallowed
                    builder.add(Provenance.BASIC);
                    break;
                case OPT_IN:
                    // Let the user's preferences pick what's sufficient for them
                    if (shouldEnforceTwoFactor()) {
                        builder.add(Provenance.BASIC_PLUS_SECOND_FACTOR);
                    } else {
                        builder.add(Provenance.BASIC);
                    }
                    break;
                case MANDATORY:
                    // Require two-factor for all actions.
                    // Only allow 2FA to be sufficient if the user has it enabled.
                    if (shouldEnforceTwoFactor()) {
                        builder.add(Provenance.BASIC_PLUS_SECOND_FACTOR);
                    }
                    break;
                default:
                    // Should never be reached
                    Preconditions.checkState(false, "Invalid 2fa enforcement level {}", level);
            }
            break;
        case TWO_FACTOR_SETUP:
            // Two factor setup will ignore the MANDATORY enforcement level, but only for the
            // specific two-factor auth setup RPCs.  Two factor setup may not be performed by
            // devices.
            switch (level) {
                case DISALLOWED:
                    builder.add(Provenance.BASIC);
                    break;
                case OPT_IN:
                case MANDATORY:
                    if (shouldEnforceTwoFactor()) {
                        builder.add(Provenance.BASIC_PLUS_SECOND_FACTOR);
                    } else {
                        builder.add(Provenance.BASIC);
                    }
                    break;
                default:
                    // Should never be reached
                    Preconditions.checkState(false, "Invalid 2fa enforcement level {}", level);
            }
            break;

        default:
            // Should never be reached; allow no provenances.
            Preconditions.checkState(false, "Invalid provenance group {}", group);
            break;
        }
        return builder.build();
    }

    @SuppressWarnings("fallthrough")
    public static void checkProvenance(User user,
            ImmutableList<Provenance> authenticatedProvenances, ProvenanceGroup provenanceGroup)
            throws ExNotAuthenticated, ExSecondFactorRequired, SQLException, ExNotFound,
            ExSecondFactorSetupRequired
    {
        switch (provenanceGroup) {
        // fallthrough is intentional.  The same logic can currently handle answering the question
        // "which exception should I throw?" for all three cases.
        case LEGACY:
        case TWO_FACTOR_SETUP:
        case INTERACTIVE:
            ImmutableList<Provenance> sufficient = user.sufficientProvenances(provenanceGroup);
            for (Provenance authedProvenance : authenticatedProvenances) {
                if (sufficient.contains(authedProvenance)) {
                    l.info("{} included provenance {} which is sufficient for {}",
                            user.id(), authedProvenance, provenanceGroup);
                    return;
                }
            }
            l.info("{} lacks provenance to satisfy {}; has {}, needs one of {}",
                    user.id(), provenanceGroup, authenticatedProvenances, sufficient);
            // The session lacks an acceptable provenance.  Throw a suitable exception.
            if (authenticatedProvenances.contains(Provenance.BASIC)) {
                if (user.getOrganization().getTwoFactorEnforcementLevel() ==
                        TwoFactorEnforcementLevel.MANDATORY && !user.shouldEnforceTwoFactor()) {
                    throw new ExSecondFactorSetupRequired();
                }
                if (sufficient.contains(Provenance.BASIC_PLUS_SECOND_FACTOR) &&
                    !sufficient.contains(Provenance.BASIC)) {
                    throw new ExSecondFactorRequired();
                }
            }
            throw new ExNotAuthenticated();
        default:
            // Should never be reached, but privilege systems should fail closed.
            throw new ExNotAuthenticated();
        }
    }

    private byte[] twoFactorSecret()
            throws SQLException, ExNotFound
    {
        return _f._tfdb.secretFor(_id);
    }

    public ImmutableList<RecoveryCode> recoveryCodes()
            throws SQLException, ExNotFound
    {
        return _f._tfdb.recoveryCodesFor(_id);
    }

    /**
     * @return true if claimedCode is acceptable, false otherwise
     */
    public boolean checkSecondFactor(int claimedCode)
            throws SQLException, ExNotFound, NoSuchAlgorithmException, InvalidKeyException
    {
        return TOTP.check(twoFactorSecret(), claimedCode, 1);
    }

    public boolean checkBackupCode(String claimedCode)
            throws SQLException, ExNotFound
    {
        ImmutableList<RecoveryCode> codes = recoveryCodes();
        byte[] claimedCodeBytes = BaseUtil.string2utf(claimedCode);
        for (RecoveryCode code: codes) {
            if (!code.isConsumed() && BaseSecUtil.constantTimeIsEqual(claimedCodeBytes,
                    BaseUtil.string2utf(code.code()))) {
                // Accept code and mark as used
                _f._tfdb.markRecoveryCodeUsed(_id, code.code());
                return true;
            }
        }
        return false;
    }

    public byte[] setupTwoFactor()
            throws SQLException
    {
        byte[] secret = BaseSecUtil.newRandomBytes(
                TwoFactorAuthDatabase.TWO_FACTOR_SECRET_KEY_LENGTH);
        Builder<String> builder = ImmutableList.builder();
        for (int i = 0 ; i < TwoFactorAuthDatabase.EXPECTED_RECOVERY_CODE_COUNT ; i++) {
            String code = generateRecoveryCode(TwoFactorAuthDatabase.RECOVERY_CODE_MAX_LENGTH);
            builder.add(code);
        }
        _f._tfdb.prepareUser(_id, secret, builder.build());
        return secret;
    }

    // generates a secure-random string of digits.  e.g. generateRecoveryCode(10) ->
    // "1248812019"
    private String generateRecoveryCode(int length)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0 ; i < length; i++) {
            sb.append(String.valueOf(BaseSecUtil.newRandomInt(10)));
        }
        return sb.toString();
    }

    /**
     * Enables second-factor enforcement for a user's future logins.
     * This should only be called once the user has proven that they can correctly use their
     * authenticator application and that they have imported their current secret.
     */
    public void enableTwoFactorEnforcement()
            throws SQLException
    {
        // Sanity check: make sure user has a two-factor secret created.
        try {
            _f._tfdb.secretFor(_id);
        } catch (ExNotFound e) {
            Preconditions.checkState(false, "can't enforce two-factor on user with no TFA secret");
        }
        _f._udb.setEnforceSecondFactor(_id, true);
    }

    public void disableTwoFactorEnforcement()
            throws SQLException
    {
        // It's okay to leave the secrets in the DB; they will no longer be used.
        _f._udb.setEnforceSecondFactor(_id, false);
    }

    public ImmutableCollection<Group> getGroups()
            throws SQLException
    {
        return groupsFromIDs(_f._gmdb.listGroupsFor(id()));
    }

    private ImmutableList<Group> groupsFromIDs(Iterable<GroupID> ids)
    {
        ImmutableList.Builder<Group> groups = ImmutableList.builder();
        for (GroupID gid : ids) {
            groups.add(_f._factGroup.create(gid));
        }
        return groups.build();
    }

    public static class EmailAndName
    {
        final public String email;
        final public String firstName;
        final public String lastName;

        public EmailAndName(String email, String firstName, String lastName)
        {
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        @Override
        public boolean equals(Object o)
        {
            return o == this || (o != null && o instanceof EmailAndName
                    && email.equals(((EmailAndName) o).email)
                    && firstName.equals(((EmailAndName) o).firstName)
                    && lastName.equals(((EmailAndName) o).lastName));
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(email, firstName, lastName);
        }
    }
}
