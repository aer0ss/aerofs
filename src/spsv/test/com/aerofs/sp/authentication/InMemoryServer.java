/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.sp.authentication.LdapConfiguration.SecurityType;
import com.aerofs.testlib.SimpleSslEngineFactory;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;

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

    void resetConfig(LdapConfiguration cfg)
    {
        cfg.SERVER_HOST = "localhost";
        cfg.SERVER_PORT = _serverPort;
        cfg.SERVER_SECURITY = SecurityType.NONE;
        cfg.SERVER_PRINCIPAL = PRINCIPAL;
        cfg.SERVER_CREDENTIAL = CRED;

        cfg.USER_ADDITIONALFILTER = "";
        cfg.USER_SCOPE = "sub";
        cfg.USER_BASE = "dc=users,dc=example,dc=org";
        cfg.USER_EMAIL = "mail";
        cfg.USER_FIRSTNAME = "givenName";
        cfg.USER_LASTNAME = "sn";
        cfg.USER_OBJECTCLASS = "inetOrgPerson";
        cfg.USER_RDN = "dn";
    }

    InMemoryServer(boolean useTls, boolean useSsl) throws Exception
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

    public void stop()                  { _server.shutDown(true); }

    private InMemoryDirectoryServer     _server;
    protected boolean                   _useSsl;
    protected boolean                   _useTls;
    protected int                       _serverPort;
    static final String                 PRINCIPAL = "cn=Admin,dc=example,dc=org";
    static final String                 CRED = "cred";
    private String                      _cert;
}
