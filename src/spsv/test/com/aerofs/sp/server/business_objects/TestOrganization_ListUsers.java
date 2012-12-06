/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.FullName;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.OrganizationDatabase.UserInfo;
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.Organization.UserListAndQueryCount;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
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
        odb.add(orgId, "test");
        odb.add(nonQueriedOrgId, "dummy");

        setupUsers();
    }

    private void setupUsers()
            throws Exception
    {
        FullName fullName = new FullName("", "");

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            udb.addUser(UserID.fromInternal("user" + i + "@test.com"), fullName, "".getBytes(),
                    orgId, AuthorizationLevel.USER);
        }

        for (int i = 0; i < NUMBER_OF_ADMINS; i++) {
            udb.addUser(UserID.fromInternal("admin" + i + "@test.com"), fullName,
                    "".getBytes(), orgId, AuthorizationLevel.ADMIN);
        }

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            udb.addUser(UserID.fromInternal("user" + i + "@dummy.com"), fullName,
                    "".getBytes(), nonQueriedOrgId, AuthorizationLevel.USER);
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
        UserListAndQueryCount pair = org.listUsers(null, TOTAL_USERS, 0);
        assertEquals(TOTAL_USERS, pair._userInfoList.size());
        assertEquals(TOTAL_USERS, pair._count);
    }

    @Test
    public void shouldListUsersWithOffset()
            throws Exception
    {
        final int offset = 4;
        final int maxResults = 6;
        UserListAndQueryCount subsetPair = org.listUsers(null, maxResults, offset);
        UserListAndQueryCount allPair = org.listUsers(null, TOTAL_USERS, 0);
        assertEquals(maxResults, subsetPair._userInfoList.size());
        assertEquals(TOTAL_USERS, allPair._userInfoList.size());

        for (int index = 0; index < maxResults; index++) {
            UserInfo subsetUser = subsetPair._userInfoList.get(index);
            UserInfo allUser = allPair._userInfoList.get(index + offset);
            assertEquals(allUser._userId, subsetUser._userId);
        }
    }

    @Test
    public void shouldFindNoUsersWhenOrgIdIsNotInDB()
            throws Exception
    {
        UserListAndQueryCount pair = invalidOrg.listUsers(null, 10, 0);
        assertTrue(pair._userInfoList.isEmpty());
    }

    // ================================
    // = searchUsers block            =
    // ================================

    @Test
    public void shouldListSubSetOfUsersThatMatchSearchKey()
            throws Exception
    {
        UserListAndQueryCount pair = org.listUsers("user", TOTAL_USERS, 0);
        assertEquals(NUMBER_OF_USERS, pair._userInfoList.size());
        for (UserInfo user : pair._userInfoList) {
            assertTrue(user._userId.toString().contains("user"));
            assertFalse(user._userId.toString().contains("admin"));
        }

        assertEquals(NUMBER_OF_USERS, pair._count);
    }

    // ====================================
    // = listUsersWithAuthorization block =
    // ====================================

    @Test
    public void shouldListSubSetOfAllUsersWithUserLevelAuth()
            throws Exception
    {
        // search term is null if we want to find all the users.
        UserListAndQueryCount pair = org.listUsersAuth(null, AuthorizationLevel.USER,
                TOTAL_USERS, 0);
        assertEquals(NUMBER_OF_USERS, pair._userInfoList.size());
        for (UserInfo user : pair._userInfoList) {
            assertTrue(user._userId.toString().contains("user"));
            assertFalse(user._userId.toString().contains("admin"));
        }
        assertEquals(NUMBER_OF_USERS, pair._count);
    }

    // ======================================
    // = searchUsersWithAuthorization block =
    // ======================================

    @Test
    public void shouldListUser1ForSearchUsersWithAuthorization()
            throws Exception
    {
        UserListAndQueryCount pair = org.listUsersAuth("user1@", AuthorizationLevel.USER,
                TOTAL_USERS, 0);

        assertEquals(1, pair._userInfoList.size());
        assertEquals(UserID.fromInternal("user1@test.com"), pair._userInfoList.get(0)._userId);
        assertEquals(1, pair._count);
    }

    @Test
    public void shouldFindUsersWithPercentageSignForSearchUsersWithAuthorization()
            throws Exception
    {
        UserListAndQueryCount pair = org.listUsersAuth("user%", AuthorizationLevel.USER,
                TOTAL_USERS, 0);

        assertEquals(NUMBER_OF_USERS, pair._userInfoList.size());
    }

    @Test
    public void shouldNotFindUserWhenSearchCouldMatchButAuthLevelDiffers()
            throws Exception
    {
        UserListAndQueryCount pair = org.listUsersAuth("user1",
                AuthorizationLevel.ADMIN, TOTAL_USERS, 0);

        assertTrue(pair._userInfoList.isEmpty());
    }
}
