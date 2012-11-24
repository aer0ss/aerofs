/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.id.UserID;
import com.aerofs.servlets.lib.db.SPDatabaseParams;
import com.aerofs.servlets.lib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.IUserSearchDatabase.UserInfo;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.sp.server.user.UserManagement.UserListAndQueryCount;
import com.aerofs.testlib.AbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class TestListUser extends AbstractTest
{
    private final int NUMBER_OF_USERS = 10;
    private final int NUMBER_OF_ADMINS = 4;
    private final int TOTAL_USERS = NUMBER_OF_USERS + NUMBER_OF_ADMINS;
    private final OrgID validOrgId = new OrgID(523);
    private final OrgID invalidOrgId = new OrgID(876);
    // Organization containing users, but will not be queried.
    // We do not expect any of these users to be returned in the queries below.
    private final OrgID nonQueriedOrgId = new OrgID(453);

    private final SPDatabaseParams _dbParams = new SPDatabaseParams();
    @Spy private final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_dbParams.getProvider());
    @Spy private final SPDatabase _spdb = new SPDatabase(_transaction);
    @InjectMocks UserManagement userManagement;

    @Before
    public void setup()
        throws Exception
    {
        LocalTestDatabaseConfigurator.initializeLocalDatabase(_dbParams);

        // we can manage the transaction at this level because none of the tests are expected to
        // throw exceptions
        _transaction.begin();

        Organization[] orgs = new Organization[] {
                new Organization(validOrgId, "test"),
                new Organization(nonQueriedOrgId, "dummy")
        };

        for (Organization org : orgs) _spdb.addOrganization(org);

        setupUsers();
    }

    @After
    public void tearDown()
        throws Exception
    {
        _transaction.commit();
    }

    private void setupUsers()
            throws Exception
    {
        for (int i=0; i < NUMBER_OF_USERS; i++) {
            User user = new User(UserID.fromInternal("user" + i + "@test.com"), "", "", "".getBytes(),
                    false, validOrgId, AuthorizationLevel.USER);

            _spdb.addUser(user);
        }

        for (int i=0; i < NUMBER_OF_ADMINS; i++) {
            User admin = new User(UserID.fromInternal("admin" + i + "@test.com"), "", "", "".getBytes(),
                    false, validOrgId, AuthorizationLevel.ADMIN);

            _spdb.addUser(admin);
        }

        for (int i=0; i < NUMBER_OF_USERS; i++) {
            User user = new User(UserID.fromInternal("user" + i + "@dummy.com"), "", "", "".getBytes(),
                    false, nonQueriedOrgId, AuthorizationLevel.USER);
            _spdb.addUser(user);
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
        UserListAndQueryCount pair = userManagement.listUsers(null, TOTAL_USERS, 0, validOrgId);
        assertEquals(TOTAL_USERS, pair._uis.size());
        assertEquals(TOTAL_USERS, pair._count);
    }

    @Test
    public void shouldListUsersWithOffset()
            throws Exception
    {
        final int offset = 4;
        final int maxResults = 6;
        UserListAndQueryCount subsetPair = userManagement.listUsers(null, maxResults,
                offset, validOrgId);
        UserListAndQueryCount allPair = userManagement.listUsers(null, TOTAL_USERS,
                0, validOrgId);
        assertEquals(maxResults, subsetPair._uis.size());
        assertEquals(TOTAL_USERS, allPair._uis.size());

        for (int index = 0; index < maxResults; index++) {
            UserInfo subsetUser = subsetPair._uis.get(index);
            UserInfo allUser = allPair._uis.get(index + offset);
            assertEquals(allUser._userId, subsetUser._userId);
        }
    }

    @Test
    public void shouldFindNoUsersWhenOrgIdIsNotInDB()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsers(null, 10, 0, invalidOrgId);
        assertTrue(pair._uis.isEmpty());
    }

    // ================================
    // = searchUsers block            =
    // ================================

    @Test
    public void shouldListSubSetOfUsersThatMatchSearchKey()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsers("user", TOTAL_USERS, 0, validOrgId);
        assertEquals(NUMBER_OF_USERS, pair._uis.size());
        for (UserInfo user : pair._uis) {
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
        UserListAndQueryCount pair = userManagement.listUsersAuth(null, AuthorizationLevel.USER,
                TOTAL_USERS, 0, validOrgId);
        assertEquals(NUMBER_OF_USERS, pair._uis.size());
        for (UserInfo user : pair._uis) {
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
        UserListAndQueryCount pair = userManagement.listUsersAuth("user1@", AuthorizationLevel.USER,
                TOTAL_USERS, 0, validOrgId);

        assertEquals(1, pair._uis.size());
        assertEquals(UserID.fromInternal("user1@test.com"), pair._uis.get(0)._userId);
        assertEquals(1, pair._count);
    }

    @Test
    public void shouldFindUsersWithPercentageSignForSearchUsersWithAuthorization()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsersAuth("user%", AuthorizationLevel.USER,
                TOTAL_USERS, 0, validOrgId);

        assertEquals(NUMBER_OF_USERS, pair._uis.size());
    }

    @Test
    public void shouldNotFindUserWhenSearchCouldMatchButAuthLevelDiffers()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsersAuth("user1",
                AuthorizationLevel.ADMIN, TOTAL_USERS, 0, validOrgId);

        assertTrue(pair._uis.isEmpty());
    }
}
