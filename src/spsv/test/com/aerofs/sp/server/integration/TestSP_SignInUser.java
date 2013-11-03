/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.SecUtil;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Test;

/**
 * Important: signIn() and signInUser() might expect scrypted password, or plaintext, depending on
 * the configured Authenticator instance.
 * <p/>
 * This is, to say the least, annoying.
 */
public class TestSP_SignInUser extends AbstractSPTest
{
    @Test
    public void shouldAllowCredentialSignIn()
            throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signIn(user.id().getString(),
                ByteString.copyFrom(SecUtil.scrypt(new String(CRED).toCharArray(), user.id())));
    }

    @Test
    public void shouldAllowCredentialSignInUser()
            throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signInUser(user.id().getString(),
                ByteString.copyFrom(SecUtil.scrypt(new String(CRED).toCharArray(), user.id())));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingUserIDToSignIn()
            throws Exception
    {
        User user = newUser();

        service.signInUser(user.id().getString(),
                ByteString.copyFrom(SecUtil.scrypt(new String(CRED).toCharArray(), user.id())));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowCleartextCred()
            throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signInUser(user.id().getString(), ByteString.copyFrom(CRED));
    }
}
