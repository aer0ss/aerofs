/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNotInvited;
import com.aerofs.base.id.OrganizationID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.sp.authentication.InMemoryServer.LdapSchema;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import com.google.protobuf.ByteString;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TestSP_LDAP extends AbstractSPTest
{
    private static InMemoryServer _server;
    private static AtomicInteger _idx = new AtomicInteger(1);
    LdapConfiguration _cfg = new LdapConfiguration();

    @BeforeClass
    public static void beforeClass() throws Exception { _server = new LdapSchema(false, false); }

    @AfterClass
    public static void tearDown() throws Exception { _server.stop(); }

    @Before
    public void updateConfigs()
    {
        _server.resetConfig(_cfg);
        authenticator = spy(
                new Authenticator(new IAuthority[] { new LdapAuthority(_cfg, aclNotificationPublisher, auditClient) })
        );
        rebuildSPService();
    }

    private User createUser(String id) throws Exception
    {
        return factUser.createFromExternalID(id);
    }

    private void createMockInvite(User user, boolean invited) throws Exception
    {
        OrganizationInvitation mockInvite = Mockito.mock(OrganizationInvitation.class);


        when(factOrgInvite.create(user, factOrg.create(OrganizationID.PRIVATE_ORGANIZATION)))
                .thenReturn(mockInvite);
        when(mockInvite.exists()).thenReturn(invited);

    }

    @Test
    public void testShouldSimpleSignIn() throws Exception
    {
        service.credentialSignIn(createTestUser("ldap1@example.com", "ldap1"),
                ByteString.copyFrom("ldap1".getBytes()));
    }

    @Test
    public void testCheckUserRecord() throws Exception
    {
        User u = createUser("test9@users.example.org");

        service.credentialSignIn(createTestUser("test9@users.example.org", "cred9"),
                ByteString.copyFrom("cred9".getBytes()));

        sqlTrans.begin();
        assertTrue(u.exists());
        assertEquals(u.getFullName()._first, "Firsty");
        assertEquals(u.getFullName()._last, "Lasto");
        verify(aclNotificationPublisher).publish_(u.getOrganization().id().toTeamServerUserID());
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

    // User does not exist in user database or invited to join.
    // Will throw ExNotInvited Exception.
    @Test(expected = ExNotInvited.class)
    public void testShouldDisallowUserNotInvited() throws Exception
    {
        Properties props = new Properties();
        props.put("ldap.invitation.required_for_signup", "true");
        ConfigurationProperties.setProperties(props);

        service.credentialSignIn(createTestUser("ldap2@example.com", "ldap1"),
                ByteString.copyFrom("ldap1".getBytes()));
    }

    // User that is in User database should be allow to sign in
    // with LDAP credentials.
    @Test
    public void testShouldAllowExistingUserSignIn() throws Exception
    {
        sqlTrans.begin();
        User user = factUser.createFromExternalID("ldap3@example.com");
        user.save(new byte[0], new FullName("Firsty", "Lasto"));
        sqlTrans.commit();

        service.credentialSignIn(createTestUser("ldap3@example.com", "ldap1"),
                ByteString.copyFrom("ldap1".getBytes()));
    }

    //User who is invited to join AeroFS are able to sign in
    //with external credentials.
    @Test
    public void testShouldAllowInvitedUserToSignIn() throws Exception
    {
        Properties props = new Properties();
        props.put("ldap.invitation.required_for_signup", "true");
        ConfigurationProperties.setProperties(props);

        User u = createUser("ldap4@example.com");
        createMockInvite(u, true);

        service.credentialSignIn(createTestUser("ldap4@example.com", "ldap1"),
                ByteString.copyFrom("ldap1".getBytes()));

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
        assertTrue(new LdapAuthority(_cfg).canAuthenticate(user.id()));
    }

    @Test
    public void shouldNotClaimExternalUser() throws Exception
    {
        User user = factUser.create(UserID.fromExternal("this@does.not.exist.in.ldap"));
        assertFalse(new LdapAuthority(_cfg).canAuthenticate(user.id()));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldNotAuthUnknownUser() throws Exception
    {
        service.credentialSignIn(
                "random@users.example.org", ByteString.copyFrom("hithere".getBytes()));
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

    private String generateUniqueEmail()
    {
                String s = new StringBuilder("uniq")
                .append(_idx.incrementAndGet())
                .append("@b.c")
                .toString();
        l.warn(s);
        return s;
    }

    @Test
    public void shouldSignInWithCompatFilter() throws Exception
    {
        String u = generateUniqueEmail();
        createTestUser(u, "dontcare");
        _cfg.USER_ADDITIONALFILTER = "uid=" + u.substring(0, u.indexOf('@'));

        service.signInUser(u, ByteString.copyFrom("dontcare".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void shouldFailIfExcludedByFilter() throws Exception
    {
        String u = generateUniqueEmail();
        createTestUser(u, "dontcare");
        _cfg.USER_ADDITIONALFILTER = "uid=" + "nosir";

        service.signInUser(u, ByteString.copyFrom("hithere".getBytes()));
    }

    @Test
    public void filterShouldNotAffectExistenceCheck() throws Exception
    {
        String u = generateUniqueEmail();
        createTestUser(u, "dontcare");
        _cfg.USER_ADDITIONALFILTER = "uid=bad_uid";
        assertTrue(new LdapAuthority(_cfg).canAuthenticate(UserID.fromExternal(u)));
    }

    // ---- Tests for LDAP credentials in legacy signin methods ----
    // Legacy format for LDAP is still cleartext...
    @Test
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
        verify(aclNotificationPublisher).publish_(u.getOrganization().id().toTeamServerUserID());
        sqlTrans.commit();
    }

    @Test(expected = ExBadCredential.class)
    public void legacyShouldDisallowBadCred() throws Exception
    {
        service.signInUser(createTestUser("badpw@users.example.org", "goodpw"),
                ByteString.copyFrom("badpw".getBytes()));
    }

    @Test(expected = ExBadCredential.class)
    public void legacyShouldDisallowEmptyPw() throws Exception
    {
        service.signInUser(createTestUser("emptypw@users.example.org", "himom"),
                ByteString.copyFrom(new byte[0]));
    }

    @Test(expected = ExBadCredential.class)
    public void legacyShouldNotAuthUnknownUser() throws Exception
    {
        service.signInUser("random@users.example.org", ByteString.copyFrom("hithere".getBytes()));
    }
}
