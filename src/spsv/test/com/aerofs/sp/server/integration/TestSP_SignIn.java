/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.sp.server.lib.id.StripeCustomerID;
import com.aerofs.lib.Param;
import com.aerofs.lib.SecUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestSP_SignIn extends AbstractSPTest
{
    private UserID _tsUserID;
    private DID _tsDID;

    @SuppressWarnings("unchecked")
    @Before
    public void mockVerkehr()
    {
        // Deliver payload for commands.
        mockAndCaptureVerkehrDeliverPayload();

        // Update CRL for the verkehr CRL.
        when(verkehrAdmin.updateCRL(any(ImmutableList.class)))
                .thenReturn(UncancellableFuture.<Common.Void>createSucceeded(null));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingUserIDToSignIn()
            throws Exception
    {
        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(USER_1.getString(), ByteString.copyFrom(USER_1_CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingTeamServerIDToSignIn()
            throws Exception
    {
        UserID _tsUserID = new OrganizationID(123).toTeamServerUserID();
        ByteString tsUserPass = getTeamServerLocalPassword(_tsUserID);

        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(_tsUserID.getString(), tsUserPass);
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerIDToSignInWithPasswords()
            throws Exception
    {
        setSessionUser(USER_1);
        setupTeamServer();

        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(_tsUserID.getString(), getTeamServerLocalPassword(_tsUserID));
    }

    @Test
    public void shouldAllowTeamServerLoginWithValidCertificate()
            throws Exception
    {
        setSessionUser(USER_1);
        setupTeamServer();

        // Credentials do not need to be supplied here.
        mockCertificateAuthenticatorSetAuthenticatedState();
        service.signIn(_tsUserID.getString(), ByteString.copyFrom(new byte[0]));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerLoginWithRevokedCertificate()
            throws Exception
    {
        setSessionUser(USER_1);

        // Setup the team server (obtail device certificate).
        setupTeamServer();

        // Revoke all device certificates including the one just created.
        service.unlinkTeamServerDevice(_tsDID.toPB(), false);

        // Expect the sign in to fail even when the cert has been verified with nginx.
        mockCertificateAuthenticatorSetAuthenticatedState();
        service.signIn(_tsUserID.getString(), getTeamServerLocalPassword(_tsUserID));
    }

    private void setupTeamServer()
            throws Exception
    {
        mockCertificateAuthenticatorSetAuthenticatedState();

        service.addOrganization("An Awesome Team", null, StripeCustomerID.TEST.getString());

        _tsUserID = UserID.fromInternal(service.getTeamServerUserID().get().getId());
        _tsDID = new DID(UniqueID.generate());

        mockCertificateGeneratorAndIncrementSerialNumber();
        service.registerTeamServerDevice(_tsDID.toPB(), newCSR(_tsUserID, _tsDID), false, "", "", "");
    }

    private ByteString getTeamServerLocalPassword(UserID _tsUserID)
    {
        return ByteString.copyFrom(SecUtil.scrypt(Param.MULTIUSER_LOCAL_PASSWORD, _tsUserID));
    }
}
