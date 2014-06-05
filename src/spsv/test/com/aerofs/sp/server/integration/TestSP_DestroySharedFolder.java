/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSP_DestroySharedFolder extends AbstractSPFolderTest
{
    User admin;
    User owner;
    User editor;
    SID sid;

    @Before
    public void setUp() throws Exception
    {
        sqlTrans.begin();
        admin = saveUser();
        owner = saveUser();
        editor = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);
        owner.setOrganization(admin.getOrganization(), AuthorizationLevel.USER);
        editor.setOrganization(admin.getOrganization(), AuthorizationLevel.USER);
        sqlTrans.commit();

        sid = SID.generate();
        shareAndJoinFolder(owner, sid, editor, Permissions.allOf(Permission.WRITE));
    }

    @Test
    public void shouldAllowManagerToDestroySharedFolder() throws Exception
    {
        setSessionUser(owner);
        service.destroySharedFolder(sid);

        // verify that the store is not found
        try {
            service.setSharedFolderName(sid, "new_name");
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void shouldAllowAdminToDestroySharedFolder() throws Exception
    {
        setSessionUser(admin);
        service.destroySharedFolder(sid);

        // verify that the store is not found
        try {
            service.setSharedFolderName(sid, "new_name");
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void shouldNotAllowEditorToDestroySharedFolder() throws Exception
    {
        setSessionUser(editor);
        try {
            service.destroySharedFolder(sid);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }

    @Test
    public void shouldNotAllowDestructionOfRootStore() throws Exception
    {
        setSessionUser(owner);
        try {
            service.destroySharedFolder(SID.rootSID(owner.id()));
            fail();
        } catch (ExBadArgs ignored) {
            // success
        }
    }

    @Test
    public void shouldThrowExNotFoundWhenTryingToDestroyNonExistingSharedFolder() throws Exception
    {
        setSessionUser(owner);
        try {
            service.destroySharedFolder(SID.generate());
            fail();
        } catch (ExNotFound ignored) {
            // success
        }
    }

    @Test
    public void shouldThrowExNoPermForAdminOfOrgWithNoManagers() throws Exception
    {
        sqlTrans.begin();
        User otherAdmin = saveUser();
        assertTrue(otherAdmin.isAdmin());
        sqlTrans.commit();
        setSessionUser(otherAdmin);
        try {
            service.destroySharedFolder(sid);
            fail();
        } catch (ExNoPerm ignored) {
            // success
        }
    }
}
