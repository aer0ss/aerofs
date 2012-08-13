package com.aerofs.sp.server.sp;

import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.spsv.InvitationCode;
import com.aerofs.lib.spsv.InvitationCode.CodeType;
import com.aerofs.proto.Sp.SPServiceReactor;
import com.aerofs.servletlib.db.DatabaseConnectionFactory;
import com.aerofs.sp.server.AeroServlet;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.email.PasswordResetEmailer;
import com.aerofs.sp.server.sp.cert.CertificateGenerator;
import com.aerofs.sp.server.sp.organization.OrganizationManagement;
import com.aerofs.sp.server.sp.user.UserManagement;
import com.aerofs.servletlib.sp.DoPostDelegate;
import com.aerofs.servletlib.sp.SPDatabase;
import com.aerofs.servletlib.sp.SPParam;
import com.aerofs.servletlib.sp.ThreadLocalHttpSessionUser;
import com.aerofs.verkehr.client.lib.commander.VerkehrCommander;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.common.util.concurrent.ExecutionError;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.mail.MessagingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.sp.server.SPSVParam.SP_EMAIL_ADDRESS;
import static com.aerofs.servletlib.sp.SPParam.MYSQL_ENDPOINT_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.MYSQL_PASSWORD_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.MYSQL_SP_SCHEMA_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.MYSQL_USER_INIT_PARAMETER;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_COMMANDER_ATTRIBUTE;
import static com.aerofs.servletlib.sp.SPParam.VERKEHR_PUBLISHER_ATTRIBUTE;

public class SPServlet extends AeroServlet
{
    private static final Logger l = Util.l(SPServlet.class);

    private static final long serialVersionUID = 1L;

    private final DatabaseConnectionFactory _dbFactory = new DatabaseConnectionFactory();
    private final SPDatabase _db = new SPDatabase(_dbFactory);
    private final ThreadLocalHttpSessionUser _sessionUser = new ThreadLocalHttpSessionUser();

    private final InvitationEmailer _emailer = new InvitationEmailer();

    private final UserManagement _userManagement =
            new UserManagement(_db, _emailer, new PasswordResetEmailer());
    private final OrganizationManagement _organizationManagement =
            new OrganizationManagement(_db, _userManagement);

    private final SharedFolderManagement _sharedFolderManagement = new SharedFolderManagement(
            _db, _userManagement, _organizationManagement, _emailer);

    private final CertificateGenerator _certificateGenerator = new CertificateGenerator();

    private final SPService _service = new SPService(_db, _sessionUser, _userManagement,
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
        _service.setVerkehrClients_(getVerkehrPublisher(), getVerkehrCommander());

        try {
            initdb_();
        } catch (Exception e) {
            l.error("init db:", e);
            throw new ServletException(e);
        }
    }

    private void initdb_()
            throws SQLException, ClassNotFoundException
    {
        String dbEndpoint = getServletContext().getInitParameter(MYSQL_ENDPOINT_INIT_PARAMETER);
        String dbUser = getServletContext().getInitParameter(MYSQL_USER_INIT_PARAMETER);
        String dbPass = getServletContext().getInitParameter(MYSQL_PASSWORD_INIT_PARAMETER);
        String dbSchema = getServletContext().getInitParameter(MYSQL_SP_SCHEMA_INIT_PARAMETER);

        // Be sure to initialize the factory before the database is initialized.
        _dbFactory.init_(dbEndpoint, dbSchema, dbUser, dbPass);
        _db.init_();
    }

    private VerkehrCommander getVerkehrCommander()
    {
        return (VerkehrCommander) getServletContext().getAttribute(VERKEHR_COMMANDER_ATTRIBUTE);
    }

    private VerkehrPublisher getVerkehrPublisher()
    {
        return (VerkehrPublisher) getServletContext().getAttribute(VERKEHR_PUBLISHER_ATTRIBUTE);
    }

    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
        throws IOException
    {
        try {
            _sessionUser.setSession(req.getSession());

            // Receive protocol version number.
            int protocol = _postDelegate.getProtocolVersion(req);
            if (protocol != C.SP_PROTOCOL_VERSION) {
                throw new IOException("sp protocol version mismatch. servlet: "
                        + C.SP_PROTOCOL_VERSION + " client: " + protocol);
            }

            byte[] decodedMessage = _postDelegate.getDecodedMessage(req);

            // Process call
            byte [] bytes;
            try {
                bytes = _reactor.react(decodedMessage).get();
            } catch (ExecutionError e) {
                l.warn("Exception in reactor " + Util.e(e));
                throw e.getCause();
            }

            _postDelegate.sendReply(resp, bytes);
        } catch (IOException e) {
            l.error("doPost: ", e);
            throw e;
        } catch (Throwable e) {
            l.error("doPost: ", e);
            throw new IOException(e);
        }
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
            throws ExAlreadyExist, SQLException, IOException, MessagingException, Exception
    {
        to = to.toLowerCase();
        String orgId = C.DEFAULT_ORGANIZATION;

        // Check that the invitee isn't already a user
        _userManagement.checkUserIdDoesNotExist(to);

        // Check that we haven't already invited this user
        if (_db.isAlreadyInvited(to, orgId)) throw  new ExAlreadyExist("user already invited");

        String code = InvitationCode.generate(CodeType.TARGETED_SIGNUP);
        _db.addTargetedSignupCode(code, SP_EMAIL_ADDRESS, to, orgId);

        _emailer.sendUserInvitationEmail(SP_EMAIL_ADDRESS, to, fromPerson, null, null, code);
    }

    // parameter format: aerofs=love&from=<email>&from=<email>&to=<email>
    //               or: aerofs=lotsoflove&inviteCount=<N>
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
        throws IOException
    {
        if ("love".equals(req.getParameter("aerofs"))) {
            handleTargetedInvite(req, rsp);
        } else if ("lotsoflove".equals(req.getParameter("aerofs"))) {
            // not just love - LOTSOFLOVE!
            handleBatchInvite(req, rsp);
        }
    }
}
