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
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.Arrays;

public class User
{
    private final static Logger l = Util.l(User.class);

    private final UserID _id;
    private final UserDatabase _db;

    public static class Factory
    {
        private final UserDatabase _db;

        public Factory(UserDatabase db)
        {
            _db = db;
        }

        public User create(UserID id)
        {
            return new User(_db, id);
        }

        public User createFromExternalID(String str)
        {
            return new User(_db, UserID.fromExternal(str));
        }
    }

    private User(UserDatabase db, UserID id)
    {
        _id = id;
        _db = db;
    }

    public void throwIfNotAdmin()
            throws ExNoPerm, ExNotFound, SQLException
    {
        if (getLevel() != AuthorizationLevel.ADMIN) {
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
        return "User:" + _id.toString();
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


    // TODO (WW) return an Organization object
    public OrgID getOrgID()
            throws ExNotFound, SQLException
    {
        return _db.getOrgID(_id);
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

    public void setLevel(AuthorizationLevel auth)
            throws SQLException
    {
        _db.setLevel(_id, auth);
    }

    public void setVerified() throws SQLException
    {
        _db.setVerified(_id);
    }

    /**
     * Add the user to the database
     * @throws ExAlreadyExist if the user ID already exists.
     */
    public void add(byte[] shaedSP, FullName fullName, OrgID orgID)
            throws ExAlreadyExist, SQLException
    {
        Util.l(this).info(this + " attempts signup");

        // TODO If successful, this method should delete all the user's existing signup codes from
        // the signup_code table
        // TODO write a test to verify that after one successful signup,
        // other codes fail/do not exist
        _db.addUser(_id, fullName, shaedSP, orgID, AuthorizationLevel.USER);
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

}
