/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.BaseParam.Topics;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.proto.Sp.SignUpWithCodeReply;
import com.aerofs.sp.common.SubscriptionCategory;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;

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
        String orgId = signUp().get().getOrgId();
        assertEquals(ImmutableSet.of(Topics.getACLTopic(":" + orgId, true)), getTopicsPublishedTo());
        clearPublishedMessages();
        signUp();
        assertVerkehrPublishedOnlyTo();
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

    @Test
    public void shouldReturnExistingTeam()
        throws Exception
    {
        ListenableFuture<SignUpWithCodeReply> firstResp = signUp();
        ListenableFuture<SignUpWithCodeReply> secondResp = signUp();

        String firstOrgIDRecv = firstResp.get().getOrgId();
        String secondOrgIDRecv = secondResp.get().getOrgId();

        sqlTrans.begin();
        String orgIDExpected = factUser.create(userID).getOrganization().id().toHexString();
        sqlTrans.commit();

        assertEquals(firstOrgIDRecv,orgIDExpected);
        assertEquals(secondOrgIDRecv,firstOrgIDRecv);
        assertFalse(firstResp.get().getExistingTeam());
        assertTrue(secondResp.get().getExistingTeam());
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

    ListenableFuture<SignUpWithCodeReply> signUp()
            throws Exception
    {
        return service.signUpWithCode(code, ByteString.copyFrom(creds), "A", "B");
    }
}
