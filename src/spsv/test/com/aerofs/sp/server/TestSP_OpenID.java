/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.Topics;
import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.OpenIdSessionAttributes;
import com.aerofs.proto.Sp.OpenIdSessionNonces;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestSP_OpenID extends AbstractSPTest
{
    final int SESSION_NONCE_TIMEOUT_SECS = 5;

    private OpenIdSessionNonces _nonces;

    @Before
    public void setUp()
    {
        _nonces = null;
    }

    @Test
    public void shouldThrowIfNoSessionExists() throws Exception
    {
       try {
           service.openIdGetSessionAttributes("Session that doesn't exist");
           fail("Expected exception.");
       } catch (ExExternalAuthFailure e) {
           // pass
       }
    }

    @Test
    public void shouldReturnEmptySessionAttributesIfSessionNotYetAuthorized() throws Exception
    {
        OpenIdSessionAttributes returnedAttributes;

        _nonces = service.openIdBeginTransaction().get();
        returnedAttributes = service.openIdGetSessionAttributes(_nonces.getSessionNonce()).get();

        assertTrue(returnedAttributes.getUserId().isEmpty());
        assertTrue(returnedAttributes.getFirstName().isEmpty());
        assertTrue(returnedAttributes.getLastName().isEmpty());
    }

    @Test
    public void shouldReturnValidSessionAttributesIfSessionAuthorized() throws Exception
    {
        OpenIdSessionAttributes returnedAttributes;
        IdentitySessionAttributes setAttributes = new IdentitySessionAttributes("a@b.com",
                "Alfonse", "Benvolio");

        _nonces = service.openIdBeginTransaction().get();

        identitySessionManager.authenticateSession(_nonces.getDelegateNonce(),
                SESSION_NONCE_TIMEOUT_SECS, setAttributes);

        returnedAttributes = service.openIdGetSessionAttributes(_nonces.getSessionNonce()).get();

        assertEquals(setAttributes.getEmail(), returnedAttributes.getUserId());
        assertEquals(setAttributes.getFirstName(), returnedAttributes.getFirstName());
        assertEquals(setAttributes.getLastName(), returnedAttributes.getLastName());

        sqlTrans.begin();
        String ts = factUser.create(UserID.fromInternal(returnedAttributes.getUserId()))
                .getOrganization().id().toTeamServerUserID().getString();
        sqlTrans.commit();

        assertEquals(ImmutableSet.of(Topics.getACLTopic(ts, true)), getTopicsPublishedTo());
    }

    @Test
    public void shouldThrowIfSessionNonceAlreadyUsed() throws Exception
    {
        shouldReturnValidSessionAttributesIfSessionAuthorized();

        try {
            service.openIdGetSessionAttributes(_nonces.getSessionNonce());
            fail("Expected exception.");
        } catch (ExExternalAuthFailure e) {
            // pass
        }
    }
}
