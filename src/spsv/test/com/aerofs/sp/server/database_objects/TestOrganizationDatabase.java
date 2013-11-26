/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.database_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class TestOrganizationDatabase extends AbstractAutoTransactionedTestWithSPDatabase
{
    UserDatabase udb = new UserDatabase(sqlTrans);
    OrganizationDatabase odb = new OrganizationDatabase(sqlTrans);
    SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);

    OrganizationID orgID = new OrganizationID(123);
    private SID sid = SID.generate();

    @Before
    public void setUp() throws Exception
    {
        odb.insert(orgID);
        addSharedFolder(SID.rootSID(UserID.fromInternal("hahaha")));
        addSharedFolder(sid);
        udb.insertUser(UserID.fromInternal("foo"), new FullName("foo", "bar"), new byte[0], orgID,
                AuthorizationLevel.ADMIN);
        udb.insertUser(UserID.fromInternal("bar"), new FullName("bar", "baz"), new byte[0], orgID,
                AuthorizationLevel.USER);
        udb.insertUser(UserID.fromInternal("baz"), new FullName("baz", "qux"), new byte[0], orgID,
                AuthorizationLevel.USER);
        udb.deactivate(UserID.fromInternal("baz"));
    }

    @Test
    public void countUsers_shouldIgnoreTeamServerAndDeactivated() throws Exception
    {
        assertEquals(2, odb.countUsers(orgID));
    }

    @Test
    public void listUsers_shouldIgnoreTeamServerAndDeactivated() throws Exception
    {
        assertEquals(2, odb.listUsers(orgID, 0, 100).size());
        assertThat(odb.listUsers(orgID, 0, 100), hasItem(UserID.fromInternal("foo")));
        assertThat(odb.listUsers(orgID, 0, 100), hasItem(UserID.fromInternal("bar")));
    }

    @Test
    public void listSharedFolders_shouldSkipRootStores() throws Exception
    {
        assertEquals(1, odb.listSharedFolders(orgID, 100, 0).size());
        assertTrue(odb.listSharedFolders(orgID, 100, 0).contains(sid));
    }

    @Test
    public void countSharedFolders_shouldSkipRootStores() throws Exception
    {
        assertEquals(1, odb.countSharedFolders(orgID));
    }

    private void addSharedFolder(SID rootsid) throws Exception
    {
        sfdb.insert(rootsid, "test");
        sfdb.insertUser(rootsid, orgID.toTeamServerUserID(),
                Permissions.allOf(Permission.WRITE), SharedFolderState.JOINED,
                null);
    }
}
