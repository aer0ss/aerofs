/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.C;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.sp.server.lib.SPParam;
import com.google.protobuf.ByteString;

public final class User
{
    public final String _id;
    public final String _firstName;
    public final String _lastName;
    public final byte[] _shaedSP; // sha256(scrypt(p|u)|passwdSalt)
    public final boolean _isVerified;
    public final String _orgId;
    public final AuthorizationLevel _level;

    public User(String id, String firstName, String lastName, byte[] shaedSP,
            boolean verified, String orgId, AuthorizationLevel level)
    {
        _id = id;
        _firstName = firstName;
        _lastName = lastName;
        _shaedSP = shaedSP;
        _isVerified = verified;
        _orgId = orgId;
        _level = level;
    }

    /**
     * Create a User using protobuf credentials instead of byte array
     */
    public User(String userId, String firstName, String lastName, ByteString credentials,
            boolean verified, String orgId, AuthorizationLevel level)
    {
        this(userId, firstName, lastName, SPParam.getShaedSP(credentials.toByteArray()),
                verified, orgId, level);
    }

    public static String normalizeUserId(String userId)
    {
        return userId.toLowerCase();
    }

    public static User createMockForID(String userId)
    {
        return new User(userId, "first", "last", SecUtil.newRandomBytes(10),
                false, C.DEFAULT_ORGANIZATION, AuthorizationLevel.USER);
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
