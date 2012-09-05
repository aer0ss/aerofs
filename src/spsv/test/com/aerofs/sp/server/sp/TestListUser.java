/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.sp;

import com.aerofs.proto.Sp.PBUser;
import com.aerofs.servletlib.db.JUnitDatabaseConnectionFactory;
import com.aerofs.servletlib.db.JUnitSPDatabaseParams;
import com.aerofs.servletlib.db.LocalTestDatabaseConfigurator;
import com.aerofs.servletlib.sp.SPDatabase;
import com.aerofs.servletlib.sp.organization.Organization;
import com.aerofs.servletlib.sp.user.AuthorizationLevel;
import com.aerofs.servletlib.sp.user.User;
import com.aerofs.sp.server.sp.user.UserManagement;
import com.aerofs.sp.server.sp.user.UserManagement.UserListAndQueryCount;
import com.aerofs.testlib.AbstractTest;
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
    private final String validOrgId = "test.com";
    private final String invalidOrgId = "notfound.com";
    // Organization containig users, but will not be queried.
    // We do not expect any of these users to be returned in the queries below.
    private final String nonQueriedOrgId = "dummy.com";

    private final JUnitSPDatabaseParams _dbParams = new JUnitSPDatabaseParams();
    @Spy private SPDatabase _spdb
            = new SPDatabase(new JUnitDatabaseConnectionFactory(_dbParams));

    @InjectMocks UserManagement userManagement;

    @Before
    public void setup()
        throws Exception
    {
        new LocalTestDatabaseConfigurator(_dbParams).configure_();
        _spdb.init_();

        Organization[] orgs = new Organization[] {
                new Organization(validOrgId, "test", "", false),
                new Organization(nonQueriedOrgId, "dummy", "", false)
        };

        for (Organization org : orgs) _spdb.addOrganization(org);

        setupUsers();
    }

    private void setupUsers()
            throws Exception
    {
        for (int i=0; i < NUMBER_OF_USERS; i++) {
            User user = new User("user"+i+"@test.com", "", "", "".getBytes(),
                    true, false, validOrgId, AuthorizationLevel.USER);

            _spdb.addUser(user, true);
        }

        for (int i=0; i < NUMBER_OF_ADMINS; i++) {
            User admin = new User("admin"+i+"@test.com", "", "", "".getBytes(),
                    true, false, validOrgId, AuthorizationLevel.ADMIN);

            _spdb.addUser(admin, true);
        }

        for (int i=0; i < NUMBER_OF_USERS; i++) {
            User user = new User("user"+i+"@dummy.com", "", "", "".getBytes(),
                    true, false, nonQueriedOrgId, AuthorizationLevel.USER);
            _spdb.addUser(user, true);
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
        assertEquals(TOTAL_USERS, pair.users.size());
        assertEquals(TOTAL_USERS, pair.count);
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
        assertEquals(maxResults, subsetPair.users.size());
        assertEquals(TOTAL_USERS, allPair.users.size());

        for (int index = 0; index < maxResults; index++) {
            PBUser subsetUser = subsetPair.users.get(index);
            PBUser allUser = allPair.users.get(index + offset);
            assertEquals(allUser.getUserEmail(), subsetUser.getUserEmail());
        }
    }

    @Test
    public void shouldFindNoUsersWhenOrgIdIsNotInDB()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsers(null, 10, 0, invalidOrgId);
        assertTrue(pair.users.isEmpty());
    }

    // ================================
    // = searchUsers block            =
    // ================================

    @Test
    public void shouldListSubSetOfUsersThatMatchSearchKey()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsers("user", TOTAL_USERS, 0, validOrgId);
        assertEquals(NUMBER_OF_USERS, pair.users.size());
        for (PBUser user : pair.users) {
            assertTrue(user.getUserEmail().contains("user"));
            assertFalse(user.getUserEmail().contains("admin"));
        }

        assertEquals(NUMBER_OF_USERS, pair.count);
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
        assertEquals(NUMBER_OF_USERS, pair.users.size());
        for (PBUser user : pair.users) {
            assertTrue(user.getUserEmail().contains("user"));
            assertFalse(user.getUserEmail().contains("admin"));
        }
        assertEquals(NUMBER_OF_USERS, pair.count);
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

        assertEquals(1, pair.users.size());
        assertEquals("user1@test.com", pair.users.get(0).getUserEmail());
        assertEquals(1, pair.count);
    }

    @Test
    public void shouldFindUsersWithPercentageSignForSearchUsersWithAuthorization()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsersAuth("user%", AuthorizationLevel.USER,
                TOTAL_USERS, 0, validOrgId);

        assertEquals(NUMBER_OF_USERS, pair.users.size());
    }

    @Test
    public void shouldNotFindUserWhenSearchCouldMatchButAuthLevelDiffers()
            throws Exception
    {
        UserListAndQueryCount pair = userManagement.listUsersAuth("user1",
                AuthorizationLevel.ADMIN, TOTAL_USERS, 0, validOrgId);

        assertTrue(pair.users.isEmpty());
    }
}
