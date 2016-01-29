package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditorFactory;
import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.base.Loggers;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.URLBasedCertificateProvider;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.DoPostDelegate;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.authentication.Authenticator;
import com.aerofs.sp.authentication.AuthenticatorFactory;
import com.aerofs.sp.server.url_sharing.UrlShare;
import com.aerofs.sp.server.url_sharing.UrlSharingDatabase;
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
import com.aerofs.sp.server.settings.token.UserSettingsToken;
import com.aerofs.sp.server.settings.token.UserSettingsTokenDatabase;
import com.aerofs.sp.server.sharing_rules.SharingRulesFactory;
import com.aerofs.ssmp.SSMPConnection;
import org.flywaydb.core.Flyway;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.aerofs.sp.client.SPProtocol.*;
import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;

public class SPServlet extends AeroServlet
{
    private static final Logger l = Loggers.getLogger(SPServlet.class);

    private static final long serialVersionUID = 1L;

    private final PooledSQLConnectionProvider _sqlConProvider;
    private final SQLThreadLocalTransaction _sqlTrans;

    // TODO (WW) remove dependency on these database classes
    private final SPDatabase _db;
    private final OrganizationDatabase _odb;
    private final UserDatabase _udb;
    private final TwoFactorAuthDatabase _tfdb;
    private final DeviceDatabase _ddb;
    private final CertificateDatabase _certdb;
    private final EmailSubscriptionDatabase _esdb;
    private final SharedFolderDatabase _sfdb;
    private final OrganizationInvitationDatabase _oidb;
    private final UrlSharingDatabase _usdb;
    private final UserSettingsTokenDatabase _ustdb;
    private final GroupDatabase _gdb;
    private final GroupMembersDatabase _gmdb;
    private final GroupSharesDatabase _gsdb;

    private final ThreadLocalRequestProvider _requestProvider;
    private final ThreadLocalHttpSessionProvider _sessionProvider;


    private final CertificateGenerator _certgen;
    private final CertificateAuthenticator _certauth;
    private final RequestRemoteAddress _remoteAddress;

    private final Organization.Factory _factOrg;
    private final SharedFolder.Factory _factSharedFolder;
    private final Certificate.Factory _factCert;
    private final Device.Factory _factDevice;
    private final OrganizationInvitation.Factory _factOrgInvite;
    private final License _license;
    private final User.Factory _factUser;
    private final UrlShare.Factory _factUrlShare;
    private final UserSettingsToken.Factory _factUserSettingsToken;
    private final Group.Factory _factGroup;

    private final SPSession _session;

    private final InvitationEmailer.Factory _factEmailer;

    private final AsyncEmailSender _asyncEmailSender;

    private final IdentitySessionManager _identitySessionManager;

    private final Authenticator _authenticator;
    private final PasswordManagement _passwordManagement;
    private final DeviceRegistrationEmailer _deviceRegistrationEmailer;

    private final RequestToSignUpEmailer _requestToSignUpEmailer;
    private final TwoFactorEmailer _twoFactorEmailer;
    private final PooledJedisConnectionProvider _jedisConProvider;
    private final JedisThreadLocalTransaction _jedisTrans;

    private final JedisEpochCommandQueue _commandQueue;

    private final Analytics _analytics;
    private final InvitationReminderEmailer _invitationReminderEmailer;

    private final SharedFolderNotificationEmailer _sfnEmailer;
    private final SharingRulesFactory _sfRules;

    private static final int RATE_LIMITER_BURST_SIZE = 10;
    private static final int RATE_LIMITER_WINDOW = 20000;
    private final JedisRateLimiter _rateLimiter;

    private final ScheduledExecutorService _scheduledExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private final AuditClient _auditClient;
    private final SSMPConnection _ssmpConnection;

    private final SPService _service;

    private final SPServiceReactor _reactor;

    private final DoPostDelegate _postDelegate;
    private final ACLNotificationPublisher _aclNotificationPublisher;

    private final Zelda _zelda;
    private final AccessCodeProvider _accessCodeProvider;

    public SPServlet()
    {
        _sqlConProvider = new PooledSQLConnectionProvider();
        _sqlTrans = new SQLThreadLocalTransaction(_sqlConProvider);
        _db = new SPDatabase(_sqlTrans);
        _odb = new OrganizationDatabase(_sqlTrans);
        _udb = new UserDatabase(_sqlTrans);
        _tfdb = new TwoFactorAuthDatabase(_sqlTrans);
        _ddb = new DeviceDatabase(_sqlTrans);
        _certdb = new CertificateDatabase(_sqlTrans);
        _esdb = new EmailSubscriptionDatabase(_sqlTrans);
        _sfdb = new SharedFolderDatabase(_sqlTrans);
        _oidb = new OrganizationInvitationDatabase(_sqlTrans);
        _usdb = new UrlSharingDatabase(_sqlTrans);
        _ustdb = new UserSettingsTokenDatabase(_sqlTrans);
        _gdb = new GroupDatabase(_sqlTrans);
        _gmdb = new GroupMembersDatabase(_sqlTrans);
        _gsdb = new GroupSharesDatabase(_sqlTrans);
        _requestProvider = new ThreadLocalRequestProvider();
        _sessionProvider = new ThreadLocalHttpSessionProvider();
        _certgen = new CertificateGenerator();
        _certauth = new CertificateAuthenticator(_requestProvider);
        _remoteAddress = new RequestRemoteAddress(_requestProvider);
        _factOrg = new Organization.Factory();
        _factSharedFolder = new SharedFolder.Factory();
        _factCert = new Certificate.Factory(_certdb);
        _factDevice = new Device.Factory();
        _factOrgInvite = new OrganizationInvitation.Factory();
        _license = new License();
        _factUser = new User.Factory();
        _factUrlShare = new UrlShare.Factory(_usdb);
        _factUserSettingsToken = new UserSettingsToken.Factory();
        _factGroup = new Group.Factory();
        _session = new SPSession(_factUser, _sessionProvider);
        _factEmailer = new InvitationEmailer.Factory();
        _asyncEmailSender = AsyncEmailSender.create();
        String deploymentSecret = AeroService.loadDeploymentSecret();
        _ssmpConnection = createSSMPConnection(deploymentSecret, URLBasedCertificateProvider.server());
        _auditClient = createAuditClient(deploymentSecret);
        _aclNotificationPublisher = new ACLNotificationPublisher(_factUser, _ssmpConnection);
        _authenticator = new AuthenticatorFactory(
                _aclNotificationPublisher,
                _auditClient
        ).create();
        _passwordManagement = new PasswordManagement(_db, _factUser, new PasswordResetEmailer(), _authenticator);
        _deviceRegistrationEmailer = new DeviceRegistrationEmailer();
        _requestToSignUpEmailer = new RequestToSignUpEmailer();
        _twoFactorEmailer = new TwoFactorEmailer();
        _jedisConProvider = new PooledJedisConnectionProvider();
        _identitySessionManager = new IdentitySessionManager(_jedisConProvider);
        _jedisTrans = new JedisThreadLocalTransaction(_jedisConProvider);
        _commandQueue = new JedisEpochCommandQueue(_jedisTrans);
        _analytics = new Analytics(new SPAnalyticsProperties(_session));
        _invitationReminderEmailer = new InvitationReminderEmailer();
        _sfnEmailer = new SharedFolderNotificationEmailer();
        _sfRules = new SharingRulesFactory(_authenticator, _factUser, _sfnEmailer);
        _rateLimiter = new JedisRateLimiter(_jedisTrans, RATE_LIMITER_BURST_SIZE, RATE_LIMITER_WINDOW, "rl");

        // Circular dependencies exist between these various factory classes.  Manual injection required.
        {
            _factUser.inject(_udb, _oidb, _tfdb, _gmdb, _factDevice, _factOrg,
                    _factOrgInvite, _factSharedFolder, _factGroup, _license);
            _factDevice.inject(_ddb, _certdb, _certgen, _factUser, _factCert);
            _factOrg.inject(_odb, _oidb, _factUser, _factSharedFolder, _factOrgInvite, _factGroup,
                    _gdb);
            _factOrgInvite.inject(_oidb, _factUser, _factOrg);
            _factSharedFolder.inject(_sfdb, _gsdb, _factGroup, _factUser);
            _factGroup.inject(_gdb, _gmdb, _gsdb, _factOrg, _factSharedFolder, _factUser);
            _factUserSettingsToken.inject(_ustdb);
        }

        try {
            _zelda = Zelda.create("http://sparta.service:8700", "sp", deploymentSecret);
        } catch (MalformedURLException e) {
            // this is impossible. Things have gone horribly wrong if we end up here.
            throw new AssertionError("Malformed bifrost URL: http://sparta.service:8700", e);
        }

        _accessCodeProvider = new AccessCodeProvider(_auditClient, _identitySessionManager);

        _service = new SPService(_db,
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
                _esdb,
                _factSharedFolder,
                _factEmailer,
                _deviceRegistrationEmailer,
                _requestToSignUpEmailer,
                _twoFactorEmailer,
                _commandQueue,
                _analytics,
                _identitySessionManager,
                _authenticator,
                _sfRules,
                _sfnEmailer,
                _asyncEmailSender,
                _factUrlShare,
                _factUserSettingsToken,
                _factGroup,
                _rateLimiter,
                _scheduledExecutor,
                _ssmpConnection,
                _auditClient,
                _aclNotificationPublisher,
                _zelda,
                _accessCodeProvider);
        _reactor = new SPServiceReactor(_service);
        _postDelegate = new DoPostDelegate(SP_POST_PARAM_PROTOCOL, SP_POST_PARAM_DATA);
    }

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        _service.setUserTracker(getUserTracker());
        _service.setSessionInvalidator(getSessionInvalidator());
        _service.setSessionExtender(getSessionExtender());
        migrate();

        _jedisConProvider.init_(REDIS.AOF_ADDRESS.getHostName(), REDIS.AOF_ADDRESS.getPort(), REDIS.PASSWORD);

        InvitationReminder er = new InvitationReminder(_esdb, _sqlTrans, _invitationReminderEmailer);
        er.start();

        _ssmpConnection.start();
    }

    /**
     * Perform any required DB migration (with implicit initialization)
     */
    private void migrate() throws ServletException
    {
        DataSource dataSource = _sqlConProvider.getDataSource();

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setValidateOnMigrate(false);
        flyway.setBaselineOnMigrate(true);
        flyway.setSchemas("aerofs_sp");
        flyway.migrate();
    }

    private static AuditClient createAuditClient(String deploymentSecret)
    {
        AuditClient auditClient = new AuditClient();
        auditClient.setAuditorClient(AuditorFactory.createAuthenticatedWithSharedSecret(
                "auditor.service", "sp", deploymentSecret));
        return auditClient;
    }

    private static SSMPConnection createSSMPConnection(String deploymentSecret,
                                                       ICertificateProvider cacert)
    {
        Executor executor = Executors.newCachedThreadPool();
        return new SSMPConnection(deploymentSecret,
                InetSocketAddress.createUnresolved("lipwig.service", 8787),
                TimerUtil.getGlobalTimer(),
                new NioClientSocketChannelFactory(executor, executor, 1, 2),
                new SSLEngineFactory(Mode.Client, Platform.Desktop, null, cacert, null)
                        ::newSslHandler
                );
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
        if (protocol != SP_PROTOCOL_VERSION) {
            l.warn("protocol version mismatch. servlet: " + SP_PROTOCOL_VERSION + " client: " +
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
        } catch (Throwable e) {
            l.warn("exception in reactor: ", e);
            throw new IOException(e.getCause());
        }

        _postDelegate.sendReply(resp, bytes);
    }

    /**
     * This is used by the sanity checking service.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException
    {
        try {
            if (req.getServletPath().equals("/sp/")) {
                databaseSanityCheck();
            }
            else if (req.getServletPath().equals("/license")) {
                // This endpoint is needed by the web module on first launch to detect whether or
                // not the first user has been created.
                JSONObject json = new JSONObject();
                json.put("users", _service.getSeatCountForPrivateOrg());
                json.put("license", _license.seats());
                rsp.getWriter().print(json.toJSONString());
            }
        } catch (Exception e) {
            l.warn("Database check error: ", e);
            throw new IOException(e);
        }
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
