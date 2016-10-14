/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.ids.UserID;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.ssmp.SSMPIdentifiers;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;

import static com.aerofs.proto.Sp.ExtAuthSessionAttributes;
import static com.aerofs.proto.Sp.ExtAuthSessionNonces;
import static org.junit.Assert.*;

public class TestSP_ExtAuth extends AbstractSPTest
{
    final int SESSION_NONCE_TIMEOUT_SECS = 5;

    private ExtAuthSessionNonces _nonces;

    @Before
    public void setUp()
    {
        _nonces = null;
    }

    @Test
    public void shouldThrowIfNoSessionExists() throws Exception
    {
       try {
           service.extAuthGetSessionAttributes("Session that doesn't exist");
           fail("Expected exception.");
       } catch (ExExternalAuthFailure e) {
           // pass
       }
    }

    @Test
    public void shouldReturnEmptySessionAttributesIfSessionNotYetAuthorized() throws Exception
    {
        ExtAuthSessionAttributes returnedAttributes;

        _nonces = service.extAuthBeginTransaction().get();
        returnedAttributes = service.extAuthGetSessionAttributes(_nonces.getSessionNonce()).get();

        assertTrue(returnedAttributes.getUserId().isEmpty());
        assertTrue(returnedAttributes.getFirstName().isEmpty());
        assertTrue(returnedAttributes.getLastName().isEmpty());
    }

    @Test
    public void shouldReturnValidSessionAttributesIfSessionAuthorized() throws Exception
    {
        ExtAuthSessionAttributes returnedAttributes;
        IdentitySessionAttributes setAttributes = new IdentitySessionAttributes("a@b.com",
                "Alfonse", "Benvolio");

        _nonces = service.extAuthBeginTransaction().get();

        identitySessionManager.authenticateSession(_nonces.getDelegateNonce(),
                SESSION_NONCE_TIMEOUT_SECS, setAttributes);

        returnedAttributes = service.extAuthGetSessionAttributes(_nonces.getSessionNonce()).get();

        assertEquals(setAttributes.getEmail(), returnedAttributes.getUserId());
        assertEquals(setAttributes.getFirstName(), returnedAttributes.getFirstName());
        assertEquals(setAttributes.getLastName(), returnedAttributes.getLastName());

        sqlTrans.begin();
        String ts = factUser.create(UserID.fromInternal(returnedAttributes.getUserId()))
                .getOrganization().id().toTeamServerUserID().getString();
        sqlTrans.commit();

        assertEquals(ImmutableSet.of(SSMPIdentifiers.getACLTopic(ts)), getTopicsPublishedTo());
    }

    @Test
    public void shouldThrowIfSessionNonceAlreadyUsed() throws Exception
    {
        shouldReturnValidSessionAttributesIfSessionAuthorized();

        try {
            service.extAuthGetSessionAttributes(_nonces.getSessionNonce());
            fail("Expected exception.");
        } catch (ExExternalAuthFailure e) {
            // pass
        }
    }
}
