/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.lib.organization;

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
    // The default organization value must be identical to the default id specified in sp.sql.
    static public OrganizationID DEFAULT = new OrganizationID(0);

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
        sb.append(Integer.toHexString(getInt()));
        return UserID.fromInternal(sb.toString());
    }

    @Override
    public String toString()
    {
        return Integer.toHexString(getInt());
    }
}
