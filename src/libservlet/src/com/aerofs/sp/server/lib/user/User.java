/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.FullName;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class User
{
    private final static Logger l = Util.l(User.class);

    public static class Factory
    {
        private final UserDatabase _db;
        private final Organization.Factory _factOrg;
        private final SharedFolder.Factory _factSharedFolder;

        @Inject
        public Factory(UserDatabase db, Organization.Factory factOrg,
                SharedFolder.Factory factSharedFolder)
        {
            _db = db;
            _factOrg = factOrg;
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
        return "user #" + _id.toString();
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
        return _f._db.hasUser(_id);
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
        return _f._factOrg.create(_f._db.getOrgID(_id));
    }

    public FullName getFullName()
            throws ExNotFound, SQLException
    {
        return _f._db.getFullName(_id);
    }

    /**
     * @return sha256(scrypt(p|u)|passwdSalt)
     */
    public byte[] getShaedSP()
            throws ExNotFound, SQLException
    {
        return _f._db.getShaedSP(_id);
    }

    public boolean isVerified()
            throws ExNotFound, SQLException
    {
        return _f._db.isVerified(_id);
    }

    public AuthorizationLevel getLevel()
            throws ExNotFound, SQLException
    {
        return _f._db.getLevel(_id);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setLevel(AuthorizationLevel auth)
            throws SQLException
    {
        _f._db.setLevel(_id, auth);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setVerified() throws SQLException
    {
        _f._db.setVerified(_id);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(FullName fullName) throws SQLException
    {
        _f._db.setName(_id, fullName);
    }

    /**
     * Add the user to the database
     * @throws ExAlreadyExist if the user ID already exists.
     */
    public void add(byte[] shaedSP, FullName fullName, Organization org)
            throws ExAlreadyExist, SQLException, ExNoPerm, IOException, ExNotFound
    {
        Util.l(this).info(this + " attempts signup");

        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _f._db.addUser(_id, fullName, shaedSP, org.id(), AuthorizationLevel.USER);

        addRootStoreAndCheckForCollision();
    }

    /**
     * Add the root store for the user, to:
     *
     * 1. include the team server to the root store.
     * 2. avoid attackers hijacking existing users' root store with intentional store ID collisions.
     */
    private void addRootStoreAndCheckForCollision()
            throws SQLException, ExNoPerm, IOException, ExNotFound, ExAlreadyExist
    {
        SharedFolder rootStore = _f._factSharedFolder.create_(SID.rootSID(_id));

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
        rootStore.add("root store: " + _id, this);
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
    public Map<UserID, Long> addAndMoveToOrganization(String orgName)
            throws ExNoPerm, SQLException, ExNotFound, ExAlreadyExist
    {
        // TODO: verify the calling user is allowed to create an organization

        // only users in the default organization or admins can add organizations.
        if (!getOrganization().id().equals(OrgID.DEFAULT) &&
                getLevel() != AuthorizationLevel.ADMIN) {
            throw new ExNoPerm("you have no permission to create new teams");
        }

        Organization org = _f._factOrg.add(orgName);
        setLevel(AuthorizationLevel.ADMIN);
        return setOrganization(org);
    }

    /**
     * Move the user to a new organization, and adjust ACLs of shared folders for the team server.
     */
    private Map<UserID, Long> setOrganization(Organization org)
            throws SQLException, ExNotFound, ExAlreadyExist
    {
        Collection<SharedFolder> sfs = getSharedFolders();

        Map<UserID, Long> epochs = Maps.newHashMap();

        for (SharedFolder sf : sfs) epochs.putAll(sf.deleteTeamServerACL(this));

        _f._db.setOrgID(_id, org.id());

        for (SharedFolder sf : sfs) epochs.putAll(sf.addTeamServerACL(this));

        return epochs;
    }

    public Collection<SharedFolder> getSharedFolders()
            throws SQLException
    {
        Collection<SID> sids = _f._db.getSharedFolders(_id);
        List<SharedFolder> sfs = Lists.newArrayListWithCapacity(sids.size());
        for (SID sid : sids) {
            sfs.add(_f._factSharedFolder.create_(sid));
        }
        return sfs;
    }

    /**
     * generate a signup invitation code and add it to the database
     * @return the signup code
     * @pre the user doesn't exist
     */
    public String addSignUpInvitationCode(User inviter, Organization org)
            throws SQLException
    {
        assert !exists();

        String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
        _f._db.addSignupCode(code, inviter.id(), _id, org.id());
        return code;
    }

    public int getSignUpInvitationsQuota()
            throws ExNotFound, SQLException
    {
        return _f._db.getSignUpInvitationsQuota(_id);
    }

    public void setSignUpInvitationQuota(int quota)
            throws SQLException
    {
        _f._db.setSignUpInvitationsQuota(_id, quota);
    }

    public boolean isInvitedToSignUp()
            throws SQLException
    {
        return _f._db.isInvitedToSignUp(_id);
    }
}
