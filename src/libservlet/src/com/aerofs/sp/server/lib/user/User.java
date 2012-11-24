/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.user;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.google.protobuf.ByteString;

public final class User
{
    private final UserID _id;
    private final OrgID _orgID;
    private final String _firstName;
    private final String _lastName;
    private final byte[] _shaedSP; // sha256(scrypt(p|u)|passwdSalt)
    private final boolean _isVerified;
    private final AuthorizationLevel _level;

    public User(UserID id, String firstName, String lastName, byte[] shaedSP,
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
    public User(UserID userId, String firstName, String lastName, ByteString credentials,
            boolean verified, OrgID orgID, AuthorizationLevel level)
    {
        this(userId, firstName, lastName, SPParam.getShaedSP(credentials.toByteArray()),
                verified, orgID, level);
    }

    // TODO (WW) why is this test-specific method in the main code? Move it out
    public static User createMockForID(UserID userId)
    {
        return new User(userId, "first", "last", SecUtil.newRandomBytes(10),
                false, OrgID.DEFAULT, AuthorizationLevel.USER);
    }

    public void throwIfNotAdmin()
            throws ExNoPerm
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

    public OrgID getOrgID()
    {
        return _orgID;
    }

    public String getFirstName()
    {
        return _firstName;
    }

    public String getLastName()
    {
        return _lastName;
    }

    public byte[] getShaedSP()
    {
        return _shaedSP;
    }

    public boolean isVerified()
    {
        return _isVerified;
    }

    public AuthorizationLevel getLevel()
    {
        return _level;
    }
}
