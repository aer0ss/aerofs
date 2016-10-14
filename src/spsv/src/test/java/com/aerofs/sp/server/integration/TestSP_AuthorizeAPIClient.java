/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.proto.Sp.AuthorizeAPIClientReply;
import com.aerofs.proto.Sp.MobileAccessCode;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TestSP_AuthorizeAPIClient extends AbstractSPTest
{
    private Organization org;
    private User admin;
    private User user;

    private void setUser(User user)
    {
        session.setUser(user);
        session.setBasicAuthDate(System.currentTimeMillis());
    }

    private ArrayList<ListenableFuture<MobileAccessCode>> newCodeList() throws Exception
    {
        ArrayList<ListenableFuture<MobileAccessCode>> list =
                new ArrayList<ListenableFuture<MobileAccessCode>>();
        list.add(service.getAccessCode());
        list.add(service.getAccessCodeForMobile());
        return list;
    }

    @Before
    public void setup() throws Exception
    {
        sqlTrans.begin();
        admin = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);
        org = admin.getOrganization();
        user = saveUser();
        user.setOrganization(org, AuthorizationLevel.USER);
        sqlTrans.commit();
    }

    @After
    public void teardown()
    {
        session.deauthorize();
    }

    @Test(expected = ExNotAuthenticated.class)
    public void testCodeShouldFailIfNotSignedIn() throws Exception
    {
        service.getAccessCode().get();
    }

    @Test(expected = ExNotAuthenticated.class)
    public void testMobileAppCodeShouldFailIfNotSignedIn() throws Exception
    {
        service.getAccessCodeForMobile().get();
    }

    @Test
    public void testShouldGenerateAuthNonce() throws Exception
    {
        setUser(user);

        for (ListenableFuture<MobileAccessCode> accessCode : newCodeList())
        {
            MobileAccessCode auth = accessCode.get();
            assertNotNull( auth );
            assertNotNull( auth.getAccessCode() );
            assertFalse( auth.getAccessCode().isEmpty() );
        }
    }

    @Test
    public void testShouldAuthDevice() throws Exception
    {
        for (User sessionUser : newArrayList(user, admin))
        {
            setUser(sessionUser);

            for (ListenableFuture<MobileAccessCode> accessCode : newCodeList()){
                MobileAccessCode auth = accessCode.get();
                session.deauthorize();

                AuthorizeAPIClientReply attrs = service.authorizeAPIClient(auth.getAccessCode(),
                        "My Test Device").get();

                assertNotNull( attrs );
                assertNotNull( attrs.getUserId() );
                assertNotNull( attrs.getOrgId() );
                assertEquals( sessionUser.id().getString(), attrs.getUserId() );
                assertEquals( org.id().toString(), attrs.getOrgId() );
                // hard-coding this so we can avoid querying the sql database to determine whether
                //   the session user is the admin.
                assertEquals( sessionUser == admin, attrs.getIsOrgAdmin() );
            }
        }
    }

    @Test
    public void testShouldConsumeNonce() throws Exception
    {
        setUser(user);
        for (ListenableFuture<MobileAccessCode> accessCode : newCodeList()){
            MobileAccessCode auth = accessCode.get();
            session.deauthorize();

            service.authorizeAPIClient(auth.getAccessCode(), "My Test Device").get();

            try {
                service.authorizeAPIClient(auth.getAccessCode(), "My Test Device").get();
                fail();
            } catch (ExExternalAuthFailure eeaf) { /* expected */ }
        }
    }

    @Test
    public void testShouldDisallowSecondSignin() throws Exception
    {
        setUser(user);
        for (ListenableFuture<MobileAccessCode> accessCode : newCodeList()){
            MobileAccessCode auth = accessCode.get();
            session.deauthorize();

            setUser(user);
            try {
                service.authorizeAPIClient(auth.getAccessCode(), "My Test Device");
                fail("Expected excepted");
            } catch (ExNoPerm enp) { /* expected */ }
        }
    }

    @Test(expected = ExExternalAuthFailure.class)
    public void testShouldFailWithBadNonce() throws Exception
    {
        service.authorizeAPIClient("Hi mom", "Devices are people too");
    }
}
