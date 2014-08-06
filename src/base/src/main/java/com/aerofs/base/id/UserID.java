/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.id;

import com.aerofs.base.ex.ExEmptyEmailAddress;
import com.aerofs.base.ex.ExFormatError;
import com.google.common.base.Preconditions;

public class UserID extends StringID
{
    /**
     * Must be consistent with src/web/web/util.py:is_team_server_user_id()
     */
    public final static char TEAM_SERVER_PREFIX = ':';

    // NB: must not be empty
    public final static UserID DUMMY = new UserID("@");

    public final static UserID UNKNOWN = UserID.fromInternal("(unknown)");

    public final static UserID UNKNOWN_TEAM_SERVER = UserID.fromInternal("" + TEAM_SERVER_PREFIX
            + "unknown");

    private static final String AEROFS_EMAIL_DOMAIN = "@aerofs.com";

    /**
     * This method normalizes {@code str}, ie. trim and convert {@code str} to lower case before
     * using it to construct the UserID.
     *
     * Use this constructor to hold UserIDs provided by external input channels like APIs, UI, etc.
     */
    public static UserID fromExternal(String str)
            throws ExEmptyEmailAddress
    {
        str = str.trim();
        // Don't check for validity of email address characters. It is too expensive. Do the check
        // at API entry points.
        if (str.isEmpty()) throw new ExEmptyEmailAddress();
        return new UserID(str.toLowerCase());
    }

    /**
     * This method uses {@code str} as is to construct a UserID, without normalization.
     *
     * Use it to hold UserIDs retrieved from internal storage managed by AeroFS or those returned
     * _from_ AeroFS.
     */
    public static UserID fromInternal(String str)
    {
        Preconditions.checkState(!str.isEmpty());
        // assert the string is already normalized
        Preconditions.checkState(isNormalized(str));
        return new UserID(str);
    }

    /**
     * Similar to fromInternal(), but throw instead of assertion failure if {@code str} is not
     * normalized. This is to prevent DoS attacks where the peer sends an unnormalized string.
     */
    public static UserID fromInternalThrowIfNotNormalized(String str)
            throws ExFormatError
    {
        if (!isNormalized(str)) throw new ExFormatError("unnormalized user ID");
        return fromInternal(str);
    }

    private static boolean isNormalized(String str)
    {
        return str.toLowerCase().equals(str);
    }

    public boolean isTeamServerID()
    {
        return getString().charAt(0) == TEAM_SERVER_PREFIX;
    }

    public boolean isAeroFSUser()
    {
        return getString().endsWith(AEROFS_EMAIL_DOMAIN);
    }

    private UserID(String str)
    {
        super(str);
    }
}
