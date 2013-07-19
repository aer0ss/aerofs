package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.Analytics;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.lib.Util;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.servlets.lib.ssl.CertificateAuthenticator;
import com.aerofs.sp.server.email.DeviceRegistrationEmailer;
import com.aerofs.sp.server.email.RequestToSignUpEmailer;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.session.HttpSessionUser;
import com.aerofs.sp.server.lib.OrganizationInvitationDatabase;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.session.ThreadLocalHttpSessionProvider;
import com.aerofs.sp.server.lib.cert.Certificate;
import com.aerofs.sp.server.lib.cert.CertificateDatabase;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.sp.server.email.InvitationReminderEmailer.Factory;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.lib.cert.CertificateGenerator;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.server.lib.device.DeviceDatabase;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.organization.OrganizationInvitation;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.email.EmailReminder;
import com.aerofs.servlets.lib.DoPostDelegate;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.session.SPActiveUserSessionTracker;
import com.aerofs.sp.server.session.SPSessionExtender;
import com.aerofs.sp.server.session.SPSessionInvalidator;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.aerofs.sp.server.lib.SPParam.SESSION_EXTENDER;
import static com.aerofs.sp.server.lib.SPParam.SESSION_INVALIDATOR;
import static com.aerofs.sp.server.lib.SPParam.SESSION_USER_TRACKER;
import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;

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
    private final DeviceDatabase _ddb = new DeviceDatabase(_sqlTrans);
    private final CertificateDatabase _certdb = new CertificateDatabase(_sqlTrans);
    private final EmailSubscriptionDatabase _esdb = new EmailSubscriptionDatabase(_sqlTrans);
    private final SharedFolderDatabase _sfdb = new SharedFolderDatabase(_sqlTrans);
    private final OrganizationInvitationDatabase _oidb = new OrganizationInvitationDatabase(_sqlTrans);

    private final ThreadLocalHttpSessionProvider _sessionProvider =
            new ThreadLocalHttpSessionProvider();
    private final HttpSessionUser _sessionUser = new HttpSessionUser(_sessionProvider);

    private final CertificateGenerator _certgen = new CertificateGenerator();
    private final CertificateAuthenticator _certauth =
            new CertificateAuthenticator(_sessionProvider);

    private final Organization.Factory _factOrg = new Organization.Factory();
    private final SharedFolder.Factory _factSharedFolder = new SharedFolder.Factory();
    private final Certificate.Factory _factCert = new Certificate.Factory(_certdb);
    private final Device.Factory _factDevice = new Device.Factory();
    private final OrganizationInvitation.Factory _factOrgInvite =
            new OrganizationInvitation.Factory();
    private final User.Factory _factUser = new User.Factory(_udb, _oidb, _factDevice, _factOrg,
            _factOrgInvite, _factSharedFolder);
    {
        _factDevice.inject(_ddb, _certdb, _certgen, _factUser, _factCert);
        _factOrg.inject(_odb, _oidb, _factUser, _factSharedFolder, _factOrgInvite);
        _factOrgInvite.inject(_oidb, _factUser, _factOrg);
        _factSharedFolder.inject(_sfdb, _factUser);
    }

    private final InvitationEmailer.Factory _factEmailer = new InvitationEmailer.Factory();

    private final PasswordManagement _passwordManagement =
            new PasswordManagement(_db, _factUser, new PasswordResetEmailer());
    private final DeviceRegistrationEmailer _deviceRegistrationEmailer = new DeviceRegistrationEmailer();
    private final RequestToSignUpEmailer _requestToSignUpEmailer = new RequestToSignUpEmailer();

    private final PooledJedisConnectionProvider _jedisConProvider =
            new PooledJedisConnectionProvider();
    private final JedisThreadLocalTransaction _jedisTrans =
            new JedisThreadLocalTransaction(_jedisConProvider);
    private final JedisEpochCommandQueue _commandQueue = new JedisEpochCommandQueue(_jedisTrans);

    private final Analytics _analytics = new Analytics(new SPAnalyticsProperties(_sessionUser));

    private final SPService _service = new SPService(_db, _sqlTrans, _jedisTrans, _sessionUser,
            _passwordManagement, _certauth, _factUser, _factOrg, _factOrgInvite, _factDevice,
            _certdb, _esdb, _factSharedFolder, _factEmailer, _deviceRegistrationEmailer,
            _requestToSignUpEmailer, _commandQueue, _analytics);
    private final SPServiceReactor _reactor = new SPServiceReactor(_service);

    private final DoPostDelegate _postDelegate = new DoPostDelegate(SP.SP_POST_PARAM_PROTOCOL,
            SP.SP_POST_PARAM_DATA);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        _service.setVerkehrClients_(getVerkehrPublisher(), getVerkehrAdmin());

        _service.setUserTracker(getUserTracker());
        _service.setSessionInvalidator(getSessionInvalidator());
        _service.setSessionExtender(getSessionExtender());

        String dbResourceName =
                getServletContext().getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);

        _sqlConProvider.init_(dbResourceName);

        String redisHost = REDIS.ADDRESS.get().getHostName();
        int redisPort = REDIS.ADDRESS.get().getPort();
        _jedisConProvider.init_(redisHost, (short) redisPort);

        PooledSQLConnectionProvider erConProvider = new PooledSQLConnectionProvider();
        erConProvider.init_(dbResourceName);

        // Initialize Email Reminder code
        InvitationReminderEmailer.Factory emailReminderFactory = new Factory();

        EmailReminder er = new EmailReminder(_esdb, _sqlTrans, emailReminderFactory);
        er.start();
    }

    private VerkehrAdmin getVerkehrAdmin()
    {
        return (VerkehrAdmin) getServletContext().getAttribute(VERKEHR_ADMIN_ATTRIBUTE);
    }

    private VerkehrPublisher getVerkehrPublisher()
    {
        return (VerkehrPublisher) getServletContext().getAttribute(VERKEHR_PUBLISHER_ATTRIBUTE);
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
        _sessionProvider.setSession(req.getSession());
        _certauth.init(req);

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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
            throws IOException
    {
        final int mysqlDatabaseCheckTimeoutSeconds = 1;

        try {
            // Check the mysql connection.
            _sqlTrans.begin();
            _sqlTrans.getConnection().isValid(mysqlDatabaseCheckTimeoutSeconds);
            _sqlTrans.commit();

            // Check the Redis connection. Do this by getting any random key.
            _jedisTrans.begin();
            _jedisTrans.get().get("");
            _jedisTrans.commit();
        } catch (Exception e) {
            l.warn("Database check error: " + Util.e(e));
            throw new IOException(e);
        }
    }
}
