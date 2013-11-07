/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSP_LDAP extends AbstractSPTest
{
    private static InMemoryServer _server;
    LdapConfiguration _cfg = new LdapConfiguration();
    @Spy IAuthenticator _authenticator = new LdapAuthenticator(_cfg);

    @BeforeClass
    public static void beforeClass() throws Exception { _server = new LdapSchema(false, false); }

    @AfterClass
    public static void tearDown() throws Exception { _server.stop(); }

    @Before
    public void updateConfigs() { _server.resetConfig(_cfg); }

    @Test
    public void testShouldSimpleSignIn() throws Exception
    {
        service.credentialSignIn(createTestUser("ldap1@example.com", "ldap1"),
                ByteString.copyFrom("ldap1".getBytes()));
    }

    @Test
    public void testCheckUserRecord() throws Exception
    {
        service.credentialSignIn(createTestUser("test9@users.example.org", "cred9"),
                ByteString.copyFrom("cred9".getBytes()));

        sqlTrans.begin();
        User u = factUser.createFromExternalID("test9@users.example.org");
        assertTrue(u.exists());
        assertEquals(u.getFullName()._first, "Firsty");
        assertEquals(u.getFullName()._last, "Lasto");
        sqlTrans.commit();
    }

    @Test(expected = ExBadCredential.class)
    public void testShouldDisallowBadCred() throws Exception
    {
        service.credentialSignIn(createTestUser("badcred1@example.org", "never gonna give you up"),
                ByteString.copyFrom("badpw".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void testShouldDisallowEmptyPw() throws Exception
    {
        service.credentialSignIn(createTestUser("badcred2@example.org", "never gonna let you down"),
                ByteString.copyFrom(new byte[0]));
    }

    @Test(expected = ExLdapConfigurationError.class)
    public void shouldCheckSecurityType()
    {
        LdapConfiguration.convertPropertyNameToSecurityType("does.not.exist", "hi mom");
    }

    @Test
    public void shouldClaimLdapUser() throws Exception
    {
        User user = newUser();
        createTestUser(user.id().getString(), "dontcare");
        assertTrue(new LdapAuthenticator(_cfg).canAuthenticate(user));
    }

    @Test
    public void shouldNotClaimExternalUser() throws Exception
    {
        User user = factUser.create(UserID.fromExternal("this@does.not.exist.in.ldap"));
        assertFalse(new LdapAuthenticator(_cfg).canAuthenticate(user));
    }

    private String createTestUser(String email, String credentialString) throws Exception
    {
        String uid = email.substring(0, email.indexOf('@'));
        _server.add(
                "dn: uid=" + uid + ",dc=users,dc=example,dc=org",
                "cn: Firsty Lasto", "sn: Lasto", "givenName: Firsty",
                "objectClass: inetOrgPerson",
                "mail: " + email,
                "userPassword: " + credentialString,
                "uid: " + uid,
                "memberOf: cn=admins,dc=roles,dc=example,dc=org",
                "memberOf: cn=testgroup,dc=roles,dc=example,dc=org");
        return email;
    }

    // ---- Tests for LDAP credentials in legacy signin methods ----
    public void legacyShouldSimpleSignIn() throws Exception
    {
        service.signInUser(createTestUser("l_ldap1@example.com", "l_ldap1"),
                ByteString.copyFrom("l_ldap1".getBytes()));
    }

    @Test
    public void legacyCheckUserRecord() throws Exception
    {
        service.signInUser(createTestUser("l_test9@users.example.org", "l_cred9"),
                ByteString.copyFrom("l_cred9".getBytes()));

        sqlTrans.begin();
        User u = factUser.createFromExternalID("l_test9@users.example.org");
        assertTrue(u.exists());
        assertEquals(u.getFullName()._first, "Firsty");
        assertEquals(u.getFullName()._last, "Lasto");
        sqlTrans.commit();
    }

    @Test(expected = ExBadCredential.class)
    public void legacyShouldDisallowBadCred() throws Exception
    {
        service.signInUser("random@users.example.org", ByteString.copyFrom("badpw".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void legacyShouldDisallowEmptyPw() throws Exception
    {
        service.signInUser("random@users.example.org", ByteString.copyFrom(new byte[0]));
    }
}
