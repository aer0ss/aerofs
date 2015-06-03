/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.authentication.LocalCredential;
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
    public void testSignInClearText() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void testSignInClearTextBadUserFails() throws Exception
    {
        User user = newUser();

        service.credentialSignIn(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void testSignInClearTextBadCredFails() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.credentialSignIn(user.id().getString(), ByteString.copyFrom("oh no".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void testSignInClearTextDoesNotNeedSCrypt() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.credentialSignIn(user.id().getString(),
                ByteString.copyFrom(LocalCredential.deriveKeyForUser(user.id(), CRED)));
    }

    // ---- ---- ---- ----
    // From here down, there are only tests for legacy paths
    // Abandon all hope ye who enter here
    // ---- ---- ---- ----

    @Test
    public void testLegacySignInUserRequiresScrypt() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signInUser(user.id().getString(),
                ByteString.copyFrom(LocalCredential.deriveKeyForUser(user.id(), CRED)));
    }

    @Test(expected = ExBadCredential.class)
    public void testLegacySignInUserFailsNonExistingUser()
            throws Exception
    {
        User user = newUser();

        service.signInUser(user.id().getString(),
                ByteString.copyFrom(LocalCredential.deriveKeyForUser(user.id(), CRED)));
    }

    @Test(expected = ExBadCredential.class)
    public void testLegacySignInUserFailsWithClearText() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.signInUser(user.id().getString(), ByteString.copyFrom(CRED));
    }
}
