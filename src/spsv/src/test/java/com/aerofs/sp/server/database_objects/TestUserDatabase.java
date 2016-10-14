/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.sp.server.database_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.id.GroupID;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.sf.SharedFolderDatabase;
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationDatabase;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;
import org.junit.Before;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestUserDatabase extends AbstractAutoTransactionedTestWithSPDatabase
{
    UserDatabase udb = new UserDatabase(sqlTrans);
    OrganizationDatabase odb = new OrganizationDatabase(sqlTrans);
    SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);
    OrganizationID orgID = new OrganizationID(123);
    UserID user1 = UserID.fromInternal("1234567");
    UserID user2= UserID.fromInternal("abcdefg");


    @Before
    public void setUp() throws Exception
    {
        odb.insert(orgID);

        udb.insertUser(user1, new FullName("foo", "bar"), new byte[0], orgID,
                AuthorizationLevel.ADMIN);
        udb.insertUser(user2, new FullName("bar", "baz"), new byte[0], orgID,
                AuthorizationLevel.USER);

    }

    @Test
    public void countJoinedSharedFolders_shouldCoutUserSharedFolders()  throws Exception
    {
        assertEquals(0, udb.countJoinedSharedFolders(user1));
        addSharedFolder(user1, user2, "lalala");
        assertEquals(1, udb.countJoinedSharedFolders(user1));
    }

    @Test
    public void countJoinedSharedFoldersWithSearchString_shouldCountUserSharedFoldersWithSearchString()
            throws Exception
    {
        assertEquals(0, udb.countJoinedSharedFolders(user1));
        assertEquals(0, udb.countJoinedSharedFolders(user2));

        createSharedFolders(user1, user2);

        assertEquals(2, udb.countJoinedSharedFoldersWithSearchString(user1, "tes"));
        assertEquals(2, udb.countJoinedSharedFoldersWithSearchString(user2, "a"));
    }

    @Test
    public void countJoinedSharedFoldersWithSearchString_shouldCountUserSharedFoldersWithNullSearchString()
            throws Exception
    {
        assertEquals(0, udb.countJoinedSharedFolders(user1));
        assertEquals(0, udb.countJoinedSharedFolders(user2));

        createSharedFolders(user1, user2);

        assertEquals(2, udb.countJoinedSharedFoldersWithSearchString(user1, "foo"));
        assertEquals(5, udb.countJoinedSharedFoldersWithSearchString(user1, null));
        assertEquals(5, udb.countJoinedSharedFoldersWithSearchString(user2, null));
    }

    @Test
    public void getJoinedSharedFolders_shouldListInAlphabeticalOrder() throws Exception
    {
        createSharedFolders(user1, user2);

        List<String> orderedNames = Lists.newArrayList("FooBar", "fooBar", "sf", "Test Folder", "test Folder");
        Collection<SID> result = udb.getJoinedSharedFolders(user1, null, null, null);
        List<String> resultNames = getResultNames(result, user1);

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());
    }

    @Test
    public void getJoinedSharedFolders_shouldListAllNamesAlphabetically() throws Exception
    {
        //Create some shared folders and change their names
        SID folder1 = addSharedFolder(user1, user2, "alpha");
        SID folder2 = addSharedFolder(user1, user2, "beta");
        SID folder3 = addSharedFolder(user1, user2, "gamma");

        // Rename for user one
        sfdb.setName(folder1, user1, "omega");

        List<SID> ordered1Ids = Lists.newArrayList(folder2, folder3, folder1);
        List<SID> ordered2Ids = Lists.newArrayList(folder1, folder2, folder3);
        Collection<SID> result1 = udb.getJoinedSharedFolders(user1, null, null, null);
        Collection<SID> result2 = udb.getJoinedSharedFolders(user2, null, null, null);

        Assert.assertArrayEquals(ordered1Ids.toArray(), result1.toArray());
        Assert.assertArrayEquals(ordered2Ids.toArray(), result2.toArray());

    }

    @Test
    public void getJoinedSharedFolders_shouldListAlphabeticallyWithLimitOffset() throws Exception
    {
        createSharedFolders(user1, user2);

        //Page 1
        List<String> orderedNames = Lists.newArrayList("FooBar", "fooBar", "sf");
        Collection<SID> result = udb.getJoinedSharedFolders(user1, 3, 0, null);
        List<String> resultNames = getResultNames(result, user1);

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());

        //Page 2
        orderedNames = Lists.newArrayList("Test Folder","test Folder");
        result = udb.getJoinedSharedFolders(user1, 3, 3, null);
        resultNames = getResultNames(result, user1);

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());
    }

    @Test
    public void getJoinedSharedFolders_shouldListAlphabeticallyWithLimitOffsetSearchString()
            throws Exception
    {
        createSharedFolders(user1, user2);

        //Page 1
        List<String> orderedNames = Lists.newArrayList("FooBar", "fooBar");
        Collection<SID> result = udb.getJoinedSharedFolders(user1, 2, 0, "foo");
        List<String> resultNames = getResultNames(result, user1);

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());

        //Page 2
        orderedNames = Lists.newArrayList("fooBar");
        result = udb.getJoinedSharedFolders(user1, 1, 1, "foo");
        resultNames = getResultNames(result, user1);

        Assert.assertArrayEquals(orderedNames.toArray(), resultNames.toArray());

        //No Matches
        result = udb.getJoinedSharedFolders(user1, 100, 0, "zzzz");
        resultNames = getResultNames(result, user1);
        Assert.assertArrayEquals(new String[0], resultNames.toArray());
    }

    @Test
    public void getJoinedSharedFolders_shouldListAllNamesAlphabeticallyWithSearchString()
            throws Exception
    {
        //Create some shared folders and change their names
        SID folder1 = addSharedFolder(user1, user2, "a_alpha");
        SID folder2 = addSharedFolder(user1, user2, "a_beta");
        SID folder3 = addSharedFolder(user1, user2, "a_gamma");

        sfdb.setName(folder1, user1, "a_omega");

        List<SID> orderedIds = Lists.newArrayList(folder2, folder3, folder1);
        List<SID> orderedIds2 = Lists.newArrayList(folder1, folder2, folder3);
        Collection<SID> result1 = udb.getJoinedSharedFolders(user1, 100, 0, "a_");
        Collection<SID> result2 = udb.getJoinedSharedFolders(user2, 100, 0, "a_");

        Assert.assertArrayEquals(orderedIds.toArray(), result1.toArray());
        Assert.assertArrayEquals(orderedIds2.toArray(), result2.toArray());
    }

    @Test
    public void getJoinedSharedFolders_shouldListOnlyJoinedFolders() throws Exception
    {
        //Create some shared folders and change their names
        SID folder1 = addSharedFolder(user1, user2, "alpha");
        SID folder2 = addSharedFolder(user1, user2, "beta");
        SID folder3 = addSharedFolder(user1, user2, "gamma");

        sfdb.setState(folder1, user1, SharedFolderState.PENDING);
        sfdb.setState(folder3, user2, SharedFolderState.LEFT);

        List<SID> ordered1Ids = Lists.newArrayList(folder2, folder3);
        List<SID> ordered2Ids = Lists.newArrayList(folder1, folder2);
        Collection<SID> result1 = udb.getJoinedSharedFolders(user1, null, null, null);
        Collection<SID> result2 = udb.getJoinedSharedFolders(user2, null, null, null);

        Assert.assertArrayEquals(ordered1Ids.toArray(), result1.toArray());
        Assert.assertArrayEquals(ordered2Ids.toArray(), result2.toArray());

    }

    @Test
    public void getLeftSharedFolders_shouldListOnlyLeftFolders() throws Exception
    {
        //Create some shared folders and change their names
        SID folder1 = addSharedFolder(user1, user2, "alpha");
        SID folder2 = addSharedFolder(user1, user2, "beta");
        SID folder3 = addSharedFolder(user1, user2, "gamma");

        sfdb.setState(folder1, user1, SharedFolderState.PENDING);
        sfdb.setState(folder3, user2, SharedFolderState.LEFT);

        List<SID> ordered1Ids = Lists.newArrayList();
        List<SID> ordered2Ids = Lists.newArrayList(folder3);
        Collection<SID> result1 = udb.getLeftSharedFolders(user1);
        Collection<SID> result2 = udb.getLeftSharedFolders(user2);

        Assert.assertArrayEquals(ordered1Ids.toArray(), result1.toArray());
        Assert.assertArrayEquals(ordered2Ids.toArray(), result2.toArray());

    }

    @Test
    public void getLeftSharedFolders_shouldListOnlyLeftFoldersAlphabeticaly() throws Exception
    {
        //Create some shared folders and change their names
        SID folder1 = addSharedFolder(user1, user2, "alpha");
        SID folder2 = addSharedFolder(user1, user2, "gamma");
        SID folder3 = addSharedFolder(user1, user2, "beta");

        sfdb.setState(folder1, user1, SharedFolderState.LEFT);
        sfdb.setState(folder2, user1, SharedFolderState.LEFT);
        sfdb.setState(folder2, user2, SharedFolderState.LEFT);
        sfdb.setState(folder3, user2, SharedFolderState.LEFT);

        List<SID> ordered1Ids = Lists.newArrayList(folder1, folder2);
        List<SID> ordered2Ids = Lists.newArrayList(folder3, folder2);
        Collection<SID> result1 = udb.getLeftSharedFolders(user1);
        Collection<SID> result2 = udb.getLeftSharedFolders(user2);

        Assert.assertArrayEquals(ordered1Ids.toArray(), result1.toArray());
        Assert.assertArrayEquals(ordered2Ids.toArray(), result2.toArray());

    }

    private SID addSharedFolder(UserID sharer, UserID sharee, String name) throws Exception
    {
        SID sid = SID.generate();
        sfdb.insert(sid, name);
        sfdb.insertUser(sid, sharer, Permissions.OWNER, SharedFolderState.JOINED,
                null, GroupID.NULL_GROUP);
        sfdb.insertUser(sid, sharee, Permissions.allOf(Permission.WRITE),  SharedFolderState.PENDING,
                sharer, GroupID.NULL_GROUP );
        sfdb.setState(sid, sharee, SharedFolderState.JOINED);
        return sid;
    }

    private void createSharedFolders(UserID user1, UserID user2) throws Exception
    {
        addSharedFolder(user1, user2, "Test Folder");
        addSharedFolder(user1, user2, "test Folder");
        addSharedFolder(user1, user2, "FooBar");
        addSharedFolder(user1, user2, "fooBar");
        addSharedFolder(user1, user2, "sf");
    }

    private List<String> getResultNames(Collection<SID> sharedFolders, UserID userId) throws Exception
    {
        List<String> result = Lists.newArrayList();
        for (SID sid : sharedFolders) {
            result.add(sfdb.getName(sid, userId));
        }
        return result;
    }
}
