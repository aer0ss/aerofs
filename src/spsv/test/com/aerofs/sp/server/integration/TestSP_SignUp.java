/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Sp.SignUpReply;
import com.aerofs.sp.common.SubscriptionCategory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Mockito.verify;

public class TestSP_SignUp extends AbstractSPTest
{
    UserID userID = UserID.fromInternal("user@email");
    byte[] creds = new byte[] { 1, 2 };

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
            service.signUp(userID.getString(), ByteString.copyFrom(new byte[0]), "A", "B");
            fail();
        } catch (ExBadCredential e) { }
    }

    @Test
    public void shouldSetEmailUnverified()
            throws Exception
    {
        signUp();

        sqlTrans.begin();
        assertFalse(factUser.create(userID).isEmailVerified());
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveReminderEmailSubscription()
            throws Exception
    {
        signUp();
        verify(esdb).removeEmailSubscription(userID, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
    }

    @Test
    public void shouldTrimUserName()
            throws Exception
    {
        signUp();
        sqlTrans.begin();
        FullName fn = factUser.create(userID).getFullName();
        assertTrue(fn._first.equals("A"));
        assertTrue(fn._last.equals("B"));
        sqlTrans.commit();
    }

    ListenableFuture<SignUpReply> signUp()
            throws Exception
    {
        return service.signUp(userID.getString(), ByteString.copyFrom(creds), " A ", " B  ");
    }
}
