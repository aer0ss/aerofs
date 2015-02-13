/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.sp.authentication.Authenticator.CredentialFormat;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.user.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSP_LocalAuthority extends AbstractSPTest
{
    @Before
    public void cacheJunk()
    {
        _isPrivateCached = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT;
    }

    @After
    public void resetWorld()
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = _isPrivateCached;
    }

    @Test
    public void authenticateUser_shouldSignInLocal() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();
        new LocalAuthority().authenticateUser(user, CRED, sqlTrans, CredentialFormat.TEXT);
    }

    @Test
    public void canAuthenticate_shouldAcceptAll() throws ExInvalidID
    {
        LocalAuthority local = new LocalAuthority();
        assertTrue(local.managesLocalCredential());
        assertTrue(local.canAuthenticate(UserID.fromExternal("a@b.c")));
    }

    @Test
    public void canAuthenticate_shouldUserAddressFilter() throws Exception
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        User user = newUser();

        // set the pattern to match ".*@email$"
        String patt = ".*"
                + user.id().getString().substring(
                    user.id().getString().indexOf("@"))
                + "$";

        Properties properties = new Properties();
        properties.put("internal_email_pattern", patt);
        ConfigurationProperties.setProperties(properties);

        IAuthority local = new LocalAuthority();

        assertTrue(local.canAuthenticate(user.id()));
        assertTrue(local.isInternalUser(user.id()));

        assertTrue(local.canAuthenticate(UserID.fromExternal("hi@example.com")));
        assertFalse(local.isInternalUser(UserID.fromExternal("hi@example.com")));
    }

    @Test
    public void canAuthenticate_shouldReturnInternalForEmptyPattern() throws Exception
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        User user = newUser();

        // set the pattern to match ".*@email$"
        Properties properties = new Properties();
        properties.put("internal_email_pattern", "");
        ConfigurationProperties.setProperties(properties);

        IAuthority local = new LocalAuthority();

        assertTrue(local.canAuthenticate(user.id()));
        assertTrue(local.isInternalUser(user.id()));

        assertTrue(local.canAuthenticate(UserID.fromExternal("hi@example.com")));
        assertTrue(local.isInternalUser(UserID.fromExternal("hi@example.com")));
    }

    @Test
    public void canAuthenticate_shouldReturnInternalForPublicDeploy() throws Exception
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = false;
        User user = newUser();

        IAuthority local = new LocalAuthority();

        assertTrue(local.canAuthenticate(user.id()));
        assertTrue(local.isInternalUser(user.id()));

        assertTrue(local.canAuthenticate(UserID.fromExternal("hi@example.com")));
        assertTrue(local.isInternalUser(UserID.fromExternal("hi@example.com")));
    }

    Boolean _isPrivateCached;
}
