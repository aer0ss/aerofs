/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.C;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.lib.FullName;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.server.lib.user.User;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;

/**
 * Attempt to authenticate principal/credential pairs against an LDAP server.
 *
 * The behavior is as follows:
 *  - establish a connection pool to the LDAP server
 *  - bind to the LDAP server with a generic credential (usually one set aside for this purpose)
 *  - query a subtree within the LDAP server for users with the given email address
 *  - for each of the returned user records, attempt to bind with the DN of the record using
 *  the credential (cleartext password) passed in from the client.
 *  - if one matches, return a simple user record.
 *  - if no bind is successful, throw ExBadCredential.
 *
 *  Notes:
 *      - LDAP errors should all be wrapped in ExBadCredential or ExServiceError rather than
 *      letting callers know about internals of the LDAP library used here.
 */
public class LdapAuthenticator implements IAuthenticator
{
    /**
     * Initialize this authenticator with a provisioning strategy.
     */
    public LdapAuthenticator(LdapConfiguration cfg, IProvisioningStrategy provisioner)
    {
        _provisioner = provisioner;
        _cfg = cfg;
    }

    /**
     * Authenticate a user/credential pair by referring to an external LDAP server.
     */
    @Override
    public void authenticateUser(
            User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans) throws Exception
    {
        FullName fullName = throwIfBadCredential(user, credential);

        trans.begin();
        if (!user.exists())
            _provisioner.saveUser(user, fullName, credential);
        trans.commit();
    }

    /**
     * Attempt to bind to the LDAP server using the given userid and credential.
     * If the connection cannot be set up (bad credential etc.) an exception will be
     * thrown; otherwise assume the principal/cred pair is okay.
     *
     * LDAP exceptions are mapped to either ExBadCredential or ExServiceError.
     */
    protected FullName throwIfBadCredential(User user, byte[] credential)
            throws ExExternalServiceUnavailable, ExBadCredential, SQLException
    {
        LDAPConnectionPool pool = getPool();
        LDAPConnection conn = getConnectionFromPool(pool);

        SearchResult results = findRecords(pool, conn, user.id());
        try {
            // It is possible for even well-configured LDAP server to have multiple entries for
            // a user. We try the DN of each returned result until we find a match.
            // Yes it's stupid; yes it's the normal way to do it in LDAP land.
            //
            for (SearchResultEntry entry : results.getSearchEntries()) {
                try {
                    conn.bind(new SimpleBindRequest(entry.getDN(), credential));
                    // ... bind throws if the credential is not accepted ...
                    return new FullName(
                            entry.getAttributeValue(_cfg.USER_FIRSTNAME),
                            entry.getAttributeValue(_cfg.USER_LASTNAME));
                } catch (LDAPException lde) {
                    // this just means that this record was not the login record.
                    _l.debug("LDAP bind error", lde);
                    continue;
                }
            }

            // none of those entries could be bound with the provided credential;
            // therefore this credential is bad.
            throw new ExBadCredential("Cannot sign in to LDAP server with the given credential");
        } finally {
            getPool().releaseAndReAuthenticateConnection(conn);
        }
    }

    /**
     * Search the LDAP server for records matching the given username.
     */
    private SearchResult findRecords(LDAPConnectionPool pool, LDAPConnection conn, UserID userId)
            throws ExExternalServiceUnavailable
    {
        try {
            //
            // NOTE: The parameters here are read as follows:
            //
            // search starting with USER_BASE as the root (using "this._scope")
            //   for objects that match "buildFilter(userId)",
            //   and returning for each object the following fields:
            //     USER_FIRSTNAME, USER_LASTNAME
            return conn.search(
                    _cfg.USER_BASE, _scope,
                    buildFilter(userId),
                    _cfg.USER_FIRSTNAME, _cfg.USER_LASTNAME);
        } catch (LDAPException lde) {
            _l.error("Error searching on LDAP server", lde);
            pool.releaseDefunctConnection(conn);
            throw new ExExternalServiceUnavailable("Error contacting the LDAP server for authentication");
        }
    }

    /**
     * Get a Connection object from the LDAP Connection pool; translate any LDAP exception into
     * an ExExternalServiceError.
     * @throws com.aerofs.base.ex.ExExternalServiceUnavailable The external LDAP server is missing or misconfigured.
     */
    private LDAPConnection getConnectionFromPool(LDAPConnectionPool pool) throws
            ExExternalServiceUnavailable
    {
        try {
            return pool.getConnection();
        } catch (LDAPException e) {
            _l.error("Cannot get connection to LDAP server", e);
            throw new ExExternalServiceUnavailable("Cannot connect to LDAP server");
        }
    }

    private Filter buildFilter(UserID userId)
    {
        Filter filter = Filter.createANDFilter(
                Filter.createEqualityFilter(_cfg.USER_EMAIL, userId.getString()),
                Filter.createEqualityFilter("objectClass", _cfg.USER_OBJECTCLASS));
        return filter;
    }


    /*
     * WARNING: Be careful here! This is easy to get wrong.
     *
     * This is a double-checked lock to lazy-init; doing this in a static initializer
     * is problematic because class-load and property-initializing ordering becomes important.
     *
     *  The inner method (initPool) is set aside to avoid requiring monitor synchronization
     *  in the normal case, i.e. the pool is already initialized. Once the pool is set up,
     *  initPool will never again be called and we will not require the monitor.
     *
     * For this style of double-checked lock, the 'volatile' keyword is important and should
     * not be removed.
     * See:
     *       "Effective Java, Second Edition"
     *       https://www.securecoding.cert.org/confluence/display/java/LCK10-J.+Do+not+use+incorrect+forms+of+the+double-checked+locking+idiom
     *
     */
    private LDAPConnectionPool getPool() throws ExExternalServiceUnavailable
    {
        LDAPConnectionPool result = _pool;
        if (result == null) result = initPool();
        return result;
    }

    /**
     * WARNING: this continues the subtle stuff from getPool(). Easy to break this.
     *
     * When we get here, we are guaranteed to have the monitor on 'this' which prevents other
     * threads from reading/writing the pool field.
     *
     * We also know that at some time in the past, the _pool field was null.
     *
     * At this point we need to recheck whether _pool is still null. If it's initialized, then we do
     * no work and return the current value.
     *
     */
    private synchronized LDAPConnectionPool initPool() throws ExExternalServiceUnavailable
    {
        if (_pool == null) {
            try {
                LDAPConnectionOptions options = new LDAPConnectionOptions();

                options.setConnectTimeoutMillis((int)(_cfg.SERVER_TIMEOUT_CONNECT * C.SEC));
                options.setResponseTimeoutMillis(_cfg.SERVER_TIMEOUT_READ * C.SEC);

                switch (_cfg.SERVER_SECURITY) {
                case NONE:
                    _l.warn("Using insecure external LDAP configuration, no SSL or TLS");
                    _pool = connectPool();
                    break;
                case SSL:
                    _l.info("Configured LDAP connection for SSL");
                    _pool = connectSSLPool(getSSLEngineFactory());
                    break;
                case STARTTLS:
                    _l.info("Configured LDAP connection for StartTLS");
                    _pool = connectTLSPool(getSSLEngineFactory());
                    break;
                default: assert false : "SecurityType maintenance error";
                }

            } catch (LDAPException lde) {
                _l.warn("LDAP connection error", lde);
                throw new ExExternalServiceUnavailable("LDAP connection error");
            } catch (GeneralSecurityException e) {
                _l.warn("LDAP security error", e);
                throw new ExExternalServiceUnavailable("LDAP security error");
            } catch (IOException e) {
                _l.warn("LDAP cert reading error", e);
                throw new ExLdapConfigurationError();
            }

            cacheUserScope();
        }
        return _pool;
    }

    /**
     * Return an SSL engine factory used to create secure contexts. The factory will be configured
     * with an explicit certificate if one is set in LDAP.SERVER_CA_CERT, otherwise the default
     * trust store is used.
     */
    private SSLEngineFactory getSSLEngineFactory()
    {
        return new SSLEngineFactory(
                Mode.Client, Platform.Desktop, null,
                (_cfg.SERVER_CA_CERT.length() > 0) ?
                        new StringBasedCertificateProvider(_cfg.SERVER_CA_CERT) : null,
                null);
    }

    private LDAPConnectionPool connectPool() throws LDAPException
    {
        LDAPConnection conn = new LDAPConnection(
                _cfg.SERVER_HOST, _cfg.SERVER_PORT,
                _cfg.SERVER_PRINCIPAL, _cfg.SERVER_CREDENTIAL);
        return new LDAPConnectionPool(conn, 1, _cfg.SERVER_MAXCONN);
    }

    private LDAPConnectionPool connectTLSPool(SSLEngineFactory factory)
            throws GeneralSecurityException, LDAPException, IOException
    {
        LDAPConnection conn = new LDAPConnection(_cfg.SERVER_HOST, _cfg.SERVER_PORT);

        conn.processExtendedOperation(new StartTLSExtendedRequest(factory.getSSLContext()));

        conn.bind(_cfg.SERVER_PRINCIPAL, _cfg.SERVER_CREDENTIAL);

        return new LDAPConnectionPool(conn, 1, _cfg.SERVER_MAXCONN,
                new StartTLSPostConnectProcessor(factory.getSSLContext()));
    }

    private LDAPConnectionPool connectSSLPool(SSLEngineFactory factory)
            throws IOException, GeneralSecurityException, LDAPException
    {
        LDAPConnection conn = new LDAPConnection(
                factory.getSSLContext().getSocketFactory(),
                _cfg.SERVER_HOST, _cfg.SERVER_PORT,
                _cfg.SERVER_PRINCIPAL, _cfg.SERVER_CREDENTIAL);

        return new LDAPConnectionPool(conn, 1, _cfg.SERVER_MAXCONN);
    }

    /**
     * Cache any calculated values that come from the LibParam configuration, and which will
     * not change for the life of this pool.
     */
    private void cacheUserScope()
    {
        if (_cfg.USER_SCOPE.equals("base")) {
            _scope = SearchScope.BASE;
        } else if (_cfg.USER_SCOPE.equals("one")) {
            _scope = SearchScope.ONE;
        } else { // default...
            _scope = SearchScope.SUB;
        }
    }

    volatile private LDAPConnectionPool _pool = null;

    private SearchScope                 _scope = SearchScope.SUB;
    private LdapConfiguration _cfg;
    private final IProvisioningStrategy _provisioner;
    private static Logger               _l = LoggerFactory.getLogger(LdapAuthenticator.class);
}
