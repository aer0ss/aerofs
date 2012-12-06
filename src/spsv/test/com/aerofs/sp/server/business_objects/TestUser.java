/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.FullName;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;

public class TestUser extends AbstractBusinessObjectTest
{
    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetOrganization()
            throws ExNotFound, SQLException
    {
        newUser().getOrganization();
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetFullName()
            throws ExNotFound, SQLException
    {
        newUser().getFullName();
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetShaedSP()
            throws ExNotFound, SQLException
    {
        newUser().getShaedSP();
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFoundOnGetLevel()
            throws ExNotFound, SQLException
    {
        newUser().getLevel();
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertIfUserNotFoundOnSetLevel()
            throws ExNotFound, SQLException
    {
        newUser().setLevel(AuthorizationLevel.ADMIN);
    }

    @Test(expected = AssertionError.class)
    public void shouldAssertIfUserNotFoundOnSetName()
            throws ExNotFound, SQLException
    {
        newUser().setName(new FullName("first", "last"));
    }

    @Test(expected = MySQLIntegrityConstraintViolationException.class)
    public void shouldThrowIfCreatingUserWithoutOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        createNewUser(newUser(), newOrg());
    }

    @Test(expected = ExAlreadyExist.class)
    public void shouldThrowIfCreatingDuplicateUsers()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User user = newUser();
        Organization org = createNewOrg();
        createNewUser(user, org);
        createNewUser(user, org);
    }

    // see User.addRootStoreAndCheckForCollision for detail
    @Test
    public void shouldCorrectRootStoreCollision()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        // create the playground
        Organization org = createNewOrg();
        Organization org2 = createNewOrg();

        // create the players
        User attacker = newUser("attacker");
        User attacker2 = newUser("attacker2");
        createNewUser(attacker, org);
        createNewUser(attacker2, org2);

        User user = newUser();

        // insert the colliding root store
        SharedFolder sf = factSharedFolder.create_(SID.rootSID(user.id()));
        sf.createNewSharedFolder("haha", attacker);
        sf.addACL(attacker2, Role.EDITOR);
        assertEquals(sf.getRoleThrows(attacker), Role.OWNER);
        assertEquals(sf.getRoleThrows(attacker2), Role.EDITOR);

        // create the ligitimate user
        createNewUser(user, org);

        // the collision should have been corrected
        assertNull(sf.getRoleNullable(attacker));
        assertNull(sf.getRoleNullable(attacker2));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldThrowBadCredentialIfUserNotFoundOnSignIn()
            throws SQLException, ExBadCredential
    {
        newUser().signIn(new byte[0]);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldThrowIfUserNoPermissionOnAddAndMoveToOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        Organization org = createNewOrg();
        User user = newUser();
        createNewUser(user, org);
        user.addAndMoveToOrganization("test");
    }

    @Test
    public void shouldSetUserAsAdminOnAddAndMoveToNewOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User user = newUser();
        createNewUser(user, factOrg.getDefault());
        user.addAndMoveToOrganization("test");

        assertFalse(user.getOrganization().isDefault());
        assertEquals(user.getLevel(), AuthorizationLevel.ADMIN);
    }

    @Test
    public void shouldUpdateTeamServerACLsOnSetOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User user = newUser();
        Organization orgOld = createNewOrg();
        createNewUser(user, orgOld);
        User tsUserOld = newUser(orgOld.id().toTeamServerUserID());

        SharedFolder sfRoot = factSharedFolder.create_(SID.rootSID(user.id()));
        SharedFolder sf1 = factSharedFolder.create_(new SID(UniqueID.generate()));
        SharedFolder sf2 = factSharedFolder.create_(new SID(UniqueID.generate()));
        sf1.createNewSharedFolder("haha", user);
        sf2.createNewSharedFolder("haha", user);

        assertEquals(sfRoot.getRoleNullable(tsUserOld), Role.EDITOR);
        assertEquals(sf1.getRoleNullable(tsUserOld), Role.EDITOR);
        assertEquals(sf2.getRoleNullable(tsUserOld), Role.EDITOR);

        Organization orgNew = createNewOrg();
        User tsUserNew = newUser(orgNew.id().toTeamServerUserID());

        user.setOrganization(orgNew);

        assertNull(sfRoot.getRoleNullable(tsUserOld));
        assertNull(sf1.getRoleNullable(tsUserOld));
        assertNull(sf2.getRoleNullable(tsUserOld));
        assertEquals(sfRoot.getRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf1.getRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf2.getRoleNullable(tsUserNew), Role.EDITOR);
    }
}
