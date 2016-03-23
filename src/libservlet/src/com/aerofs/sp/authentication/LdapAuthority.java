/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.base.analytics.AnalyticsClient;
import com.aerofs.base.analytics.AnalyticsEvent;
import com.aerofs.base.analytics.IAnalyticsClient;
import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.ids.UserID;
import com.aerofs.lib.FullName;
import com.aerofs.servlets.lib.db.IThreadLocalTransaction;
import com.aerofs.sp.authentication.Authenticator.CredentialFormat;
import com.aerofs.sp.server.ACLNotificationPublisher;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SimpleBindRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

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
 *
 * TODO: This class is only public for the verifier servlet; rework to make this package-private
 */
public class LdapAuthority implements IAuthority
{
    private ACLNotificationPublisher _aclPublisher;

    /**
     * Initialize this authenticator in a limited scope for the LDAP verification servlet
     */
    public LdapAuthority(LdapConfiguration cfg)
    {
        _cfg = cfg;
        _connector = new LdapConnector(_cfg);
        _auditClient = new AuditClient();
        _auditClient.setAuditorClient(AuditorFactory.createNoopClient());
        _analyticsClient = new AnalyticsClient();
    }

    /**
     * Initialize this authenticator with a provisioning strategy.
     */
    public LdapAuthority(LdapConfiguration cfg, ACLNotificationPublisher aclPublisher, AuditClient auditClient,
                         IAnalyticsClient analyticsClient)
    {
        _cfg = cfg;
        _connector = new LdapConnector(_cfg);
        _aclPublisher = aclPublisher;
        _auditClient = auditClient;
        _analyticsClient = analyticsClient;
    }

    @Override
    public String toString() { return "LDAP"; }

    /**
     * Test the LDAP connection.
     */
    public void testConnection() throws ExExternalServiceUnavailable, LDAPException
    {
        // This will throw if:
        //  - the connection is not viable.
        //  - the query parameters are badly mangled. We can't safely
        //    look for any specific user - but we can run a search against the tree and make
        //    sure the LDAP query isn't entirely malformed.
        canAuthenticateThrows(UserID.fromInternal("test@example.com"), true);
    }

    /**
     * Authenticate a user/credential pair by referring to an external LDAP server.
     */
    @Override
    public void authenticateUser(User user, byte[] credential,
            IThreadLocalTransaction<SQLException> trans, CredentialFormat format) throws Exception
    {
        // authenticateUser should only be called by SP/Sparta, not the LDAP Verification servlet
        Preconditions.checkNotNull(_aclPublisher);
        FullName fullName = throwIfBadCredential(user, credential);

        _l.info("LDAP auth ok {}", user.id());
        trans.begin();
        // Save the user record in the database with an empty password (which cannot be used to sign
        // in)
        if (!user.exists()) {
            _auditClient.event(AuditTopic.USER, "user.org.provision")
                    .add("user", user.id())
                    .add("authority", this)
                    .publish();
            user.save(new byte[0], fullName);

            _analyticsClient.track(AnalyticsEvent.USER_SIGNUP);

            // notify TS of user creation (for root store auto-join)
            _aclPublisher.publish_(user.getOrganization().id().toTeamServerUserID());
        }

        trans.commit();
    }

    @Override
    public boolean managesLocalCredential() { return false; }

    @Override
    public boolean isInternalUser(UserID userID) throws ExExternalServiceUnavailable
    {
        return canAuthenticate(userID);
    }

    /**
     * Determine if the given user can be handled by this authenticator.
     * @throws ExExternalServiceUnavailable The external authority cannot be reached
     */
    @Override
    public boolean canAuthenticate(UserID userID) throws ExExternalServiceUnavailable
    {
        try {
            return canAuthenticateThrows(userID, false);
        } catch (LDAPException e) {
            _l.warn("LDAP search exception", BaseLogUtil.suppress(e));
            return false;
        }
    }

    private boolean canAuthenticateThrows(UserID userID, boolean useExtraFilter)
            throws ExExternalServiceUnavailable, LDAPException
    {
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);
        try {
            return conn.search(
                    _cfg.USER_BASE, _cfg.USER_SCOPE, buildFilter(userID, useExtraFilter), _cfg.USER_EMAIL)
                    .getEntryCount() > 0;
        } finally {
            _connector.getPool().releaseAndReAuthenticateConnection(conn);
        }
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
        LDAPConnectionPool pool = _connector.getPool();
        LDAPConnection conn = _connector.getConnectionFromPool(pool);

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
                    _l.info("LDAP bind error", BaseLogUtil.suppress(lde));
                }
            }

            // none of those entries could be bound with the provided credential;
            // therefore this credential is bad.
            throw new ExBadCredential("Cannot sign in to LDAP server with the given credential");
        } finally {
            _connector.getPool().releaseAndReAuthenticateConnection(conn);
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
            return conn.search(_cfg.USER_BASE, _cfg.USER_SCOPE,
                    buildFilter(userId, true),
                    _cfg.USER_FIRSTNAME,
                    _cfg.USER_LASTNAME);
        } catch (LDAPException lde) {
            _l.error("Error searching on LDAP server", lde);
            pool.releaseDefunctConnection(conn);
            throw new ExExternalServiceUnavailable("Error contacting the LDAP server for authentication");
        }
    }


    /**
     * Build an LDAP filter for the user email, object class, and an optional additional filter.
     *
     * @param userId The UserId to search for.
     * @param useAdditionalFilter if true, cfg.USER_ADDITIONALFILTER is taken into account.
     * @throws LDAPException Query error (fragment is malformed or otherwise invalid)
     */
    private Filter buildFilter(UserID userId, boolean useAdditionalFilter) throws LDAPException
    {
        List<Filter> filterList = Lists.newLinkedList();

        filterList.add(Filter.createEqualityFilter(_cfg.USER_EMAIL, userId.getString()));
        filterList.add(Filter.createEqualityFilter("objectClass", _cfg.USER_OBJECTCLASS));
        if (useAdditionalFilter && !Strings.isNullOrEmpty(_cfg.USER_ADDITIONALFILTER)) {
            filterList.add(Filter.create(_cfg.USER_ADDITIONALFILTER));
        }

        return Filter.createANDFilter(filterList);
    }

    private static Logger               _l = LoggerFactory.getLogger(LdapAuthority.class);
    protected LdapConfiguration         _cfg;
    private AuditClient                 _auditClient;
    private LdapConnector               _connector;
    private IAnalyticsClient             _analyticsClient;
}
