/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.lib.LibParam.LDAP;
import com.aerofs.lib.LibParam.LDAP.Schema;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

import static org.junit.Assert.assertEquals;

public abstract class InMemoryServer
{
    /**
     * An in-memory test server that simulates a usual LDAP (unix-style) schema.
     * Add users to taste in individual test cases.
     */
    public static class LdapSchema extends InMemoryServer
    {
        public LdapSchema(boolean useTls, boolean useSsl) throws Exception { super(useTls, useSsl); }

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

        @Override
        public void resetConfig()
        {
            LDAP.SERVER_HOST = "localhost";
            LDAP.SERVER_PORT = _serverPort;
            LDAP.SERVER_USETLS = _useTls;
            LDAP.SERVER_USESSL = _useSsl;
            LDAP.SERVER_PRINCIPAL = PRINCIPAL;
            LDAP.SERVER_CREDENTIAL = CRED;

            Schema.USER_SCOPE = "sub";
            Schema.USER_BASE = "dc=users,dc=example,dc=org";
            Schema.USER_EMAIL = "mail";
            Schema.USER_FIRSTNAME = "givenName";
            Schema.USER_LASTNAME = "sn";
            Schema.USER_OBJECTCLASS = "inetOrgPerson";
            Schema.USER_RDN = "dn";
        }
    }

    abstract protected void createSchema(InMemoryDirectoryServer server) throws Exception;
    abstract public void resetConfig();

    InMemoryServer(boolean useTls, boolean useSsl) throws Exception
    {
        _useSsl = useSsl;
        _useTls = useTls;
        startServer();
        createSchema(_server);
        resetConfig();
        sanityCheck();
    }

    private void startServer() throws LDAPException
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=org");
        InMemoryListenerConfig listenerConfig = new InMemoryListenerConfig("test", null, 0, null, null, null); // FIXME: StartTLS

        config.addAdditionalBindCredentials(PRINCIPAL, CRED);

        config.setListenerConfigs(listenerConfig);
        config.setSchema(null); // do not check (attribute) schema
        _server = new InMemoryDirectoryServer(config);
        _server.startListening();
        _serverPort = _server.getListenPort();
    }

    public void stop()
    {
        _server.shutDown(true);
    }

    public void add(String... ldifLines) throws Exception { _server.add(ldifLines); }

    private void sanityCheck() throws LDAPException
    {
        LDAPConnection conn = new LDAPConnection("localhost", _serverPort);
        BindResult bind = conn.bind(PRINCIPAL, CRED);
        assertEquals(bind.getResultCode(), ResultCode.SUCCESS);
    }

    private InMemoryDirectoryServer     _server;

    protected boolean                   _useSsl;
    protected boolean                   _useTls;
    protected int                       _serverPort;

    static final String                 PRINCIPAL = "cn=Admin,dc=example,dc=org";
    static final String                 CRED = "cred";
}
