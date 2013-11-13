/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.sp.authentication.LdapConfiguration.SecurityType;
import com.aerofs.sp.authentication.InMemoryServer.LdapSchema;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Spy;

import java.io.IOException;
import java.security.cert.CertificateException;

public class TestSP_SSLConnectionLDAP extends AbstractSPTest
{
    @BeforeClass
    public static void beforeClass() throws Exception { _server = new LdapSchema(false, true); }

    @AfterClass
    public static void tearDown() throws Exception { _server.stop(); }

    @Before
    public void updateConfigs() throws CertificateException, IOException
    {
        _server.resetConfig(_cfg);
        _cfg.SERVER_SECURITY = SecurityType.SSL;
        _cfg.SERVER_CA_CERT = _server.getCertString();
    }

    @Test
    public void shouldSimpleSignIn() throws Exception
    {
        _server.add("dn: uid=ldap1,dc=users,dc=example,dc=org", "objectClass: inetOrgPerson",
                "cn: Joe Tester", "sn: Tester", "givenName: Joe",
                "mail: ldap1@users.example.org", "userPassword: ldap1", "uid: ldap1",
                "memberOf: cn=testgroup,dc=roles,dc=example,dc=org");
        service.signInUser("ldap1@users.example.org", ByteString.copyFrom("ldap1".getBytes()));
    }

    @Test
    public void shouldSimpleSignInTwo() throws Exception
    {
        _server.add("dn: uid=test2,dc=users,dc=example,dc=org", "objectClass: inetOrgPerson",
                "cn: Louise DoesNotExist", "sn: DoesNotExist", "givenName: Louise",
                "mail: test2@users.example.org", "userPassword: cred2", "uid: test2",
                "memberOf: cn=admins,dc=roles,dc=example,dc=org",
                "memberOf: cn=testgroup,dc=roles,dc=example,dc=org");

        service.signInUser("test2@users.example.org", ByteString.copyFrom("cred2".getBytes()));
    }

    @Test(expected =  ExExternalServiceUnavailable.class)
    public void shouldFailBadPort() throws Exception
    {
        _cfg.SERVER_PORT = 9;
        service.signInUser("test2@users.example.org", ByteString.copyFrom("cred2".getBytes()));
    }

    @Test(expected =  ExExternalServiceUnavailable.class)
    public void shouldFailBadCert() throws Exception
    {
        _cfg.SERVER_CA_CERT = "Hi, mom!!! Wait, this is not a certificate.";
        service.signInUser("test2@users.example.org", ByteString.copyFrom("cred2".getBytes()));
    }

    @Test(expected = ExExternalServiceUnavailable.class)
    public void shouldRequireSSL() throws Exception
    {
        _cfg.SERVER_SECURITY = SecurityType.NONE;
        service.signInUser("test2@users.example.org", ByteString.copyFrom("cred2".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void badCredShouldFail() throws Exception
    {
        service.signInUser("test2@users.example.org", ByteString.copyFrom("wango".getBytes()));
    }

    LdapConfiguration _cfg = new LdapConfiguration();
    @Spy Authenticator _authenticator = new Authenticator(
            new IAuthority[] { new LdapAuthority(_cfg) });
    private static InMemoryServer _server;
}