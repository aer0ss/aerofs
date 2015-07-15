/*
 * Copyright (c) Air Computing Inc., 2012.
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
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationDatabase;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolderDatabase;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.text.Collator;
import java.util.*;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.*;

public class TestOrganizationDatabase extends AbstractAutoTransactionedTestWithSPDatabase
{
    UserDatabase udb = new UserDatabase(sqlTrans);
    OrganizationDatabase odb = new OrganizationDatabase(sqlTrans);
    SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);
    private SharedFolder.Factory _factSharedFolder;

    OrganizationID orgID = new OrganizationID(123);
    private SID sid = SID.generate();

    @Before
    public void setUp() throws Exception
    {
        odb.insert(orgID);
        addSharedFolder(SID.rootSID(UserID.fromInternal("hahaha")));
        addSharedFolder(sid, "test_shared");
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
        assertEquals(2, odb.listUsers(orgID, 0, 100, null).size());
        assertThat(odb.listUsers(orgID, 0, 100, null), hasItem(UserID.fromInternal("foo")));
        assertThat(odb.listUsers(orgID, 0, 100, null), hasItem(UserID.fromInternal("bar")));
    }

    @Test
    public void listSharedFolders_shouldSkipRootStores() throws Exception
    {
        assertEquals(1, odb.listSharedFolders(orgID, 100, 0).size());
        assertTrue(odb.listSharedFolders(orgID, 100, 0).contains(sid));
    }

    @Test
    public void listSharedFolders_shouldNotBreakWhenFilteringManyRootStores() throws Exception
    {
        // NB: this is a probabilistic test.
        // The relative numbers of root store vs regular stores have been chosen to minimize the
        // likelihood of false positives while keeping the test reasonably quick.
        for (int i = 0; i < 30; ++i) {
            addSharedFolder(SID.rootSID(UserID.fromInternal("u" + i)));
        }

        for (int i = 0; i < 4; ++i) {
            addSharedFolder(SID.generate());
        }

        assertEquals(5, odb.listSharedFolders(orgID, 10, 0).size());
        assertTrue(odb.listSharedFolders(orgID, 10, 0).contains(sid));
    }

    @Test
    public void listSharedFolders_shouldListSharedFoldersInAlphabeticalOrder() throws Exception
    {
        List<String> orderedNames = Lists.newArrayList();
        orderedNames.add("an_example");
        orderedNames.add("2sf_example10");
        orderedNames.add("s1f_example10");
        orderedNames.add("sf_example10");
        orderedNames.add("sf_example2");
        orderedNames.add("Sf_EXAMPLE1");
        orderedNames.add("sf_example1");
        orderedNames.add("test");
        orderedNames.add("TEST");

        // Set of ordered names will be in random order every time in the test will be run.
        for (String str: Sets.newHashSet(orderedNames)) {
            addSharedFolder(SID.generate(), str);
        }

        // Since test_shared was created in the setup function, we add it to the expected result
        // after creating other shared folders in order to avoid creating test_shared twice.
        orderedNames.add("test_shared");

        // Natural sorting.
        Collections.sort(orderedNames, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int lowerCaseComp = o1.compareToIgnoreCase(o2);
                return lowerCaseComp == 0 ? o1.compareTo(o2) : lowerCaseComp;
            }
        });

        // Get 2 results first and then the rest to assert that order is maintained across multiple
        // pages.
        Collection<SID> sharedFolders = odb.listSharedFolders(orgID, 2, 0);
        sharedFolders.addAll(odb.listSharedFolders(orgID, 8, 2));
        assertEquals(10, sharedFolders.size());
        List<String> result = Lists.newArrayList();
        for (SID sid : sharedFolders) {
            result.add(sfdb.getName(sid, orgID.toTeamServerUserID()));
        }

        Assert.assertArrayEquals(orderedNames.toArray(), result.toArray());
    }

    @Test
    public void countSharedFolders_shouldSkipRootStores() throws Exception
    {
        assertEquals(1, odb.countSharedFolders(orgID));
    }

    private void addSharedFolder(SID rootsid) throws Exception
    {
        addSharedFolder(rootsid, "test");
    }

    private void addSharedFolder(SID rootsid, String name) throws Exception
    {
        sfdb.insert(rootsid, name);
        sfdb.insertUser(rootsid, orgID.toTeamServerUserID(),
                Permissions.allOf(Permission.WRITE), SharedFolderState.JOINED,
                null, GroupID.NULL_GROUP);
    }
}
