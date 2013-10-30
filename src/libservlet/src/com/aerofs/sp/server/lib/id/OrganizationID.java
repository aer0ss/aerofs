/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.id;

import com.aerofs.lib.Util;
import com.aerofs.lib.id.IntegerID;
import com.aerofs.base.id.UserID;

/**
 * Organization ID.
 *
 * Since clients are oblivious to organization IDs, this class should be only visible to servers.
 */
public class OrganizationID extends IntegerID
{
    // In private deployment, new users are all created by default in the same organization,
    // also known as the private organization.
    //
    // Note: we use "2" as the id instead of "1" because 1 is already assigned to our own internal
    // AeroFS organization. It really shouldn't matter though since those ids should never coexist
    // together, but having different numbers makes reading log files easier.
    public static final OrganizationID PRIVATE_ORGANIZATION = new OrganizationID(2);

    static {
        // assert the team server prefix is an invalid email address char. This check can be done
        // in UserID. But I don't really want to slow down client launch time any further.
        assert !Util.isValidEmailAddressToken(String.valueOf(UserID.TEAM_SERVER_PREFIX));
    }

    public OrganizationID(int i)
    {
        super(i);
    }

    public UserID toTeamServerUserID()
    {
        StringBuilder sb = new StringBuilder().append(UserID.TEAM_SERVER_PREFIX);
        sb.append(toHexString());
        return UserID.fromInternal(sb.toString());
    }

    @Override
    public String toString()
    {
        return Integer.toHexString(getInt());
    }

    public String toHexString()
    {
        return Integer.toHexString(getInt());
    }
}
