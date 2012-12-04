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
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.common.InvitationCode;
import com.aerofs.sp.common.InvitationCode.CodeType;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Arrays;

public class User
{
    private final static Logger l = Util.l(User.class);

    private final UserID _id;

    private final UserDatabase _db;
    private final Organization.Factory _factOrg;

    public static class Factory
    {
        private final UserDatabase _db;
        private final Organization.Factory _factOrg;

        public Factory(UserDatabase db, Organization.Factory factOrg)
        {
            _db = db;
            _factOrg = factOrg;
        }

        public User create(@Nonnull UserID id)
        {
            return new User(_db, _factOrg, id);
        }

        public User createFromExternalID(@Nonnull String str)
        {
            return create(UserID.fromExternal(str));
        }
    }

    private User(UserDatabase db, Organization.Factory factOrg, UserID id)
    {
        _id = id;
        _db = db;
        _factOrg = factOrg;
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
        return _db.hasUser(_id);
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
        return _factOrg.create(_db.getOrgID(_id));
    }

    public FullName getFullName()
            throws ExNotFound, SQLException
    {
        return _db.getFullName(_id);
    }

    /**
     * @return sha256(scrypt(p|u)|passwdSalt)
     */
    public byte[] getShaedSP()
            throws ExNotFound, SQLException
    {
        return _db.getShaedSP(_id);
    }

    public boolean isVerified()
            throws ExNotFound, SQLException
    {
        return _db.isVerified(_id);
    }

    public AuthorizationLevel getLevel()
            throws ExNotFound, SQLException
    {
        return _db.getLevel(_id);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setLevel(AuthorizationLevel auth)
            throws SQLException
    {
        _db.setLevel(_id, auth);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setVerified() throws SQLException
    {
        _db.setVerified(_id);
    }

    // TODO (WW) throw ExNotFound if the user doesn't exist?
    public void setName(FullName fullName) throws SQLException
    {
        _db.setName(_id, fullName);
    }

    /**
     * Add the user to the database
     * @throws ExAlreadyExist if the user ID already exists.
     */
    public void add(byte[] shaedSP, FullName fullName, Organization org)
            throws ExAlreadyExist, SQLException
    {
        Util.l(this).info(this + " attempts signup");

        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _db.addUser(_id, fullName, shaedSP, org.id(), AuthorizationLevel.USER);
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
    public void addAndMoveToOrganization(String orgName)
            throws ExNoPerm, SQLException, ExNotFound
    {
        // TODO: verify the calling user is allowed to create an organization
        // (check with the payment system)

        // only users in the default organization or admins can add organizations.
        if (!getOrganization().id().equals(OrgID.DEFAULT) &&
                getLevel() != AuthorizationLevel.ADMIN) {
            throw new ExNoPerm("you have no permission to create new teams");
        }

        Organization org = _factOrg.add(orgName);
        setOrganization(org);
        setLevel(AuthorizationLevel.ADMIN);
    }

    /**
     * Move the user to a new organization
     */
    private void setOrganization(Organization org)
            throws SQLException
    {
        _db.setOrgID(_id, org.id());
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
        _db.addSignupCode(code, inviter.id(), _id, org.id());
        return code;
    }

    public int getSignUpInvitationsQuota()
            throws ExNotFound, SQLException
    {
        return _db.getSignUpInvitationsQuota(_id);
    }

    public void setSignUpInvitationQuota(int quota)
            throws SQLException
    {
        _db.setSignUpInvitationsQuota(_id, quota);
    }

    public boolean isInvitedToSignUp()
            throws SQLException
    {
        return _db.isInvitedToSignUp(_id);
    }
}
