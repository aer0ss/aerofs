/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.proto.Sp.AuthorizeAPIClientReply;
import com.aerofs.proto.Sp.MobileAccessCode;
import com.aerofs.sp.server.lib.user.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TestSP_AuthorizeAPIClient extends AbstractSPTest
{
    private User user;

    @Before
    public void setup() throws Exception
    {
        sqlTrans.begin();
        user = saveUser();
        sqlTrans.commit();
    }

    @After
    public void teardown()
    {
        session.remove();
    }

    @Test(expected = ExNoPerm.class)
    public void testShouldFailIfNotSignedIn() throws Exception
    {
        service.getMobileAccessCode().get();
    }

    @Test
    public void testShouldGenerateAuthNonce() throws Exception
    {
        session.setUser(user);

        MobileAccessCode auth = service.getMobileAccessCode().get();
        assertNotNull( auth );
        assertNotNull( auth.getAccessCode() );
        assertFalse( auth.getAccessCode().isEmpty() );
    }

    @Test
    public void testShouldAuthDevice() throws Exception
    {
        session.setUser(user);
        MobileAccessCode auth = service.getMobileAccessCode().get();
        session.remove();

        AuthorizeAPIClientReply attrs = service.authorizeAPIClient(auth.getAccessCode(),
                "My Test Device").get();
        String expectedOrg = attrs.getOrgId();

        assertNotNull( attrs );
        assertNotNull( attrs.getUserId() );
        assertNotNull( attrs.getOrgId() );
        assertEquals( user.id().getString(), attrs.getUserId() );
        assertEquals( expectedOrg, attrs.getOrgId() );
        assertEquals( attrs.getIsOrgAdmin(), Boolean.TRUE );
    }

    @Test
    public void testShouldConsumeNonce() throws Exception
    {
        session.setUser(user);
        MobileAccessCode auth = service.getMobileAccessCode().get();
        session.remove();

        service.authorizeAPIClient(auth.getAccessCode(), "My Test Device").get();

        try {
            service.authorizeAPIClient(auth.getAccessCode(), "My Test Device").get();
            fail();
        } catch (ExExternalAuthFailure eeaf) { /* expected */ }
    }

    @Test
    public void testShouldDisallowSecondSignin() throws Exception
    {
        session.setUser(user);
        MobileAccessCode auth = service.getMobileAccessCode().get();
        session.remove();

        session.setUser(user);
        try {
            service.authorizeAPIClient(auth.getAccessCode(), "My Test Device");
            fail("Expected excepted");
        } catch (ExNoPerm enp) { /* expected */ }
    }

    @Test(expected = ExExternalAuthFailure.class)
    public void testShouldFailWithBadNonce() throws Exception
    {
        service.authorizeAPIClient("Hi mom", "Devices are people too");
    }
}
