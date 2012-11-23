/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.google.protobuf.ByteString;

public final class User
{
    public final String _id;
    public final String _firstName;
    public final String _lastName;
    public final byte[] _shaedSP; // sha256(scrypt(p|u)|passwdSalt)
    public final boolean _isVerified;
    public final OrgID _orgID;
    public final AuthorizationLevel _level;

    public User(String id, String firstName, String lastName, byte[] shaedSP,
            boolean verified, OrgID orgID, AuthorizationLevel level)
    {
        _id = id;
        _firstName = firstName;
        _lastName = lastName;
        _shaedSP = shaedSP;
        _isVerified = verified;
        _orgID = orgID;
        _level = level;
    }

    /**
     * Create a User using protobuf credentials instead of byte array
     */
    public User(String userId, String firstName, String lastName, ByteString credentials,
            boolean verified, OrgID orgID, AuthorizationLevel level)
    {
        this(userId, firstName, lastName, SPParam.getShaedSP(credentials.toByteArray()),
                verified, orgID, level);
    }

    /**
     * TODO (WW) create a UserID class and move this method there (as a constructor)
     */
    public static String normalizeUserId(String userId)
    {
        return userId.toLowerCase();
    }

    // TODO (WW) why is this test-specific method in the main code? Move it out
    public static User createMockForID(String userId)
    {
        return new User(userId, "first", "last", SecUtil.newRandomBytes(10),
                false, OrgID.DEFAULT, AuthorizationLevel.USER);
    }

    public void throwIfNotAdmin()
            throws ExNoPerm
    {
        if (_level != AuthorizationLevel.ADMIN) {
            throw new ExNoPerm("User " + _id + " does not have administrator privileges");
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
        return this == o || (o != null && _id.equals(((User) o)._id));
    }

    @Override
    public String toString()
    {
        return _id + "(" + _firstName + " " + _lastName + ") with <" + _orgID + "> auth " + _level;
    }
}
