/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.sp.authentication.LdapConfiguration.SecurityType;
import com.aerofs.testlib.SimpleSslEngineFactory;
import com.google.common.collect.Lists;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.SearchScope;

import java.util.List;

public abstract class InMemoryServer
{
    /**
     * An in-memory test server that simulates a usual LDAP (unix-style) schema.
     * Add users to taste in individual test cases.
     */
    public static class LdapSchema extends InMemoryServer
    {
        public LdapSchema(boolean useTls, boolean useSsl) throws Exception
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
            add("dn: cn=testgroup,dc=roles,dc=example,dc=org",
                    "objectClass: groupOfUniqueNames", "cn: testgroup");
            add("dn: cn=admins,dc=roles,dc=example,dc=org",
                    "objectClass: groupOfUniqueNames", "cn: admins");
        }


    }

    abstract protected void createSchema(InMemoryDirectoryServer server) throws Exception;

    public void resetConfig(LdapConfiguration cfg)
    {
        cfg.SERVER_HOST = "localhost";
        cfg.SERVER_PORT = _serverPort;
        cfg.SERVER_SECURITY = SecurityType.NONE;
        cfg.SERVER_PRINCIPAL = PRINCIPAL;
        cfg.SERVER_CREDENTIAL = CRED;

        cfg.USER_ADDITIONALFILTER = "";
        cfg.USER_SCOPE = SearchScope.SUB;
        cfg.USER_BASE = "dc=users,dc=example,dc=org";
        cfg.USER_EMAIL = "mail";
        cfg.USER_FIRSTNAME = "givenName";
        cfg.USER_LASTNAME = "sn";
        cfg.USER_OBJECTCLASS = "inetOrgPerson";

        cfg.GROUP_OBJECTCLASSES = Lists.newArrayList("groupOfNames", "groupOfUniqueNames",
                "groupOfEntries", "groupOfURLs", "groupOfUniqueURLs", "posixGroup");
        cfg.GROUP_SCOPE = SearchScope.SUB;
        cfg.GROUP_NAME = "cn";
        cfg.GROUP_BASE = "dc=roles,dc=example,dc=org";
        cfg.GROUP_DYNAMIC_MEMBERS = Lists.newArrayList("memberUrl");
        cfg.GROUP_STATIC_MEMBERS = Lists.newArrayList("member", "uniqueMember");
        cfg.GROUP_UID_MEMBER = "memberUid";

        cfg.COMMON_UID_ATTRIBUTE = "uidNumber";
    }

    protected InMemoryServer(boolean useTls, boolean useSsl) throws Exception
    {
        _useSsl = useSsl;
        _useTls = useTls;
        startServer();
        createSchema(_server);
    }

    private void startServer() throws Exception
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=org");
        InMemoryListenerConfig listenerConfig;

        if (_useTls || _useSsl) {
            SimpleSslEngineFactory simpleSslEngineFactory = new SimpleSslEngineFactory();
            if (_useSsl) {
                listenerConfig = InMemoryListenerConfig.createLDAPSConfig(
                        "localhost", 0, simpleSslEngineFactory.getSSLContext().getServerSocketFactory());
            } else {
                listenerConfig = InMemoryListenerConfig.createLDAPConfig(
                        "localhost", null, 0, simpleSslEngineFactory.getSSLContext().getSocketFactory());
            }
            _cert = simpleSslEngineFactory.getCertificate();
        } else {
            listenerConfig = new InMemoryListenerConfig("test", null, 0, null, null, null);
        }

        config.addAdditionalBindCredentials(PRINCIPAL, CRED);
        config.setListenerConfigs(listenerConfig);
        config.setSchema(null); // do not check (attribute) schema
        config.setAuthenticationRequiredOperationTypes(config.getAllowedOperationTypes());

        _server = new InMemoryDirectoryServer(config);
        _server.startListening();
        _serverPort = _server.getListenPort();
    }

    public String getCertString()       { return _cert; }

    public void add(String... ldifLines) throws Exception { _server.add(ldifLines); }

    public void modify(String DN, Modification mod)
            throws LDAPException
    {
        _server.modify(DN, mod);
    }

    public void modify(String DN, List<Modification> mods)
            throws LDAPException
    {
        _server.modify(DN, mods);
    }

    public void delete(String DN)
            throws LDAPException
    {
        _server.deleteSubtree(DN);
    }

    public void stop()                  { _server.shutDown(true); }

    private InMemoryDirectoryServer     _server;
    protected boolean                   _useSsl;
    protected boolean                   _useTls;
    protected int                       _serverPort;
    static final String                 PRINCIPAL = "cn=Admin,dc=example,dc=org";
    static final String                 CRED = "cred";
    private String                      _cert;
}
