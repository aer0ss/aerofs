/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExBadCredential;
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
import static org.junit.Assert.fail;

public class TestSP_OpenIdAuthority extends AbstractSPTest
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
    public void authenticateUser_shouldRefuseSignin() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();
        try {
            new OpenIdAuthority().authenticateUser(user, CRED, sqlTrans, CredentialFormat.TEXT);
            fail("expected bad credential");
        } catch (ExBadCredential expected) {}
    }

    @Test
    public void shouldNotBeLocal()
    {
        OpenIdAuthority openid = new OpenIdAuthority();
        assertFalse(openid.managesLocalCredential());
    }

    @Test
    public void canAuthenticate_shouldUseAddressPattern() throws Exception
    {
        configureInternalPattern();
        OpenIdAuthority openid = new OpenIdAuthority();
        User u = newUser();

        assertTrue(openid.isInternalUser(u.id()));
        assertTrue(openid.canAuthenticate(u.id()));

        assertFalse( openid.isInternalUser(UserID.fromExternal("external@far.away")) );
        assertFalse(openid.canAuthenticate(UserID.fromExternal("external@far.away")));
    }

    @Test
    public void canAuthenticate_shouldBeInternalForEmptyPattern() throws Exception
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = true;
        User user = newUser();

        // set the pattern to match ".*@email$"
        Properties properties = new Properties();
        properties.put("internal_email_pattern", "");
        ConfigurationProperties.setProperties(properties);

        IAuthority openid = new OpenIdAuthority();

        assertTrue(openid.canAuthenticate(user.id()));
        assertTrue(openid.isInternalUser(user.id()));

        assertTrue(openid.canAuthenticate(UserID.fromExternal("hi@example.com")));
        assertTrue(openid.isInternalUser(UserID.fromExternal("hi@example.com")));
    }

    @Test
    public void canAuthenticate_shouldBeInternalForPublicDeploy() throws Exception
    {
        PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT = false;
        User user = newUser();

        IAuthority local = new LocalAuthority();

        assertTrue(local.canAuthenticate(user.id()));
        assertTrue(local.isInternalUser(user.id()));

        assertTrue(local.canAuthenticate(UserID.fromExternal("hi@example.com")));
        assertTrue(local.isInternalUser(UserID.fromExternal("hi@example.com")));
    }


    private void configureInternalPattern()
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
    }

    Boolean _isPrivateCached;
}
