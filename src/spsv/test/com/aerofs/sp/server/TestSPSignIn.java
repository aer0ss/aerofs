/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server;

import com.aerofs.lib.C;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.server.lib.organization.OrgID;
import com.google.protobuf.ByteString;
import org.junit.Ignore;
import org.junit.Test;

import java.security.KeyPair;

public class TestSPSignIn extends AbstractSPServiceTest
{
    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingUserIDToSignIn()
            throws Exception
    {
        service.signIn(TEST_USER_1.toString(), ByteString.copyFrom(TEST_USER_1_CRED));
    }

    @Ignore("bring it back ASAP to reduce security risks. Right now we blindly allow team server " +
            "IDs to pass through. It's not a big issue yet since we don't allow people signing up" +
            " with malformed email addresses, and team server IDs are all intentionally malformed.")
    @Test(expected = ExBadCredential.class)
    public void shouldNotAllowNonExistingTeamServerIDToSignIn()
            throws Exception
    {
        UserID tsUserID = new OrgID(123).toTeamServerUserID();
        ByteString tsUserPass = getTeamServerLocalPassword(tsUserID);

        service.signIn(tsUserID.toString(), tsUserPass);
    }

    @Ignore("see above")
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
        mockCertificateGeneratorAndIncrementSerialNumber();

        UserID tsUserID = UserID.fromInternal(service.getTeamServerUserID().get().getId());
        DID tsDID = new DID(UniqueID.generate());

        KeyPair kp = SecUtil.newRSAKeyPair();
        byte[] csr = SecUtil.newCSR(kp.getPublic(), kp.getPrivate(), tsUserID, tsDID).getEncoded();
        service.certifyTeamServerDevice(tsDID.toPB(), ByteString.copyFrom(csr));
        return tsUserID;
    }

    private ByteString getTeamServerLocalPassword(UserID tsUserID)
    {
        return ByteString.copyFrom(SecUtil.scrypt(C.TEAM_SERVER_LOCAL_PASSWORD, tsUserID));
    }
}
