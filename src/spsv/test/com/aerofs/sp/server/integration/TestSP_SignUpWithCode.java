/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static junit.framework.Assert.fail;

public class TestSP_SignUpWithCode extends AbstractSPTest
{
    UserID userID = UserID.fromInternal("user@email");
    byte[] creds = new byte[] { 1, 2 };
    String code;

    @Before
    public void setup()
            throws SQLException
    {
        // emulate inviting to sign up
        sqlTrans.begin();
        User user = factUser.create(userID);
        code = user.addSignUpCode();
        esdb.insertEmailSubscription(user.id(), SubscriptionCategory.AEROFS_INVITATION_REMINDER);
        sqlTrans.commit();
    }

    @Test
    public void shouldIgnoreExistingUserWithMatchingPassword()
            throws Exception
    {
        signUp();
        signUp();
    }

    @Test
    public void shouldThrowIfUserExistsAndPasswordDoesntMatch()
            throws Exception
    {
        signUp();

        try {
            service.signUpWithCode(code, ByteString.copyFrom(new byte[0]), "A", "B");
            fail();
        } catch (ExBadCredential e) {
        }
    }

    void signUp()
            throws Exception
    {
        service.signUpWithCode(code, ByteString.copyFrom(creds), "A", "B");
    }
}
