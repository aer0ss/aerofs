package com.aerofs.sp.server.integration;

import com.aerofs.base.C;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.SID;
import com.aerofs.lib.ex.ExNoAdminOrOwner;
import com.aerofs.sp.server.lib.cert.CertificateGenerator.CertificationResult;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestSP_DeactivateUser extends AbstractSPACLTest
{
    User u0;
    User u1;

    Device u0d0;
    Device u0d1;
    Device u1d0;

    @Override
    protected Device saveDevice(User user) throws Exception
    {
        CertificationResult cert = mock(CertificationResult.class);
        when(cert.toString()).thenReturn(AbstractSPCertificateBasedTest.RETURNED_CERT);
        when(cert.getSerial()).thenReturn(++AbstractSPCertificateBasedTest._lastSerialNumber);
        when(cert.getExpiry()).thenReturn(
                new Timestamp(System.currentTimeMillis() + C.DAY * 365L));

        Device d = super.saveDevice(user);
        d.addCertificate(cert);
        return d;
    }

    @Before
    public void setUp() throws Exception
    {
        sqlTrans.begin();

        u0 = saveUser();
        u1 = saveUser();

        u0d0 = saveDevice(u0);
        u0d1 = saveDevice(u0);
        u1d0 = saveDevice(u1);

        sqlTrans.commit();

        shareFolder(u0, SID.generate(), u1, Permissions.allOf(Permission.WRITE));
        shareAndJoinFolder(u0, SID.generate(), u1, Permissions.allOf(Permission.WRITE,
                Permission.MANAGE));
        SID sid = SID.generate();
        shareAndJoinFolder(u0, sid, u1, Permissions.allOf());
        leaveSharedFolder(u1, sid);
    }

    @Test(expected = ExNotFound.class)
    public void shouldThrowIfUserNotFound() throws Exception
    {
        setSession(u0);
        service.deactivateUser("totallynotauser", false);
    }

    @Test(expected = ExNoPerm.class)
    public void shouldnotAllowUserToDeactivateOther() throws Exception
    {
        setSession(u1);
        service.deactivateUser(u0.id().getString(), false);
    }

    @Test
    public void shouldNotAllowAdminToDeactivateUserOfDifferentOrg() throws Exception
    {
        sqlTrans.begin();
        u1.setOrganization(saveOrganization(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        setSession(u1);
        try {
            service.deactivateUser(u0.id().getString(), false);
            fail();
        } catch (ExNoPerm e) {
            // noop
        }
    }

    @Test
    public void shouldAllowUserToDeactivateSelf() throws Exception
    {
        setSession(u0);
        service.deactivateUser(u0.id().getString(), false);

        sqlTrans.begin();
        assertFalse(u0.exists());
        sqlTrans.commit();

        // TODO: verify crls and cmds
    }

    @Test
    public void shouldNotAllowUserToDeactivateSelfIfLastAdmin() throws Exception
    {
        shareAndJoinFolder(u0, SID.generate(), u1, Permissions.allOf(Permission.WRITE));

        setSession(u0);
        try {
            service.deactivateUser(u0.id().getString(), false);
            fail();
        } catch (ExNoAdminOrOwner e) {
            // noop
        }
    }

    @Test
    public void shouldAllowAdminToDeactivateUserOfSameOrg() throws Exception
    {
        sqlTrans.begin();
        u1.setOrganization(u0.getOrganization(), AuthorizationLevel.ADMIN);
        sqlTrans.commit();

        setSession(u1);
        service.deactivateUser(u0.id().getString(), false);

        sqlTrans.begin();
        assertFalse(u0.exists());
        sqlTrans.commit();

        // TODO: verify crls and cmds
    }

    @Test
    public void shouldAllowDeactivateAndReactivate() throws Exception
    {
        setSession(u0);
        service.deactivateUser(u0.id().getString(), false);

        sqlTrans.begin();
        String code = u0.addSignUpCode();
        sqlTrans.commit();

        service.signUpWithCode(code, ByteString.copyFromUtf8("password"), "NewFirst", "NewLast");

        sqlTrans.begin();
        assertTrue(u0.exists());
        sqlTrans.commit();
    }
}
