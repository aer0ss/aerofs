/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp.user;

import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.sp.server.sp.SPParam;
import com.google.protobuf.ByteString;

public final class User
{
    public final String _id;
    public final String _firstName;
    public final String _lastName;
    public final byte[] _shaedSP; // sha256(scrypt(p|u)|passwdSalt)
    public final boolean _isFinalized;
    public final boolean _isVerified;
    public final String _orgId;
    public final AuthorizationLevel _level;

    public User(String id, String firstName, String lastName, byte[] shaedSP, boolean finalized,
            boolean verified, String orgId, AuthorizationLevel level)
    {
        _id = id;
        _firstName = firstName;
        _lastName = lastName;
        _shaedSP = shaedSP;
        _isFinalized = finalized;
        _isVerified = verified;
        _orgId = orgId;
        _level = level;
    }

    /**
     * Copy the given User, but replace finalized with that provided
     */
    public User(User user, boolean finalized)
    {
        this(user._id, user._firstName, user._lastName, user._shaedSP, finalized, user._isVerified,
                user._orgId, user._level);
    }

    /**
     * Create a User using protobuf credentials instead of byte array
     */
    public User(String userId, String firstName, String lastName, ByteString credentials,
            boolean finalized, boolean verified, String orgId, AuthorizationLevel level)
    {
        this(userId, firstName, lastName, SPParam.getShaedSP(credentials.toByteArray()), finalized,
                verified, orgId, level);
    }

    public static String normalizeUserId(String userId)
    {
        return userId.toLowerCase();
    }

    public void verifyIsAdmin()
            throws ExNoPerm
    {
        if (_level != AuthorizationLevel.ADMIN) {
            throw new ExNoPerm("User " + _id + " does not have administrator privileges");
        }
    }

    @Override
    public String toString()
    {
        return _id + "(" + _firstName + " " + _lastName + ") with <" + _orgId
                + "> auth " + _level;
    }
}
