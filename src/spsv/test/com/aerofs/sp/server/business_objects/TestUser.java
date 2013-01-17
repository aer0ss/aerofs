/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.business_objects;

import com.aerofs.lib.FullName;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.base.id.SID;
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
        saveUser(newUser(), newOrganization());
    }

    @Test(expected = ExAlreadyExist.class)
    public void shouldThrowIfCreatingDuplicateUsers()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User user = newUser();
        Organization org = saveOrganization();
        saveUser(user, org);
        saveUser(user, org);
    }

    // see User.addRootStoreAndCheckForCollision for detail
    @Test
    public void shouldCorrectRootStoreCollision()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        // create the playground
        Organization org = saveOrganization();
        Organization org2 = saveOrganization();

        // create the players
        User attacker = newUser("attacker");
        User attacker2 = newUser("attacker2");
        saveUser(attacker, org);
        saveUser(attacker2, org2);

        User user = newUser();

        // insert the colliding root store
        SharedFolder sf = factSharedFolder.create(SID.rootSID(user.id()));
        sf.save("haha", attacker);
        sf.addMemberACL(attacker2, Role.EDITOR);
        assertEquals(sf.getMemberRoleThrows(attacker), Role.OWNER);
        assertEquals(sf.getMemberRoleThrows(attacker2), Role.EDITOR);

        // create the ligitimate user
        saveUser(user, org);

        // the collision should have been corrected
        assertNull(sf.getMemberRoleNullable(attacker));
        assertNull(sf.getMemberRoleNullable(attacker2));
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
        Organization org = saveOrganization();
        User user = newUser();
        saveUser(user, org);
        user.addAndMoveToOrganization("test", null, null, null);
    }

    @Test
    public void shouldSetUserAsAdminOnAddAndMoveToNewOrganization()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User user = newUser();
        saveUser(user, factOrg.getDefault());
        user.addAndMoveToOrganization("test", null, null, null);

        assertFalse(user.getOrganization().isDefault());
        assertEquals(user.getLevel(), AuthorizationLevel.ADMIN);
    }

    @Test
    public void shouldUpdateTeamServerACLsOnSetOrg()
            throws ExNoPerm, IOException, ExNotFound, SQLException, ExAlreadyExist
    {
        User user = newUser();
        Organization orgOld = saveOrganization();
        saveUser(user, orgOld);
        User tsUserOld = newUser(orgOld.id().toTeamServerUserID());

        SharedFolder sfRoot = factSharedFolder.create(SID.rootSID(user.id()));
        SharedFolder sf1 = factSharedFolder.create(SID.generate());
        SharedFolder sf2 = factSharedFolder.create(SID.generate());
        sf1.save("haha", user);
        sf2.save("haha", user);

        assertEquals(sfRoot.getMemberRoleNullable(tsUserOld), Role.EDITOR);
        assertEquals(sf1.getMemberRoleNullable(tsUserOld), Role.EDITOR);
        assertEquals(sf2.getMemberRoleNullable(tsUserOld), Role.EDITOR);

        Organization orgNew = saveOrganization();
        User tsUserNew = newUser(orgNew.id().toTeamServerUserID());

        user.setOrganization(orgNew);

        assertNull(sfRoot.getMemberRoleNullable(tsUserOld));
        assertNull(sf1.getMemberRoleNullable(tsUserOld));
        assertNull(sf2.getMemberRoleNullable(tsUserOld));
        assertEquals(sfRoot.getMemberRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf1.getMemberRoleNullable(tsUserNew), Role.EDITOR);
        assertEquals(sf2.getMemberRoleNullable(tsUserNew), Role.EDITOR);
    }
}
