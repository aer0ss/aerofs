package com.aerofs.sp.server;

import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExEmailSendingFailed;
import com.aerofs.lib.id.UserID;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.db.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.EmailSubscriptionDatabase;
import com.aerofs.sp.server.lib.SharedFolderDatabase;
import com.aerofs.sp.server.lib.SharedFolderInvitationDatabase;
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
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.sp.server.sp.EmailReminder;
import com.aerofs.sp.server.user.PasswordManagement;
import com.aerofs.servlets.lib.DoPostDelegate;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.ThreadLocalHttpSessionUser;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_ATTRIBUTE;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;

public class SPServlet extends AeroServlet
{
    private static final Logger l = Util.l(SPServlet.class);

    private static final long serialVersionUID = 1L;

    private final PooledSQLConnectionProvider _conProvider = new PooledSQLConnectionProvider();
    private final SQLThreadLocalTransaction _trans = new SQLThreadLocalTransaction(_conProvider);

    // TODO (WW) remove dependency on these database classes
    private final SPDatabase _db = new SPDatabase(_trans);
    private final OrganizationDatabase _odb = new OrganizationDatabase(_trans);
    private final UserDatabase _udb = new UserDatabase(_trans);
    private final DeviceDatabase _ddb = new DeviceDatabase(_trans);
    private final CertificateDatabase _certdb = new CertificateDatabase(_trans);
    private final EmailSubscriptionDatabase _esdb = new EmailSubscriptionDatabase(_trans);
    private final SharedFolderDatabase _sfdb = new SharedFolderDatabase(_trans);
    private final SharedFolderInvitationDatabase _sfidb =
            new SharedFolderInvitationDatabase(_trans);

    private final ThreadLocalHttpSessionUser _sessionUser = new ThreadLocalHttpSessionUser();
    private final CertificateGenerator _certgen = new CertificateGenerator();

    private final Organization.Factory _factOrg = new Organization.Factory(_odb);
    private final User.Factory _factUser = new User.Factory(_udb, _factOrg);
    private final Device.Factory _factDevice = new Device.Factory(_ddb, _factUser, _certdb,
            _certgen);
    private final SharedFolder.Factory _factSharedFolder = new SharedFolder.Factory(_sfdb);
    private final SharedFolderInvitation.Factory _factSFI =
            new SharedFolderInvitation.Factory(_sfidb, _factUser, _factSharedFolder);
    private final InvitationEmailer.Factory _factEmailer = new InvitationEmailer.Factory();

    private final PasswordManagement _passwordManagement =
            new PasswordManagement(_db, _factUser, new PasswordResetEmailer());

    private final SPService _service = new SPService(_db, _sfdb, _trans, _sessionUser,
            _passwordManagement, _factUser, _factOrg, _factDevice, _certdb, _esdb,
            _factSharedFolder, _factSFI, _factEmailer);
    private final SPServiceReactor _reactor = new SPServiceReactor(_service);

    private final DoPostDelegate _postDelegate = new DoPostDelegate(C.SP_POST_PARAM_PROTOCOL,
            C.SP_POST_PARAM_DATA);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        _certgen.setCAURL_(getServletContext().getInitParameter("ca_url"));
        _service.setVerkehrClients_(getVerkehrPublisher(), getVerkehrAdmin());

        String dbResourceName =
                getServletContext().getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);

        _conProvider.init_(dbResourceName);

        //initialize Email Reminder code
        PooledSQLConnectionProvider erConProvider = new PooledSQLConnectionProvider();
        erConProvider.init_(dbResourceName);

        InvitationReminderEmailer.Factory emailReminderFactory = new Factory();

        EmailReminder er = new EmailReminder(_esdb, _trans, emailReminderFactory);
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

    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
        throws IOException
    {
        _sessionUser.setSession(req.getSession());

        // Receive protocol version number.
        int protocol = _postDelegate.getProtocolVersion(req);
        if (protocol != C.SP_PROTOCOL_VERSION) {
            l.warn("protocol version mismatch. servlet: " + C.SP_PROTOCOL_VERSION + " client: " +
                    protocol);
            resp.sendError(400, "prototcol version mismatch");
            return;
        }

        byte[] decodedMessage = _postDelegate.getDecodedMessage(req);

        // Process call
        byte [] bytes;
        try {
            bytes = _reactor.react(decodedMessage).get();
            _trans.cleanUp();
        } catch (Exception e) {
            l.warn("exception in reactor: " + Util.e(e));
            throw new IOException(e.getCause());
        }

        _postDelegate.sendReply(resp, bytes);
    }

    // parameter format: aerofs=love&from=<email>&from=<email>&to=<email>
    //               or: aerofs=lotsoflove&inviteCount=<N>
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
            throws IOException
    {
        try {
            _trans.begin();
            if ("love".equals(req.getParameter("aerofs"))) {
                handleSignUpInvite(req, rsp);
            }
            _trans.commit();
        } catch (SQLException e) {
            _trans.handleException();
            throw new IOException(e);
        } catch (IOException e) {
            _trans.handleException();
            throw e;
        }
    }

    protected void handleSignUpInvite(HttpServletRequest req, HttpServletResponse rsp)
            throws IOException
    {
        String fromPerson = req.getParameter("fromPerson");
        String to = req.getParameter("to");
        if (fromPerson == null || to == null) {
            rsp.getWriter().println("incomplete request.");
            return;
        }

        try {
            inviteFromScript(fromPerson, to);
            rsp.getWriter().println("done: " + fromPerson + " -> " + to);
        } catch (ExAlreadyExist e) {
            rsp.getWriter().println("skip " + to);
        } catch (Exception e) {
            l.error("handleSignUpInvite: ", e);
            throw new IOException(e);
        }
    }

    /**
     * Invite "to" to use AeroFS, assuming the caller is the SPServlet's HTTP GET request.
     * This exists only to support the invitation artifact script.
     * TODO it should be removed when the tools/invite script is removed
     * Perhaps it can be dumped into an Invite.java if it is ever created.
     * @param inviterName inviter's name
     */
    private void inviteFromScript(@Nonnull String inviterName, @Nonnull String inviteeIdString)
            throws ExAlreadyExist, SQLException, IOException, ExEmailSendingFailed
    {
        User invitee = _factUser.createFromExternalID(inviteeIdString);

        // Check that the invitee isn't already a user
        if (invitee.exists()) throw new ExAlreadyExist("user already exists");

        // Check that we haven't already invited this user
        if (invitee.isInvitedToSignUp()) throw new ExAlreadyExist("user already invited");

        User inviter = _factUser.create(UserID.fromInternal(SV.SUPPORT_EMAIL_ADDRESS));
        InvitationEmailer emailer = _service.inviteToSignUp(invitee, _factOrg.getDefault(), inviter,
                inviterName, null, null);
        emailer.send();
    }
}
