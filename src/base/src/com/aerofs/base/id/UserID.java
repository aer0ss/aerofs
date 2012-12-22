/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.id;

import com.aerofs.base.ex.ExFormatError;
import com.google.common.collect.Lists;

import java.util.List;

public class UserID extends StringID
{
    public final static char TEAM_SERVER_PREFIX = ':';

    public final static UserID UNKNOWN = UserID.fromInternal("(unknown)");

    /**
     * This method normalizes {@code str}, ie. convert {@code str} to lower case, before using it to
     * construct the UserID.
     *
     * Use this constructor to hold UserIDs provided by external input channels like APIs, UI, etc.
     */
    public static UserID fromExternal(String str)
    {
        // TODO (WW) throw or assert if str is empty?
        return new UserID(str.toLowerCase());
    }

    /**
     * This method uses {@code str} as is to construct a UserID, without normalization.
     *
     * Use it to hold UserIDs retreived from internal storage managed by AeroFS or those returned
     * _from_ AeroFS.
     */
    public static UserID fromInternal(String str)
    {
        assert !str.isEmpty();
        // assert the string is already normalized
        assert isNormalized(str);
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
        return toString().charAt(0) == TEAM_SERVER_PREFIX;
    }

    /**
     * This method is used mostly to convert a list of user IDs to Protubuf messages
     */
    public static List<String> toStrings(Iterable<UserID> userIDs)
    {
        List<String> strs = Lists.newArrayListWithExpectedSize(8);
        for (UserID userId : userIDs) strs.add(userId.toString());
        return strs;
    }

    /**
     * Call fromExternal() on a list of strings
     */
    public static List<UserID> fromExternal(List<String> strs)
    {
        List<UserID> userIDs = Lists.newArrayListWithExpectedSize(8);
        for (String userId : strs) userIDs.add(UserID.fromExternal(userId));
        return userIDs;
    }

    private UserID(String str)
    {
        super(str);
    }
}
