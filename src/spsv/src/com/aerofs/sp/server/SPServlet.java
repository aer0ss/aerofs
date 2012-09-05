package com.aerofs.sp.server;

import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.InvitationCode.CodeType;
import com.aerofs.lib.spsv.sendgrid.SubscriptionCategory;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.db.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.InvitationReminderEmailer;
import com.aerofs.sp.server.email.InvitationReminderEmailer.Factory;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.cert.CertificateGenerator;
import com.aerofs.sp.server.organization.OrganizationManagement;
import com.aerofs.sp.server.sp.EmailReminder;
import com.aerofs.sp.server.user.UserManagement;
import com.aerofs.servlets.lib.DoPostDelegate;
import com.aerofs.sp.server.lib.SPDatabase;
import com.aerofs.sp.server.lib.SPParam;
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
    private final SQLThreadLocalTransaction _spTrans = new SQLThreadLocalTransaction(_conProvider);
    private final SPDatabase _db = new SPDatabase(_spTrans);

    private final ThreadLocalHttpSessionUser _sessionUser = new ThreadLocalHttpSessionUser();

    private final InvitationEmailer.Factory _emailerFactory = new InvitationEmailer.Factory();

    private final UserManagement _userManagement =
            new UserManagement(_db, _db, _emailerFactory, new PasswordResetEmailer());
    private final OrganizationManagement _organizationManagement =
            new OrganizationManagement(_db, _userManagement);

    private final SharedFolderManagement _sharedFolderManagement = new SharedFolderManagement(
            _db, _userManagement, _organizationManagement, _emailerFactory);

    private final CertificateGenerator _certificateGenerator = new CertificateGenerator();

    private final SPService _service = new SPService(_db, _spTrans, _sessionUser, _userManagement,
            _organizationManagement, _sharedFolderManagement, _certificateGenerator);
    private final SPServiceReactor _reactor = new SPServiceReactor(_service);

    private final DoPostDelegate _postDelegate = new DoPostDelegate(C.SP_POST_PARAM_PROTOCOL,
            C.SP_POST_PARAM_DATA);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        _certificateGenerator.setCAURL_(getServletContext().getInitParameter("ca_url"));
        _service.setVerkehrClients_(getVerkehrPublisher(), getVerkehrAdmin());

        String dbResourceName =
                getServletContext().getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);

        _conProvider.init_(dbResourceName);

        //initialize Email Reminder code
        PooledSQLConnectionProvider erConProvider = new PooledSQLConnectionProvider();
        erConProvider.init_(dbResourceName);

        SQLThreadLocalTransaction erTrans = new SQLThreadLocalTransaction(erConProvider);
        SPDatabase erDB = new SPDatabase(erTrans);
        InvitationReminderEmailer.Factory emailReminderFactory = new Factory();

        EmailReminder er = new EmailReminder(erDB, erTrans, emailReminderFactory);
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
            _spTrans.cleanUp();
        } catch (Exception e) {
            l.warn("exception in reactor: " + Util.e(e));
            throw new IOException(e.getCause());
        }

        _postDelegate.sendReply(resp, bytes);
    }

    protected void handleBatchInvite(HttpServletRequest req, HttpServletResponse rsp)
            throws IOException
    {
        String inviteCountStr = req.getParameter("inviteCount");

        int inviteCount = 0;
        try {
            inviteCount = Integer.parseInt(inviteCountStr);
        } catch(NumberFormatException e) {
            l.error("handleBatchInvite: ", e);
            throw e;
        }

        if (inviteCount <= 0) {
            rsp.getWriter().println("incomplete batch invite request");
            return;
        }

        try {
            String bsc = getBatchSignUpCode(inviteCount);
            rsp.getWriter().println("done: " + SPParam.getWebDownloadLink(bsc, true));
        } catch (Exception e) {
            l.error("handleBatchInvite: " + e);
            throw new IOException(e);
        }
    }

    private String getBatchSignUpCode(int inviteCount)
            throws SQLException
    {
        String bsc = InvitationCode.generate(InvitationCode.CodeType.BATCH_SIGNUP);
        _db.initBatchSignUpCode(bsc, inviteCount);
        return bsc;
    }

    protected void handleTargetedInvite(HttpServletRequest req, HttpServletResponse rsp)
            throws IOException
    {
        String fromPerson = req.getParameter("fromPerson");
        String to = req.getParameter("to");
        if (fromPerson == null || to == null) {
            rsp.getWriter().println("incomplete request.");
            return;
        }

        try {
            try {
                inviteFromScript(fromPerson, to);
                rsp.getWriter().println("done: " + fromPerson + " -> " + to);
            }
            catch (ExAlreadyExist e) {
                rsp.getWriter().println("skip " + to);
            }
        } catch (IOException e) {
            l.error("handleTargetedInvite: ", e);
            throw e;
        } catch (Throwable e) {
            l.error("handleTargetedInvite: ", e);
            throw new IOException(e);
        }
    }

    /**
     * Invite "to" to use AeroFS, assuming the caller is the SPServlet's HTTP GET request.
     * This exists only to support the invitation artifact script.
     * TODO it should be removed when the tools/invite script is removed
     * Perhaps it can be dumped into an Invite.java if it is ever created.
     * @param fromPerson inviter's name
     */
    private void inviteFromScript(@Nonnull String fromPerson, @Nonnull String to)
            throws Exception
    {
        to = to.toLowerCase();
        String orgId = C.DEFAULT_ORGANIZATION;

        // Check that the invitee isn't already a user
        _userManagement.checkUserIdDoesNotExist(to);

        // Check that we haven't already invited this user
        if (_db.isAlreadyInvited(to, orgId)) throw  new ExAlreadyExist("user already invited");

        String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
        _db.addTargetedSignupCode(code, SV.SUPPORT_EMAIL_ADDRESS, to, orgId);

        _emailerFactory.createUserInvitation(SV.SUPPORT_EMAIL_ADDRESS, to, fromPerson, null, null, code)
                .send();

        _db.addEmailSubscription(to, SubscriptionCategory.AEROFS_INVITATION_REMINDER);
    }

    // parameter format: aerofs=love&from=<email>&from=<email>&to=<email>
    //               or: aerofs=lotsoflove&inviteCount=<N>
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
        throws IOException
    {
        try {
            _spTrans.begin();
            if ("love".equals(req.getParameter("aerofs"))) {
                handleTargetedInvite(req, rsp);
            } else if ("lotsoflove".equals(req.getParameter("aerofs"))) {
                // not just love - LOTSOFLOVE!
                handleBatchInvite(req, rsp);
            }
            _spTrans.commit();
        } catch (SQLException e) {
            _spTrans.handleException();
            throw new IOException(e);
        } catch (IOException e) {
            _spTrans.handleException();
            throw e;
        }
    }
}
