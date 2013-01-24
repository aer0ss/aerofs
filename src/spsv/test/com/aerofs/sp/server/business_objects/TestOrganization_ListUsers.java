/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.id.StripeCustomerID;
import com.aerofs.lib.FullName;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.organization.Organization.UsersAndQueryCount;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

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
        odb.insert(orgId, "test", null, null, StripeCustomerID.TEST);
        odb.insert(nonQueriedOrgId, "dummy", null, null, StripeCustomerID.TEST);

        setupUsers();
    }

    private void setupUsers()
            throws Exception
    {
        FullName fullName = new FullName("", "");

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            udb.insertUser(UserID.fromInternal("user" + i + "@test.com"), fullName, "".getBytes(),
                    orgId, AuthorizationLevel.USER);
        }

        for (int i = 0; i < NUMBER_OF_ADMINS; i++) {
            udb.insertUser(UserID.fromInternal("admin" + i + "@test.com"), fullName, "".getBytes(),
                    orgId, AuthorizationLevel.ADMIN);
        }

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            udb.insertUser(UserID.fromInternal("user" + i + "@dummy.com"), fullName, "".getBytes(),
                    nonQueriedOrgId, AuthorizationLevel.USER);
        }
    }

    // ================================
    // = listUser block               =
    // ================================

    @Test
    public void shouldListAllUsersForListUsers()
            throws Exception
    {
        // search term null means search for all
        UsersAndQueryCount pair = org.listUsers(null, TOTAL_USERS, 0);
        assertEquals(TOTAL_USERS, pair.users().size());
        assertEquals(TOTAL_USERS, pair.count());
    }

    @Test
    public void shouldListUsersWithOffset()
            throws Exception
    {
        final int offset = 4;
        final int maxResults = 6;
        UsersAndQueryCount subsetPair = org.listUsers(null, maxResults, offset);
        UsersAndQueryCount allPair = org.listUsers(null, TOTAL_USERS, 0);
        assertEquals(maxResults, subsetPair.users().size());
        assertEquals(TOTAL_USERS, allPair.users().size());

        for (int index = 0; index < maxResults; index++) {
            User subsetUser = subsetPair.users().get(index);
            User allUser = allPair.users().get(index + offset);
            assertEquals(allUser, subsetUser);
        }
    }

    @Test
    public void shouldFindNoUsersWhenOrgIdIsNotInDB()
            throws Exception
    {
        UsersAndQueryCount pair = invalidOrg.listUsers(null, 10, 0);
        assertTrue(pair.users().isEmpty());
    }

    // ================================
    // = searchUsers block            =
    // ================================

    @Test
    public void shouldListSubSetOfUsersThatMatchSearchKey()
            throws Exception
    {
        UsersAndQueryCount pair = org.listUsers("user", TOTAL_USERS, 0);
        assertEquals(NUMBER_OF_USERS, pair.users().size());
        for (User user : pair.users()) {
            assertTrue(user.id().toString().contains("user"));
            assertFalse(user.id().toString().contains("admin"));
        }

        assertEquals(NUMBER_OF_USERS, pair.count());
    }

    // ====================================
    // = listUsersWithAuthorization block =
    // ====================================

    @Test
    public void shouldListSubSetOfAllUsersWithUserLevelAuth()
            throws Exception
    {
        // search term is null if we want to find all the users.
        UsersAndQueryCount pair = org.listUsersAuth(null, AuthorizationLevel.USER,
                TOTAL_USERS, 0);
        assertEquals(NUMBER_OF_USERS, pair.users().size());
        for (User user : pair.users()) {
            assertTrue(user.id().toString().contains("user"));
            assertFalse(user.id().toString().contains("admin"));
        }
        assertEquals(NUMBER_OF_USERS, pair.count());
    }

    // ======================================
    // = searchUsersWithAuthorization block =
    // ======================================

    @Test
    public void shouldListUser1ForSearchUsersWithAuthorization()
            throws Exception
    {
        UsersAndQueryCount pair = org.listUsersAuth("user1@", AuthorizationLevel.USER,
                TOTAL_USERS, 0);

        assertEquals(1, pair.users().size());
        assertEquals(UserID.fromInternal("user1@test.com"), pair.users().get(0).id());
        assertEquals(1, pair.count());
    }

    @Test
    public void shouldFindUsersWithPercentageSignForSearchUsersWithAuthorization()
            throws Exception
    {
        UsersAndQueryCount pair = org.listUsersAuth("user%", AuthorizationLevel.USER,
                TOTAL_USERS, 0);

        assertEquals(NUMBER_OF_USERS, pair.users().size());
    }

    @Test
    public void shouldNotFindUserWhenSearchCouldMatchButAuthLevelDiffers()
            throws Exception
    {
        UsersAndQueryCount pair = org.listUsersAuth("user1",
                AuthorizationLevel.ADMIN, TOTAL_USERS, 0);

        assertTrue(pair.users().isEmpty());
    }
}
