/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.database_objects;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.AbstractAutoTransactionedTestWithSPDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.base.id.OrganizationID;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestOrganizationDatabase extends AbstractAutoTransactionedTestWithSPDatabase
{
    OrganizationDatabase odb = new OrganizationDatabase(sqlTrans);
    SharedFolderDatabase sfdb = new SharedFolderDatabase(sqlTrans);

    OrganizationID orgID = new OrganizationID(123);
    private SID sid = SID.generate();

    @Test
    public void listSharedFolders_shouldSkipRootStores()
            throws SQLException, ExBadArgs, ExAlreadyExist
    {
        addRootStoreAndNonRootStore();

        assertEquals(odb.listSharedFolders(orgID, 100, 0).size(), 1);
        assertTrue(odb.listSharedFolders(orgID, 100, 0).contains(sid));
    }

    @Test
    public void countSharedFolders_shouldSkipRootStores()
            throws SQLException, ExBadArgs, ExAlreadyExist
    {
        addRootStoreAndNonRootStore();

        assertEquals(odb.countSharedFolders(orgID), 1);
    }

    private void addRootStoreAndNonRootStore()
            throws SQLException, ExAlreadyExist
    {
        addSharedFolder(SID.rootSID(UserID.fromInternal("hahaha")));
        addSharedFolder(sid);
    }

    private void addSharedFolder(SID rootsid)
            throws SQLException, ExAlreadyExist
    {
        sfdb.insert(rootsid, "test");
        sfdb.insertUser(rootsid, orgID.toTeamServerUserID(), Role.EDITOR, SharedFolderState.JOINED,
                null);
    }

}
