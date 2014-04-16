/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.base.id.UserID;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.google.common.collect.Lists;

import java.util.List;

public class DryadService
{
    private final SQLThreadLocalTransaction     _sqlTrans;

    private final OrganizationDatabase          _dbOrg;

    public DryadService(SQLThreadLocalTransaction sqlTrans, OrganizationDatabase dbOrg)
    {
        _sqlTrans = sqlTrans;
        _dbOrg = dbOrg;
    }

    public List<String> listUserIDs(int offset, int maxResult)
            throws Exception
    {
        _sqlTrans.begin();
        List<UserID> userIDs = _dbOrg.listUsers(OrganizationID.PRIVATE_ORGANIZATION,
                offset, maxResult);
        _sqlTrans.commit();

        List<String> results = Lists.newArrayListWithCapacity(userIDs.size() + 1);
        results.add(OrganizationID.PRIVATE_ORGANIZATION.toTeamServerUserID().getString());

        for (UserID userID : userIDs) {
            results.add(userID.getString());
        }

        return results;
    }
}
