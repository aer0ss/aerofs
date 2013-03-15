/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.DID;
import com.aerofs.lib.Param;
import com.aerofs.lib.SecUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UniqueID;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.id.OrganizationID;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestSP_SignIn extends AbstractSPTest
{
    private User tsUser;
    private Device tsDevice;

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
        service.signIn(USER_1.id().getString(), ByteString.copyFrom(CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingTeamServerIDToSignIn()
            throws Exception
    {
        UserID _tsUserID = new OrganizationID(123).toTeamServerUserID();

        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(_tsUserID.getString(), getTeamServerLocalPassword(_tsUserID));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerIDToSignInWithPasswords()
            throws Exception
    {
        setSessionUser(USER_1);
        setupTeamServer();

        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(tsUser.id().getString(), getTeamServerLocalPassword());
    }

    @Test
    public void shouldAllowTeamServerLoginWithValidCertificate()
            throws Exception
    {
        setSessionUser(USER_1);
        setupTeamServer();

        // Credentials do not need to be supplied here.
        mockCertificateAuthenticatorSetAuthenticatedState(tsUser, tsDevice);
        service.signIn(tsUser.id().getString(), tsDevice.id().toPB());
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerLoginWithRevokedCertificate()
            throws Exception
    {
        // Setup the team server (obtain device certificate).
        setSessionUser(USER_1);
        setupTeamServer();

        // Revoke all device certificates including the one just created.
        service.unlinkDevice(tsDevice.id().toPB(), false);

        // Expect the sign in to fail even when the cert has been verified with nginx.
        mockCertificateAuthenticatorSetAuthenticatedState(tsUser, tsDevice);
        service.signIn(tsUser.id().getString(), tsDevice.id().toPB());
    }

    private void setupTeamServer()
            throws Exception
    {
        tsUser = factUser.create(UserID.fromInternal(service.getTeamServerUserID().get().getId()));
        tsDevice = factDevice.create(new DID(UniqueID.generate()));

        mockCertificateAuthenticatorSetAuthenticatedState(tsUser, tsDevice);
        mockCertificateGeneratorAndIncrementSerialNumber();

        service.registerTeamServerDevice(tsDevice.id().toPB(), newCSR(tsUser, tsDevice), false, "", "", "");
    }

    private ByteString getTeamServerLocalPassword()
    {
        return getTeamServerLocalPassword(tsUser.id());
    }

    private ByteString getTeamServerLocalPassword(UserID userID)
    {
        return ByteString.copyFrom(SecUtil.scrypt(Param.MULTIUSER_LOCAL_PASSWORD, userID));
    }
}
