/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.FullName;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.common.SharedFolderState;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestUser_deactivate extends AbstractBusinessObjectTest
{
    @Test
    public void shouldMakeAllMethodsBehaveAsIfUserNeverExisted() throws Exception
    {
        User user = saveUser();
        saveSharedFolder(user);
        addCert(saveDevice(user));

        user.deactivate(ImmutableSet.<Long>builder(), null);

        assertFalse(user.exists());

        try {
            user.getFullName();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.getOrganization();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.getLevel();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.getSignupDate();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.isAdmin();
            fail();
        } catch (ExNotFound e) {}

        try {
            user.belongsTo(factOrg.create(42));
            fail();
        } catch (ExNotFound e) {}

        assertThat(user.getDevices(), empty());
        assertThat(user.getPeerDevices(), empty());
        assertThat(user.getJoinedFolders(), empty());
        assertThat(user.getPendingSharedFolders(), empty());

        try {
            user.getACLEpoch();
            fail();
        } catch (AssertionError e) {}
    }


    @Test
    public void shouldDeleteSharedFolderWithNoJoinedUsers() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        assertTrue(sf.exists());
        sf.addPendingUser(saveUser(), Permissions.allOf(Permission.WRITE), user);
        User left = saveUser();
        sf.addJoinedUser(left, Permissions.allOf());
        sf.setState(left, SharedFolderState.LEFT);

        User admin = saveUser();

        user.deactivate(ImmutableSet.<Long>builder(), admin);

        assertFalse(sf.exists());
    }

    @Test
    public void shouldTransferOwnershipOnDeactivationOfLastOwner() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE));

        User admin = saveUser();

        user.deactivate(ImmutableSet.<Long>builder(), admin);

        assertTrue(sf.exists());
        assertTrue(sf.hasOwnerLeft());
        assertThat(sf.getJoinedUsers(), hasItem(admin));
    }

    @Test
    public void shouldNotTransferOwnershipOnWhenOtherOwnersLeft() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE, Permission.MANAGE));

        User admin = saveUser();

        user.deactivate(ImmutableSet.<Long>builder(), admin);

        assertTrue(sf.exists());
        assertTrue(sf.hasOwnerLeft());
        assertThat(sf.getJoinedUsers(), not(hasItem(admin)));
    }

    @Test
    public void shouldFailWhenCannotTransferOwnership() throws Exception
    {
        User user = saveUser();

        SharedFolder sf = saveSharedFolder(user);
        sf.addJoinedUser(saveUser(), Permissions.allOf(Permission.WRITE));

        try {
            user.deactivate(ImmutableSet.<Long>builder(), null);
            fail();
        } catch (ExNoAdminOrOwner e) {}
    }

    @Test
    public void shouldDeleteAllSignupCodes()
            throws Exception
    {
        User user = newUser();
        String codes[] = {
            user.addSignUpCode(),
            user.addSignUpCode(),
            user.addSignUpCode(),
        };

        user.save(new byte[0], new FullName("", ""));
        for (String code : codes) {
            assertEquals(db.getSignUpCode(code), user.id());
        }

        user.deactivate(ImmutableSet.<Long>builder(), null);
        for (String code : codes) {
            try {
                db.getSignUpCode(code);
                fail();
            } catch (ExNotFound e) {}
        }
    }
}
