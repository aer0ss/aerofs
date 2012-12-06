/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.google.protobuf.ByteString;
import org.junit.Test;

public class TestSP_SignIn extends AbstractSPTest
{
    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingUserIDToSignIn()
            throws Exception
    {
        // Set AppRoot since SPService depends on Cfg which in turn depends on AppRoot.
        // TODO (WW) remove the dependency of SPService on Cfg.
        AppRoot.set("/non-existing");
        service.signIn(TEST_USER_1.toString(), ByteString.copyFrom(TEST_USER_1_CRED));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingTeamServerIDToSignIn()
            throws Exception
    {
        UserID tsUserID = new OrgID(123).toTeamServerUserID();
        ByteString tsUserPass = getTeamServerLocalPassword(tsUserID);

        service.signIn(tsUserID.toString(), tsUserPass);
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowTeamServerIDToSignInWithPasswords()
            throws Exception
    {
        setSessionUser(TEST_USER_1);

        UserID tsUserID = setupTeamServer();

        service.signIn(tsUserID.toString(), getTeamServerLocalPassword(tsUserID));
    }

    private UserID setupTeamServer()
            throws Exception
    {
        UserID tsUserID = UserID.fromInternal(service.getTeamServerUserID().get().getId());
        DID tsDID = new DID(UniqueID.generate());

        mockCertificateGeneratorAndIncrementSerialNumber();
        service.certifyTeamServerDevice(tsDID.toPB(), newCSR(tsUserID, tsDID));
        return tsUserID;
    }

    private ByteString getTeamServerLocalPassword(UserID tsUserID)
    {
        return ByteString.copyFrom(SecUtil.scrypt(C.TEAM_SERVER_LOCAL_PASSWORD, tsUserID));
    }
}
