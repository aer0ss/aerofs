/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TestSP_RemoveUserFromOrganization extends AbstractSPTest
{
    @Test
    public void shouldRemoveUserFromOrganization() throws Exception
    {
        sqlTrans.begin();

        // Create a new organization, with an admin and a regular user
        User orgAdmin = saveUser();
        User user = saveUser();
        Organization org = orgAdmin.getOrganization();
        user.setOrganization(org, AuthorizationLevel.USER);

        // Check that everything matches our world view
        assertEquals(2, org.countUsers());
        assertEquals(org, user.getOrganization());
        assertEquals(org, orgAdmin.getOrganization());

        sqlTrans.commit();

        // Now remove the user from the organization
        setSession(orgAdmin);
        service.removeUserFromOrganization(user.id().getString());

        // Check that the user has indeed been removed
        sqlTrans.begin();

        assertEquals(1, org.countUsers());
        assertFalse(org.equals(user.getOrganization()));

        sqlTrans.commit();
    }

    @Test
    public void shouldFailForNonExistingUser() throws Exception
    {
        sqlTrans.begin();
        User orgAdmin = saveUser();
        sqlTrans.commit();

        setSession(orgAdmin);
        try {
            service.removeUserFromOrganization("non_existing@user.com");
            fail();
        } catch (ExNoPerm e) { /* expected */ }
    }

    @Test
    public void shouldFailIfRemovingOneself() throws Exception
    {
        sqlTrans.begin();
        User orgAdmin = saveUser();
        sqlTrans.commit();

        setSession(orgAdmin);
        try {
            service.removeUserFromOrganization(orgAdmin.id().getString());
            fail();
        } catch (ExNoPerm e) { /* expected */ }
    }

    @Test
    public void shouldFailForNonAdmin() throws Exception
    {
        sqlTrans.begin();

        // Create an organization with one admin and two users

        User orgAdmin = saveUser();
        Organization org = orgAdmin.getOrganization();
        User user1 = saveUser();
        User user2 = saveUser();
        user1.setOrganization(org, AuthorizationLevel.USER);
        user2.setOrganization(org, AuthorizationLevel.USER);

        sqlTrans.commit();

        // Now let's make user1 try to remove user2. Should throw ExNoPerm
        setSession(user1);
        try {
            service.removeUserFromOrganization(user2.id().getString());
            fail();
        } catch (ExNoPerm e) { /* expected */ }
    }

    @Test(expected = ExNoPerm.class)
    public void shouldFailInEnterpriseDeployment() throws Exception
    {
        boolean savedIsPrivateDeployment = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT;
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        try {
            shouldRemoveUserFromOrganization();
        } finally {
            PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = savedIsPrivateDeployment;
        }
    }
}
