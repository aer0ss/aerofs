/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.C;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.lib.log.LogUtil;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.StartTLSPostConnectProcessor;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class LdapConnector
{

    public LdapConnector(LdapConfiguration cfg) {
        _cfg = cfg;
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
    public LDAPConnectionPool getPool() throws ExExternalServiceUnavailable
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
                    _pool = connectPool(options);
                    break;
                case SSL:
                    _l.info("Configured LDAP connection for SSL");
                    _pool = connectSSLPool(getSSLEngineFactory(), options);
                    break;
                case STARTTLS:
                    _l.info("Configured LDAP connection for StartTLS");
                    _pool = connectTLSPool(getSSLEngineFactory(), options);
                    break;
                default: assert false : "SecurityType maintenance error";
                }

            } catch (LDAPException lde) {
                // N.B. the exceptions thrown below need to have helpful error messages, since these
                // error messages are passed right back up to the site configuration UI. Therefore
                // please do not remove the getMessage() calls!
                _l.warn("LDAP connection error", lde);
                throw new ExExternalServiceUnavailable(lde.getMessage());
            } catch (GeneralSecurityException e) {
                _l.warn("LDAP security error", e);
                throw new ExExternalServiceUnavailable(e.getMessage());
            } catch (IOException e) {
                _l.warn("LDAP cert reading error", e);
                throw new ExLdapConfigurationError(e.getMessage());
            }
        }
        return _pool;
    }

    /**
     * Get a Connection object from the LDAP Connection pool; translate any LDAP exception into
     * an ExExternalServiceError.
     * @throws com.aerofs.base.ex.ExExternalServiceUnavailable The external LDAP server is missing or misconfigured.
     */
    public LDAPConnection getConnectionFromPool(LDAPConnectionPool pool) throws
            ExExternalServiceUnavailable
    {
        try {
            return pool.getConnection();
        } catch (LDAPException e) {
            _l.error("Cannot get connection to LDAP server", LogUtil.suppress(e));
            throw new ExExternalServiceUnavailable("Cannot connect to LDAP server");
        }
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

    private LDAPConnectionPool connectPool(LDAPConnectionOptions options) throws LDAPException
    {
        LDAPConnection conn = new LDAPConnection(options,
                _cfg.SERVER_HOST, _cfg.SERVER_PORT,
                _cfg.SERVER_PRINCIPAL, _cfg.SERVER_CREDENTIAL);

        return new LDAPConnectionPool(conn, 1, _cfg.SERVER_MAXCONN);
    }

    private LDAPConnectionPool connectTLSPool(SSLEngineFactory factory,
            LDAPConnectionOptions options)
            throws GeneralSecurityException, LDAPException, IOException
    {
        LDAPConnection conn = new LDAPConnection(options, _cfg.SERVER_HOST, _cfg.SERVER_PORT);

        conn.processExtendedOperation(new StartTLSExtendedRequest(factory.getSSLContext()));

        conn.bind(_cfg.SERVER_PRINCIPAL, _cfg.SERVER_CREDENTIAL);

        return new LDAPConnectionPool(conn, 1, _cfg.SERVER_MAXCONN,
                new StartTLSPostConnectProcessor(factory.getSSLContext()));
    }

    private LDAPConnectionPool connectSSLPool(SSLEngineFactory factory,
            LDAPConnectionOptions options)
            throws IOException, GeneralSecurityException, LDAPException
    {
        LDAPConnection conn = new LDAPConnection(
                factory.getSSLContext().getSocketFactory(),
                options,
                _cfg.SERVER_HOST, _cfg.SERVER_PORT,
                _cfg.SERVER_PRINCIPAL, _cfg.SERVER_CREDENTIAL);

        return new LDAPConnectionPool(conn, 1, _cfg.SERVER_MAXCONN);
    }

    private static Logger _l = LoggerFactory.getLogger(LdapAuthority.class);
    private LdapConfiguration _cfg;
    private LDAPConnectionPool _pool;
}
