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
import com.aerofs.sp.server.lib.organization.OrganizationID;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class TestSP_SignIn extends AbstractSPTest
{
    @SuppressWarnings("unchecked")
    @Before
    public void mockVerkehrAdminUpdateCRL()
    {
        when(verkehrAdmin.updateCRL_(any(ImmutableList.class)))
                .thenReturn(UncancellableFuture.<Common.Void>createSucceeded(null));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingUserIDToSignIn()
            throws Exception
    {
        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(USER_1.toString(), ByteString.copyFrom(USER_1_CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingTeamServerIDToSignIn()
            throws Exception
    {
        UserID tsUserID = new OrganizationID(123).toTeamServerUserID();
        ByteString tsUserPass = getTeamServerLocalPassword(tsUserID);

        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(tsUserID.toString(), tsUserPass);
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerIDToSignInWithPasswords()
            throws Exception
    {
        setSessionUser(USER_1);
        UserID tsUserID = setupTeamServer();

        mockCertificateAuthenticatorSetUnauthorizedState();
        service.signIn(tsUserID.toString(), getTeamServerLocalPassword(tsUserID));
    }

    @Test
    public void shouldAllowTeamServerLoginWithValidCertificate()
            throws Exception
    {
        setSessionUser(USER_1);
        UserID tsUserID = setupTeamServer();

        // Credentials do not need to be supplied here.
        mockCertificateAuthenticatorSetAuthenticatedState();
        service.signIn(tsUserID.toString(), ByteString.copyFrom(new byte[0]));
    }

    // Ignore until revoke team server device certificate has been completed.
    @Ignore
    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerLoginWithRevokedCertificate()
            throws Exception
    {
        setSessionUser(USER_1);

        // Setup the team server (obtail device certificate).
        UserID tsUserID = setupTeamServer();

        // Revoke all device certificates including the one just created.
        service.revokeAllTeamServerDeviceCertificates();

        // Expect the sign in to fail even when the cert has been verified with nginx.
        mockCertificateAuthenticatorSetAuthenticatedState();
        service.signIn(tsUserID.toString(), getTeamServerLocalPassword(tsUserID));
    }

    private UserID setupTeamServer()
            throws Exception
    {
        mockCertificateAuthenticatorSetAuthenticatedState();

        UserID tsUserID = UserID.fromInternal(service.getTeamServerUserID().get().getId());
        DID tsDID = new DID(UniqueID.generate());

        mockCertificateGeneratorAndIncrementSerialNumber();
        service.certifyTeamServerDevice(tsDID.toPB(), newCSR(tsUserID, tsDID));
        return tsUserID;
    }

    private ByteString getTeamServerLocalPassword(UserID tsUserID)
    {
        return ByteString.copyFrom(SecUtil.scrypt(Param.TEAM_SERVER_LOCAL_PASSWORD, tsUserID));
    }
}
