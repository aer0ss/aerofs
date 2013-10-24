/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.authentication.AuthenticatorFactory.NoProvisioning;
import com.aerofs.sp.authentication.InMemoryServer.LdapSchema;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Spy;

/**
 */
public class TestSP_NoProvisionLDAP extends AbstractSPTest
{
    @BeforeClass
    public static void beforeClass() throws Exception { _server = new LdapSchema(false, false); }

    @AfterClass
    public static void tearDown() throws Exception { _server.stop(); }

    @Before
    public void updateConfigs() { _server.resetConfig(_cfg); }

    @Test
    public void shouldSimpleSignIn() throws Exception
    {
        User user = factUser.createFromExternalID("nop1@users.example.org");
        sqlTrans.begin();
        saveUser(user);
        sqlTrans.commit();

        _server.add("dn: uid=nop1,dc=users,dc=example,dc=org", "objectClass: inetOrgPerson",
                "cn: Joe Tester", "sn: Tester", "givenName: Joe",
                "mail: nop1@users.example.org", "userPassword: cred1", "uid: nop1",
                "memberOf: cn=testgroup,dc=roles,dc=example,dc=org");
        service.signInUser(
                "nop1@users.example.org", ByteString.copyFrom("cred1".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldFailNoUser() throws Exception
    {
        _server.add("dn: uid=nop2,dc=users,dc=example,dc=org", "objectClass: inetOrgPerson",
                "cn: Joe Tester", "sn: Tester", "givenName: Joe",
                "mail: nop2@users.example.org", "userPassword: cred2", "uid: nop2",
                "memberOf: cn=testgroup,dc=roles,dc=example,dc=org");
        service.signInUser(
                "nop2@users.example.org", ByteString.copyFrom("cred2".getBytes()));
    }

    @Test(expected = ExLdapConfigurationError.class)
    public void shouldCheckSecurityType()
    {
        _cfg.SERVER_SECURITY = LdapConfiguration.convertPropertyNameToSecurityType("does.not.exist",
                "hi mom");
    }

    LdapConfiguration _cfg = new LdapConfiguration();
    // this supplies an instance of type IAuthenticator; when the InjectMocks-annotated
    // SPService instance asks for an IAuthenticator field, it will get this object.
    @Spy IAuthenticator _authenticator = new LdapAuthenticator(_cfg, new NoProvisioning());
    private static InMemoryServer _server;
}