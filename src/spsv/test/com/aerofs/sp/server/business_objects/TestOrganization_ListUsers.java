/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.lib.FullName;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestOrganization_ListUsers extends AbstractBusinessObjectTest
{
    private final int NUMBER_OF_USERS = 10;
    private final int NUMBER_OF_ADMINS = 4;
    private final int TOTAL_USERS = NUMBER_OF_USERS + NUMBER_OF_ADMINS;
    private final OrganizationID orgId = new OrganizationID(523);
    private final OrganizationID invalidOrgId = new OrganizationID(876);
    // Organization containing users, but will not be queried.
    // We do not expect any of these users to be returned in the queries below.
    private final OrganizationID nonQueriedOrgId = new OrganizationID(453);

    Organization org = factOrg.create(orgId);
    Organization invalidOrg = factOrg.create(invalidOrgId);

    @Before
    public void setup()
        throws Exception
    {
        odb.insert(orgId);
        odb.insert(nonQueriedOrgId);

        setupUsers();
    }

    private void setupUsers()
            throws Exception
    {
        FullName fullName = new FullName("", "");

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            udb.insertUser(UserID.fromInternal("user" + i + "@test.com"), fullName, "".getBytes(),
                    orgId, AuthorizationLevel.USER, true);
        }

        for (int i = 0; i < NUMBER_OF_ADMINS; i++) {
            udb.insertUser(UserID.fromInternal("admin" + i + "@test.com"), fullName, "".getBytes(),
                    orgId, AuthorizationLevel.ADMIN, true);
        }

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            udb.insertUser(UserID.fromInternal("user" + i + "@dummy.com"), fullName, "".getBytes(),
                    nonQueriedOrgId, AuthorizationLevel.USER, true);
        }
    }

    @Test
    public void shouldNotListTeamServerUsers()
            throws SQLException, ExAlreadyExist, ExBadArgs
    {
        udb.insertUser(orgId.toTeamServerUserID(), new FullName("", ""), "".getBytes(),
                orgId, AuthorizationLevel.USER, true);

        for (User u : org.listUsers(TOTAL_USERS, 0)) {
            assertFalse(u.id().isTeamServerID());
        }
    }

    @Test
    public void shouldListAllUsersForListUsers()
            throws Exception
    {
        Collection<User> users = org.listUsers(TOTAL_USERS, 0);
        assertEquals(TOTAL_USERS, users.size());
    }

    @Test
    public void shouldListUsersWithOffset()
            throws Exception
    {
        final int offset = 4;
        final int maxResults = 6;
        List<User> subsetUsers = org.listUsers(maxResults, offset);
        List<User> allUsers = org.listUsers(TOTAL_USERS, 0);
        assertEquals(maxResults, subsetUsers.size());
        assertEquals(TOTAL_USERS, allUsers.size());

        for (int index = 0; index < maxResults; index++) {
            User subsetUser = subsetUsers.get(index);
            User allUser = allUsers.get(index + offset);
            assertEquals(allUser, subsetUser);
        }
    }

    @Test
    public void shouldFindNoUsersWhenOrgIdIsNotInDB()
            throws Exception
    {
        assertTrue(invalidOrg.listUsers(10, 0).isEmpty());
    }
}
