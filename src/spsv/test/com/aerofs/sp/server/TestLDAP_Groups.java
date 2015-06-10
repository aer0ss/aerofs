/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.proto.Sp.ListGroupsReply;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.IAuthority;
import com.aerofs.sp.authentication.InMemoryServer;
import com.aerofs.sp.authentication.LdapAuthority;
import com.aerofs.sp.authentication.LdapConfiguration;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.integration.AbstractSPTest;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.SearchScope;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLDAP_Groups extends AbstractSPTest
{

    public static class LdapGroupSchema extends InMemoryServer
    {
        public LdapGroupSchema(boolean useTls, boolean useSsl) throws Exception
        {
            super(useTls, useSsl);
        }

        @Override
        protected void createSchema(InMemoryDirectoryServer server) throws Exception
        {
            add("dn: dc=org", "objectClass: top", "objectClass: domain", "dc: org");
            add("dn: dc=example,dc=org", "objectClass: top", "objectClass: domain",
                    "dc: example");

            add("dn: dc=users,dc=example,dc=org", "objectClass: top",
                    "objectClass: domain", "dc: users");

            add("dn: dc=roles,dc=example,dc=org", "objectClass: top",
                    "objectClass: domain", "dc: roles");
            add("dn: " + testGroupDN,
                    "objectClass: groupOfNames", "cn: testgroup");
            add("dn: " + adminGroupDN,
                    "objectClass: groupOfUniqueNames", "cn: admins");
            add("dn: " + urlGroupDN,
                    "objectClass: groupOfUniqueURLs", "cn: urls");
            add("dn: " + posixGroupDN,
                    "objectClass: posixGroup", "cn: posix");
        }

        public static String groupBaseDN = "dc=roles,dc=example,dc=org";
        public static String testGroupDN = "cn=testgroup," + groupBaseDN;
        public static String adminGroupDN = "cn=admins," + groupBaseDN;
        public static String urlGroupDN = "cn=urls," + groupBaseDN;
        public static String posixGroupDN = "cn=posix," + groupBaseDN;
        public static String userBaseDN = "dc=users,dc=example,dc=org";
    }

    LdapConfiguration _cfg = new LdapConfiguration();
    InvitationHelper _invitationHelper = mock(InvitationHelper.class);
    InvitationEmailer.Factory _invitationEmailFact = new InvitationEmailer.Factory();
    LdapGroupSynchronizer _syncer;
    protected User admin = null;
    protected Organization org = null;
    private static AtomicInteger _idx = new AtomicInteger(1);

    private static ByteString creds = ByteString.copyFrom("temp123".getBytes());

    private static InMemoryServer _server;

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        _server = new LdapGroupSchema(false, false);
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
        _server.stop();
    }

    @Before
    public void updateConfigs()
            throws Exception
    {
        when(_invitationHelper.createBatchFolderInvitationAndEmailer(any(), any(), any(), any()))
                .thenReturn(_invitationEmailFact.doesNothing());
        _server.resetConfig(_cfg);
        _syncer = new LdapGroupSynchronizer(_cfg, factUser, factGroup, _invitationHelper);
        authenticator = new Authenticator(new IAuthority[] {
                new LdapAuthority(_cfg, aclNotificationPublisher, auditClient)
        });
        rebuildSPService();
        sqlTrans.begin();
        try {
            admin = saveUser();
            org = admin.getOrganization();
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.rollback();
            throw e;
        }
        setSession(admin);
    }

    @After
    public void clearLDAPTree()
            throws Exception
    {
        clearGroupMembers();
        clearUsers();
    }

    @Test
    public void shouldCreateGroups()
            throws Exception
    {
        ListGroupsReply reply = service.listGroups(10, 0, "").get();
        assertEquals(reply.getGroupsCount(), 0);
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        reply = service.listGroups(10, 0, "").get();
        assertEquals(reply.getGroupsCount(), 4);
    }

    @Test
    public void shouldDeleteGroups()
            throws Exception
    {
        sqlTrans.begin();
        Group group;
        try {
            group = factGroup.save("an external group", org.id(), "externalid".getBytes());
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.rollback();
            throw e;
        }

        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        sqlTrans.begin();
        try {
            assertFalse(group.exists());
            sqlTrans.commit();
        } catch (Exception e) {
            sqlTrans.rollback();
            throw e;
        }
    }

    @Test
    public void shouldDeleteGroupsWithMembers()
            throws Exception
    {
        User user;
        sqlTrans.begin();
        user = saveUser();
        sqlTrans.commit();

        String ldapUserEmail = generateUniqueEmail();
        createAndSaveUser(ldapUserEmail);
        User ldapUser = factUser.createFromExternalID(ldapUserEmail);

        sqlTrans.begin();
        Group group;
        group = factGroup.save("an external group", org.id(), "externalid".getBytes());
        group.addMember(user);
        group.addMember(ldapUser);
        sqlTrans.commit();

        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        sqlTrans.begin();
        assertFalse(group.exists());
        assertEquals(user.getGroups().size(), 0);
        assertEquals(ldapUser.getGroups().size(), 0);
        sqlTrans.commit();
    }

    @Test
    public void shouldRemoveUsers()
            throws Exception
    {
        String email = generateUniqueEmail();
        addTestMember(createAndSaveUser(email));
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 1);
        sqlTrans.commit();

        clearGroupMembers();
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 0);
        sqlTrans.commit();
    }

    @Test
    public void shouldNotAddUnassociatedUserToGroup()
            throws Exception
    {
        String email = generateUniqueEmail();
        createAndSaveUser(email);
        //service is created
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 0);
        sqlTrans.commit();
    }

    @Test
    public void shouldAddStaticMember()
            throws Exception
    {
        String email = generateUniqueEmail();
        addTestMember(createAndSaveUser(email));
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 1);
        sqlTrans.commit();
    }

    @Test
    public void shouldAddSeveralStaticMembers()
            throws Exception
    {
        String email1 = generateUniqueEmail(), email2 = generateUniqueEmail();
        addTestMember(createAndSaveUser(email1));
        addTestMember(createAndSaveUser(email2));

        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        User user1 = factUser.createFromExternalID(email1);
        User user2 = factUser.createFromExternalID(email2);

        sqlTrans.begin();
        assertEquals(user1.getGroups().size(), 1);
        assertEquals(user2.getGroups().size(), 1);
        sqlTrans.commit();
    }

    @Test
    public void shouldAddMemberToSeveralGroups()
            throws Exception
    {
        String email = generateUniqueEmail();
        String dn = createAndSaveUser(email);
        addTestMember(dn);
        addAdminMember(dn);

        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 2);
        sqlTrans.commit();
    }

    @Test
    public void shouldAddNestedGroupMembers()
            throws Exception
    {
        String email = generateUniqueEmail();
        addTestMember(createAndSaveUser(email));
        addAdminMember(LdapGroupSchema.testGroupDN);
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 2);
        sqlTrans.commit();

        clearGroupMembers();
    }

    @Test
    public void shouldAddMemberSpecifiedInDynamicQuery()
            throws Exception
    {
        String email = generateUniqueEmail();
        createAndSaveUser(email);
        String ldapUrl = "ldap:///" + LdapGroupSchema.userBaseDN + "??one?(mail=" + email + ")";
        addMemberURL(ldapUrl);
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 1);
        sqlTrans.commit();
    }

    @Test
    public void shouldAddMemberSpecifiedByUID()
            throws Exception
    {
        String email = generateUniqueEmail();
        createAndSaveUser(email);
        addPosixUser(email);
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        User user = factUser.createFromExternalID(email);

        sqlTrans.begin();
        assertEquals(user.getGroups().size(), 1);
        sqlTrans.commit();
    }

    @Test
    public void shouldHandleMultipleAttributeTypes()
            throws Exception
    {
        List<String> emails = Lists.newArrayList(generateUniqueEmail(), generateUniqueEmail(),
                generateUniqueEmail(), generateUniqueEmail());
        List<String> dns = Lists.newArrayList();
        List<User> users = Lists.newArrayList();
        for (String email : emails) {
            dns.add(createAndSaveUser(email));
            users.add(factUser.createFromExternalID(email));
        }

        _server.modify(LdapGroupSchema.testGroupDN, new Modification(ModificationType.ADD, "member", dns.get(0)));
        _server.modify(LdapGroupSchema.testGroupDN, new Modification(ModificationType.ADD, "uniqueMember", dns.get(1)));
        String ldapUrl = "ldap:///" + LdapGroupSchema.userBaseDN + "??one?(mail=" + emails.get(2) + ")";
        _server.modify(LdapGroupSchema.testGroupDN, new Modification(ModificationType.ADD, "memberURL", ldapUrl));
        String uid = emails.get(3).substring(0, emails.get(3).indexOf('@'));
        _server.modify(LdapGroupSchema.testGroupDN, new Modification(ModificationType.ADD, "memberUid", uid));

        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        sqlTrans.begin();
        for (User user : users) {
            assertEquals(user.getGroups().size(), 1);
        }
        sqlTrans.commit();
    }

    @Test
    public void shouldWorkWithScope()
            throws Exception
    {
        _cfg.GROUP_BASE = LdapGroupSchema.groupBaseDN;
        _cfg.GROUP_SCOPE = SearchScope.BASE;
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        ListGroupsReply reply = service.listGroups(10, 0, "").get();
        assertEquals(reply.getGroupsCount(), 0);

        _cfg.GROUP_SCOPE = SearchScope.ONE;
        _server.add("dn: dc=nested," + LdapGroupSchema.groupBaseDN, "objectClass: top",
                "objectClass: domain", "dc: nested");
        _server.add("dn: cn=deeper,dc=nested," + LdapGroupSchema.groupBaseDN,
                "objectClass: groupOfNames", "cn: deeper");
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        reply = service.listGroups(10, 0, "").get();
        assertEquals(reply.getGroupsCount(), 4);

        _cfg.GROUP_SCOPE = SearchScope.SUB;
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        reply = service.listGroups(10, 0 ,"").get();
        assertEquals(reply.getGroupsCount(), 5);

        _server.delete("cn=deeper,dc=nested," + LdapGroupSchema.groupBaseDN);
        _server.delete(("dc=nested," + LdapGroupSchema.groupBaseDN));
    }

    @Test
    public void shouldIgnoreGroupMembersNotInScope()
            throws Exception
    {
        String email = generateUniqueEmail();
        String uid = email.substring(0, email.indexOf('@'));
        // this dn isn't under the user base
        String dn = "uidNumber=" + uid + ",dc=example,dc=org";
        _server.add("dn: " + dn, "cn: Firsty Lasto", "sn: Lasto", "givenName: Firsty",
                "objectClass: inetOrgPerson", "mail: " + email,
                "userPassword: temp123", "uidNumber: " + uid);

        addTestMember(dn);
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        sqlTrans.begin();
        Group testGroup = factGroup.createFromExternalID(
                getExternalIDofGroupDN(LdapGroupSchema.testGroupDN));
        assertEquals(testGroup.listMembers().size(), 0);
        sqlTrans.commit();
    }

    @Test
    public void shouldAddNonexistentUsers()
            throws Exception
    {
        String email = generateUniqueEmail();
        String dn = addUser(email);
        addTestMember(dn);
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        sqlTrans.begin();
        Group testGroup = factGroup.createFromExternalID(
                getExternalIDofGroupDN(LdapGroupSchema.testGroupDN));
        assertEquals(testGroup.listMembers().size(), 1);
        sqlTrans.commit();
    }

    @Test
    public void shouldUpdateNameOfGroups()
            throws Exception
    {
        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);

        sqlTrans.begin();
        Group testGroup = factGroup.createFromExternalID(
            getExternalIDofGroupDN(LdapGroupSchema.testGroupDN));
        String commonName = testGroup.getCommonName();
        assertEquals(commonName, "testgroup");

        testGroup.setCommonName("othername");
        sqlTrans.commit();

        assertFalse(_syncer.synchronizeGroups(sqlTrans, org)._errored);
        sqlTrans.begin();
        commonName = testGroup.getCommonName();
        sqlTrans.commit();
        assertEquals(commonName, "testgroup");
    }

    private String generateUniqueEmail()
    {
        return "unique_" + _idx.incrementAndGet() + "@b.c";
    }

    private String addUser(String email)
            throws Exception
    {
        String uid = email.substring(0, email.indexOf('@'));
        _server.add("dn: uidNumber=" + uid + ",dc=users,dc=example,dc=org",
                "cn: Firsty Lasto",
                "sn: Lasto",
                "givenName: Firsty",
                "objectClass: inetOrgPerson",
                "mail: " + email,
                "userPassword: temp123",
                "uidNumber: " + uid);
        return "uidNumber=" + uid + ",dc=users,dc=example,dc=org";
    }

    private void addTestMember(String dn)
            throws Exception
    {
        _server.modify(LdapGroupSchema.testGroupDN, new Modification(ModificationType.ADD, "member", dn));
    }

    private void addAdminMember(String dn)
            throws Exception
    {
        _server.modify(LdapGroupSchema.adminGroupDN, new Modification(ModificationType.ADD, "uniqueMember", dn));
    }

    private void addMemberURL(String url)
            throws Exception
    {
        _server.modify(LdapGroupSchema.urlGroupDN, new Modification(ModificationType.ADD, "memberUrl", url));
    }

    private void addPosixUser(String email)
            throws Exception
    {
        String uid = email.substring(0, email.indexOf('@'));
        _server.modify(LdapGroupSchema.posixGroupDN, new Modification(ModificationType.ADD, "memberUid", uid));
    }

    private String createAndSaveUser(String email)
            throws Exception
    {
        String dn = addUser(email);
        service.credentialSignIn(email, creds).get();
        return dn;
    }

    private void clearGroupMembers()
            throws Exception
    {
        String[] groups = {LdapGroupSchema.testGroupDN, LdapGroupSchema.adminGroupDN,
                LdapGroupSchema.urlGroupDN, LdapGroupSchema.posixGroupDN};
        for(String dn : groups) {
            _server.delete(dn);
        }

        _server.add("dn: " + LdapGroupSchema.testGroupDN, "objectClass: groupOfNames", "cn: testgroup");
        _server.add("dn: " + LdapGroupSchema.adminGroupDN, "objectClass: groupOfUniqueNames", "cn: admins");
        _server.add("dn: " + LdapGroupSchema.urlGroupDN, "objectClass: groupOfUniqueURLs", "cn: urls");
        _server.add("dn: " + LdapGroupSchema.posixGroupDN, "objectClass: posixGroup", "cn: posix");
    }

    private void clearUsers()
            throws Exception
    {
        _server.delete(LdapGroupSchema.userBaseDN);
        _server.add("dn: dc=users,dc=example,dc=org", "objectClass: top", "objectClass: domain",
                "dc: users");
    }

    private byte[] getExternalIDofGroupDN(String dn)
            throws NoSuchAlgorithmException, UnsupportedEncodingException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(dn.getBytes("UTF-8"));
        return md.digest();
    }
}
