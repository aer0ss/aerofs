/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.sp.authentication.AuthenticatorFactory.AutoProvisioning;
import com.aerofs.sp.authentication.InMemoryServer.LdapSchema;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Spy;

import static org.junit.Assert.assertEquals;

public class TestSP_AutoProvisionLDAP extends AbstractSPTest
{
    @Spy IAuthenticator _authenticator = new LdapAuthenticator(new AutoProvisioning());

    @BeforeClass
    public static void beforeClass() throws Exception { _server = new LdapSchema(false, false); }

    @AfterClass
    public static void tearDown() throws Exception { _server.stop(); }

    @Before
    public void updateConfigs() { _server.resetConfig(); }

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

    @Test
    public void checkUserRecord() throws Exception
    {
        _server.add("dn: uid=test9,dc=users,dc=example,dc=org", "objectClass: inetOrgPerson",
                "cn: Joe Tester", "sn: Tester", "givenName: Joe", "mail: test9@users.example.org",
                "userPassword: cred9", "uid: test9",
                "memberOf: cn=testgroup,dc=roles,dc=example,dc=org");
        service.signInUser("test9@users.example.org", ByteString.copyFrom("cred9".getBytes()));

        sqlTrans.begin();
        User u = factUser.createFromExternalID("test9@users.example.org");
        assert u.exists();
        assertEquals(u.getFullName()._first, "Joe");
        assertEquals(u.getFullName()._last, "Tester");
        sqlTrans.commit();
    }

    @Test(expected = ExBadCredential.class)
    public void shouldDisallowBadCred() throws Exception
    {
        service.signInUser("random@users.example.org", ByteString.copyFrom("badpw".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldDisallowEmptyPw() throws Exception
    {
        service.signInUser("random@users.example.org", ByteString.copyFrom(new byte[0]));
    }

    private static InMemoryServer _server;
}
