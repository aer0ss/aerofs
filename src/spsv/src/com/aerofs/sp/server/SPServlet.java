package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.lib.Util;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.DoPostDelegate;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.server.URLSharing.UrlShare;
import com.aerofs.sp.server.URLSharing.UrlSharingDatabase;
import com.aerofs.sp.server.email.DeviceRegistrationEmailer;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.email.SharedFolderNotificationEmailer;
import com.aerofs.sp.server.email.TwoFactorEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.group.Group;
import com.aerofs.sp.server.lib.group.GroupDatabase;
import com.aerofs.sp.server.lib.group.GroupMembersDatabase;
import com.aerofs.sp.server.lib.group.GroupSharesDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationDatabase;
import com.aerofs.sp.server.lib.organization.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.sf.SharedFolder;
import com.aerofs.sp.server.lib.sf.SharedFolderDatabase;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.session.RequestRemoteAddress;
import com.aerofs.sp.server.lib.session.ThreadLocalHttpSessionProvider;
import com.aerofs.sp.server.lib.twofactor.TwoFactorAuthDatabase;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSession;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.googlecode.flyway.core.Flyway;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;

import static com.aerofs.sp.server.lib.SPParam.AUDIT_CLIENT_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;
import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_CLIENT_ATTRIBUTE;

public class SPServlet extends AeroServlet
{
    private static final Logger l = Loggers.getLogger(SPServlet.class);

    private static final long serialVersionUID = 1L;

    private final PooledSQLConnectionProvider _sqlConProvider = new PooledSQLConnectionProvider();
    private final SQLThreadLocalTransaction _sqlTrans = new SQLThreadLocalTransaction(_sqlConProvider);

    // TODO (WW) remove dependency on these database classes
    private final SPDatabase _db = new SPDatabase(_sqlTrans);
    private final OrganizationDatabase _odb = new OrganizationDatabase(_sqlTrans);
    private final UserDatabase _udb = new UserDatabase(_sqlTrans);
    private final TwoFactorAuthDatabase _tfdb = new TwoFactorAuthDatabase(_sqlTrans);
    private final DeviceDatabase _ddb = new DeviceDatabase(_sqlTrans);
    private final CertificateDatabase _certdb = new CertificateDatabase(_sqlTrans);
    private final EmailSubscriptionDatabase _esdb = new EmailSubscriptionDatabase(_sqlTrans);
    private final SharedFolderDatabase _sfdb = new SharedFolderDatabase(_sqlTrans);
    private final OrganizationInvitationDatabase _oidb = new OrganizationInvitationDatabase(_sqlTrans);
    private final UrlSharingDatabase _usdb = new UrlSharingDatabase(_sqlTrans);
    private final GroupDatabase _gdb = new GroupDatabase(_sqlTrans);
    private final GroupMembersDatabase _gmdb = new GroupMembersDatabase(_sqlTrans);
    private final GroupSharesDatabase _gsdb = new GroupSharesDatabase(_sqlTrans);

    private final ThreadLocalRequestProvider _requestProvider =
            new ThreadLocalRequestProvider();
    private final ThreadLocalHttpSessionProvider _sessionProvider =
            new ThreadLocalHttpSessionProvider();


    private final CertificateGenerator _certgen = new CertificateGenerator();
    private final CertificateAuthenticator _certauth =
            new CertificateAuthenticator(_requestProvider);
    private final RequestRemoteAddress _remoteAddress = new RequestRemoteAddress(_requestProvider);

    private final Organization.Factory _factOrg = new Organization.Factory();
    private final SharedFolder.Factory _factSharedFolder = new SharedFolder.Factory();
    private final Certificate.Factory _factCert = new Certificate.Factory(_certdb);
    private final Device.Factory _factDevice = new Device.Factory();
    private final OrganizationInvitation.Factory _factOrgInvite =
            new OrganizationInvitation.Factory();
    private final License _license = new License();
    private final User.Factory _factUser = new User.Factory();
    private final UrlShare.Factory _factUrlShare = new UrlShare.Factory(_usdb);
    private final Group.Factory _factGroup = new Group.Factory();

    private final SPSession _session = new SPSession(_factUser, _sessionProvider);

    private final InvitationEmailer.Factory _factEmailer = new InvitationEmailer.Factory();

    private final AsyncEmailSender _asyncEmailSender = AsyncEmailSender.create();

    private final Authenticator _authenticator = AuthenticatorFactory.create();
    private final PasswordManagement _passwordManagement =
            new PasswordManagement(_db, _factUser, new PasswordResetEmailer(), _authenticator);
    private final DeviceRegistrationEmailer _deviceRegistrationEmailer = new DeviceRegistrationEmailer();

    private final RequestToSignUpEmailer _requestToSignUpEmailer = new RequestToSignUpEmailer();
    private final TwoFactorEmailer _twoFactorEmailer = new TwoFactorEmailer();
    private final PooledJedisConnectionProvider _jedisConProvider =
            new PooledJedisConnectionProvider();
    private final JedisThreadLocalTransaction _jedisTrans =
            new JedisThreadLocalTransaction(_jedisConProvider);

    private final JedisEpochCommandQueue _commandQueue = new JedisEpochCommandQueue(_jedisTrans);

    private final Analytics _analytics =
            new Analytics(new SPAnalyticsProperties(_session));
    private final InvitationReminderEmailer _invitationReminderEmailer = new InvitationReminderEmailer();

    private final SharedFolderNotificationEmailer _sfnEmailer = new SharedFolderNotificationEmailer();
    private final SharingRulesFactory _sfRules =
            new SharingRulesFactory(_authenticator, _factUser, _sfnEmailer);

    private static final int RATE_LIMITER_BURST_SIZE = 10;
    private static final int RATE_LIMITER_WINDOW = 20000;
    private final JedisRateLimiter _rateLimiter =
             new JedisRateLimiter(_jedisTrans, RATE_LIMITER_BURST_SIZE, RATE_LIMITER_WINDOW, "rl");

    {
        _factUser.inject(_udb, _oidb, _tfdb, _factDevice, _factOrg,
                _factOrgInvite, _factSharedFolder, _license);
        _factDevice.inject(_ddb, _certdb, _certgen, _factUser, _factCert);
        _factOrg.inject(_odb, _oidb, _factUser, _factSharedFolder, _factOrgInvite, _factGroup,
                _gdb);
        _factOrgInvite.inject(_oidb, _factUser, _factOrg);
        _factSharedFolder.inject(_sfdb, _gsdb, _factGroup, _factUser);
        _factGroup.inject(_gdb, _gmdb, _gsdb, _factOrg, _factSharedFolder, _factUser);
    }

    private final SPService _service = new SPService(_db,
            _sqlTrans,
            _jedisTrans,
            _session,
            _passwordManagement,
            _certauth,
            _remoteAddress,
            _factUser,
            _factOrg,
            _factOrgInvite,
            _factDevice,
            _certdb,
            _esdb,
            _factSharedFolder,
            _factEmailer,
            _deviceRegistrationEmailer,
            _requestToSignUpEmailer,
            _twoFactorEmailer,
            _commandQueue,
            _analytics,
            new IdentitySessionManager(),
            _authenticator,
            _sfRules,
            _sfnEmailer,
            _asyncEmailSender,
            _factUrlShare,
            _factGroup,
            _rateLimiter,
            _license);

    private final SPServiceReactor _reactor = new SPServiceReactor(_service);

    private final DoPostDelegate _postDelegate = new DoPostDelegate(SP.SP_POST_PARAM_PROTOCOL,
            SP.SP_POST_PARAM_DATA);


    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        _authenticator.setACLPublisher_(new ACLNotificationPublisher(_factUser, getVerkehrClient()));

        _service.setNotificationClients(getVerkehrClient());
        _service.setAuditorClient_(getAuditClient());

        _service.setUserTracker(getUserTracker());
        _service.setSessionInvalidator(getSessionInvalidator());
        _service.setSessionExtender(getSessionExtender());

        String dbResourceName =
                getServletContext().getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);

        _sqlConProvider.init_(dbResourceName);
        migrate();

        String redisHost = REDIS.AOF_ADDRESS.getHostName();
        int redisPort = REDIS.AOF_ADDRESS.getPort();
        _jedisConProvider.init_(redisHost, (short) redisPort);

        PooledSQLConnectionProvider erConProvider = new PooledSQLConnectionProvider();
        erConProvider.init_(dbResourceName);

        InvitationReminder er = new InvitationReminder(_esdb, _sqlTrans, _invitationReminderEmailer);
        er.start();
    }

    /**
     * Perform any required DB migration (with implicit initialization)
     */
    private void migrate() throws ServletException
    {
        DataSource dataSource;
        try {
            dataSource = _sqlConProvider.getDataSource();
        } catch (ExDbInternal e) {
            throw new ServletException(e);
        }

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setInitOnMigrate(true);
        flyway.setSchemas("aerofs_sp");
        flyway.migrate();
    }

    private VerkehrClient getVerkehrClient()
    {
        return (VerkehrClient) getServletContext().getAttribute(VERKEHR_CLIENT_ATTRIBUTE);
    }

    private AuditClient getAuditClient()
    {
        return (AuditClient) getServletContext().getAttribute(AUDIT_CLIENT_ATTRIBUTE);
    }

    private SPActiveUserSessionTracker getUserTracker()
    {
        return (SPActiveUserSessionTracker) getServletContext().getAttribute(SESSION_USER_TRACKER);
    }

    private SPSessionInvalidator getSessionInvalidator()
    {
        return (SPSessionInvalidator) getServletContext().getAttribute(SESSION_INVALIDATOR);
    }

    private SPSessionExtender getSessionExtender()
    {
        return (SPSessionExtender) getServletContext().getAttribute(SESSION_EXTENDER);
    }

    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
        throws IOException
    {
        // We disable SP rather than shutting down the entire Tomcat process to keep other servlets
        // (e.g. SMTP/LDAP verification servlets) running, which is required by the Site Setup to
        // complete the setup without the need of restarting SP after the user inputs a valid license.
        // See also docs/design/licensing.md
        //
        // Also, do not block doGet() so server status still works after license expires.
        //
        if (!_license.isValid()) throw new IOException("the license is invalid");

        // Set up thread-local proxy objects
        _requestProvider.set(req);
        _sessionProvider.setSession(req.getSession());

        // Receive protocol version number.
        int protocol;
        try {
            protocol = _postDelegate.getProtocolVersion(req);
        } catch (NumberFormatException e) {
            l.warn("no valid protocol version found");
            resp.sendError(400, "invalid request");
            return;
        }
        if (protocol != SP.SP_PROTOCOL_VERSION) {
            l.warn("protocol version mismatch. servlet: " + SP.SP_PROTOCOL_VERSION + " client: " +
                    protocol);
            resp.sendError(400, "prototcol version mismatch");
            return;
        }

        byte[] decodedMessage = _postDelegate.getDecodedMessage(req);

        // Process call
        byte [] bytes;
        try {
            bytes = _reactor.react(decodedMessage).get();
            _sqlTrans.cleanUp();
            _jedisTrans.cleanUp();
        } catch (Exception e) {
            l.warn("exception in reactor: " + Util.e(e));
            throw new IOException(e.getCause());
        }

        _postDelegate.sendReply(resp, bytes);
    }

    /**
     * This used by various server sanity checking probes (pagerduty in public deployment and the
     * sanity checker service in private deployment).
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException
    {
        try {
            if (req.getServletPath().equals("/license")) {
                licenseCheck(rsp);
            } else {
                // Ok, wonky but for backwards compatibility: we do the database sanity check for
                // any servlet request to /sp/*.
                // TODO: reconsider this; only do if path.equals("/")
                databaseSanityCheck();
            }
        } catch (Exception e) {
            l.warn("Database check error: " + Util.e(e));
            throw new IOException(e);
        }
    }

    private void licenseCheck(HttpServletResponse rsp) throws Exception
    {
        // Haha, this is amazing. Such love.
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"users\":");
        sb.append(_service.getSeatCountForPrivateOrg());
        sb.append(",");
        sb.append("\"seats\":");
        sb.append(_license.seats());
        sb.append("}");
        rsp.getWriter().print(sb.toString());
    }

    private void databaseSanityCheck() throws Exception
    {
        final int mysqlDatabaseCheckTimeoutSeconds = 1;
        // Check the mysql connection.
        _sqlTrans.begin();
        _sqlTrans.getConnection().isValid(mysqlDatabaseCheckTimeoutSeconds);
        _sqlTrans.commit();

        // Check the Redis connection. Do this by getting any random key.
        _jedisTrans.begin();
        _jedisTrans.get().get("");
        _jedisTrans.commit();
    }

}
