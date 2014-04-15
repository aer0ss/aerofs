/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.id.OrganizationID;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.Organization.Factory;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;

import java.util.List;

public class DryadService
{
    private final SQLThreadLocalTransaction     _sqlTrans;

    private final Organization.Factory          _factOrg;

    public DryadService(SQLThreadLocalTransaction sqlTrans,
            Factory factOrg)
    {
        _sqlTrans = sqlTrans;
        _factOrg = factOrg;
    }

    public List<String> listUserIDs(int offset, int maxResult)
            throws Exception
    {
        _sqlTrans.begin();
        Organization org = createPrivateOrganization();
        List<User> users = Lists.newArrayList(org.getTeamServerUser());
        users.addAll(org.listUsers(maxResult, offset));
        _sqlTrans.commit();

        List<String> userIDs = Lists.newArrayListWithCapacity(users.size());

        for (User user : users) {
            userIDs.add(user.id().getString());
        }

        return userIDs;
    }

    private Organization createPrivateOrganization()
    {
        return _factOrg.create(OrganizationID.PRIVATE_ORGANIZATION);
    }
}
