/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Test;

/**
 */
public class TestSP_ValidateCredential extends AbstractSPTest
{
    @Test
    public void shouldValidateCredential() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.validateCredential(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotValidateNonExistingUserID() throws Exception
    {
        User user = newUser();

        service.validateCredential(user.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotValidateBadCredential() throws Exception
    {
        sqlTrans.begin();
        User user = saveUser();
        sqlTrans.commit();

        service.validateCredential(user.id().getString(), ByteString.copyFrom("oh no!".getBytes()));
    }
}
