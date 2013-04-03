package com.aerofs.sp.server.lib.user;

import com.aerofs.proto.Sp.PBAuthorizationLevel;

import javax.annotation.Nonnull;

/**
 * User authorization levels for SP services
 */
public enum AuthorizationLevel
{
    // N.B. user levels with more permissions must go after those with fewer
    USER,
    ADMIN;

    @Override
    public String toString()
    {
        return "#" + super.toString() + "#";
    }

    public static AuthorizationLevel fromPB(PBAuthorizationLevel pb)
            throws IllegalArgumentException
    {
        switch (pb) {
        case USER: return USER;
        case ADMIN: return ADMIN;
        default:
            throw new IllegalArgumentException(pb + " not supported");
        }
    }

    public PBAuthorizationLevel toPB()
    {
        switch (this) {
        case USER : return PBAuthorizationLevel.USER;
        case ADMIN: return PBAuthorizationLevel.ADMIN;
        default: assert false; return null;
        }
    }

    public static AuthorizationLevel fromOrdinal(int ordinal)
    {
        return AuthorizationLevel.values()[ordinal];
    }

    /**
     * @return true if 'this' dominates or matches the authorization level of {@code level}
     */
    public boolean covers(@Nonnull AuthorizationLevel level)
    {
        return compareTo(level) >= 0;
    }
}
