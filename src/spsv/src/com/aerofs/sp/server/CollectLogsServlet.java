/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.lib.Util;
import com.aerofs.servlets.lib.AbstractEmailSender;
import com.aerofs.servlets.lib.AsyncEmailSender;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.email.Email;
import com.aerofs.sp.server.lib.License;
import com.aerofs.sp.server.lib.SPParam;
import com.aerofs.sp.server.lib.user.UserDatabase;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Collections;
import java.util.List;

import static com.aerofs.sp.server.CommandUtil.createUploadLogsToAeroFSCommandMessage;
import static com.aerofs.sp.server.CommandUtil.createUploadLogsToOnSiteCommandMessage;
import static com.aerofs.sp.server.lib.SPParam.SSMP_CLIENT_ATTRIBUTE;
import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class CollectLogsServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    private final Logger l = Loggers.getLogger(CollectLogsServlet.class);

    private static final String OPTION_ONSITE = "on-site";

    private SQLThreadLocalTransaction _sqlTrans;
    private UserDatabase _udb;
    private AbstractEmailSender _email;
    private CommandDispatcher _cmd;
    private License _license;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        PooledSQLConnectionProvider _sqlConnProvider = new PooledSQLConnectionProvider();
        _sqlTrans = new SQLThreadLocalTransaction(_sqlConnProvider);
        _udb = new UserDatabase(_sqlTrans);

        // N.B. this choice relies on the current method of provisioning AsyncEmailSender.
        // Specifically, we relies on AsyncEmailSender to pull configuration in the following order:
        // - pull from config server
        // - read from a local properties file (file not available for private deployments)
        // - defaults to use local smtp client
        _email = AsyncEmailSender.create();

        SSMPConnection ssmp = (SSMPConnection) getServletContext()
                .getAttribute(SSMP_CLIENT_ATTRIBUTE);
        checkNotNull(ssmp);
        ssmp.start();

        PooledJedisConnectionProvider _jedisConnProvider = new PooledJedisConnectionProvider();
        _jedisConnProvider.init_(REDIS.AOF_ADDRESS.getHostName(), REDIS.AOF_ADDRESS.getPort(), REDIS.PASSWORD);

        JedisThreadLocalTransaction _jedisTrans = new JedisThreadLocalTransaction(_jedisConnProvider);
        JedisEpochCommandQueue _commandQueue = new JedisEpochCommandQueue(_jedisTrans);
        _cmd = new CommandDispatcher(_commandQueue, _jedisTrans, ssmp);

        _license = new License();

        l.info("CollectLogsServlet initialized.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    {
        l.info("POST");

        try {
            String defectID         = req.getParameter("defectID");
            String version          = req.getParameter("version");
            List<UserID> userIDs    = getUsersIDs(req.getParameterValues("users"));
            boolean toOnsite        = equal(req.getParameter("option"), OPTION_ONSITE);
            String subject          = req.getParameter("subject");
            String message          = req.getParameter("message");
            long customerID         = Long.parseLong(_license.customerID());
            String customerName     = _license.customerName();

            String commandMessage;

            if (toOnsite) {
                l.info("Send logs to on-site collection facility.");

                String hostname = req.getParameter("host");
                int port = Integer.parseInt(req.getParameter("port"));
                String cert = req.getParameter("cert");
                commandMessage = createUploadLogsToOnSiteCommandMessage(defectID, getExpiryTime(),
                        hostname, port, cert);
                enqueueCommandsForUsers(commandMessage, userIDs);
                notifyOnSiteSupport(hostname, subject, defectID, userIDs);
            } else {
                l.info("Send logs to AeroFS support.");

                String email = req.getParameter("email");
                commandMessage = createUploadLogsToAeroFSCommandMessage(defectID, getExpiryTime());
                enqueueCommandsForUsers(commandMessage, userIDs);
                emailAeroSupport(customerID, customerName, email, defectID, version, userIDs,
                        subject, message);
                notifyOnSiteSupport(SPParam.BRAND + " Support", subject, defectID, userIDs);
            }

            resp.setStatus(200);
        } catch (Exception e) {
            l.error("", e);
            resp.setStatus(500);
        }
    }

    private List<UserID> getUsersIDs(String[] users)
    {
        users = Objects.firstNonNull(users, new String[0]);

        List<UserID> userIDs = Lists.newArrayListWithCapacity(users.length);

        for (String user : users) {
            try {
                userIDs.add(UserID.fromExternal(user));
            } catch (ExInvalidID e) {
                l.warn("Invalid User ID: {}", user);
            }
        }

        return userIDs;
    }

    private void enqueueCommandsForUsers(String commandMessage, List<UserID> userIDs)
    {
        for (UserID userID : userIDs) {
            for (DID did : listUserDevices(userID)) {
                try {
                    _cmd.enqueueCommand(did, commandMessage);
                } catch (Exception e) {
                    l.warn("Unable to enqueue commands for device: {}/{}",
                            userID.getString(), did.toStringFormal(), e);
                }
            }
        }
    }

    private List<DID> listUserDevices(UserID userID)
    {
        try {
            return getUserDevices(userID);
        } catch (Exception e) {
            l.warn("Failed to get user's devices. User: {}", userID.getString());
            return Collections.emptyList();
        }
    }

    private List<DID> getUserDevices(UserID userID) throws Exception
    {
        _sqlTrans.begin();

        try {
            List<DID> devices = _udb.getDevices(userID);
            _sqlTrans.commit();
            return devices;
        } catch (Exception e) {
            _sqlTrans.handleException();
            throw e;
        } finally {
            _sqlTrans.cleanUp();
        }
    }

    // Sends a confirmation e-mail to on-site support to acknowledge the command has been issued.
    private void notifyOnSiteSupport(String dest, String subject, String defectID, List<UserID> users)
            throws Exception
    {
        String fromName = SPParam.EMAIL_FROM_NAME;
        String to = WWW.SUPPORT_EMAIL_ADDRESS;
        String replyTo = WWW.SUPPORT_EMAIL_ADDRESS;
        subject = format("[%s Support] %s", SPParam.BRAND, subject);
        String header = format("%s Support", SPParam.BRAND);
        String body = format("An administrator has issued a command to collect logs from %s users. " +
                        "%s client logs from the following users' computers will be uploaded " +
                        "to %s over the next seven days.\n\n" +
                        "Defect ID: %s\n" +
                        "Users:\n%s\n",
                SPParam.BRAND, SPParam.BRAND, dest, defectID, formatUsersList(users));

        Email email = new Email();
        email.addSection(header, body);
        _email.sendPublicEmailFromSupport(fromName, to, replyTo, subject,
                email.getTextEmail(), email.getHTMLEmail());
    }

    private void emailAeroSupport(long customerID,
                                  String customerName,
                                  String contactEmail,
                                  String defectID,
                                  String version,
                                  List<UserID> users,
                                  String subject,
                                  String message)
            throws Exception
    {
        String fromName = SPParam.EMAIL_FROM_NAME;

        String to = "support@aerofs.com";
        String replyTo = contactEmail;
        String header = format("%s has reported an issue while using %s.", contactEmail,
                SPParam.BRAND);
        String body = format(
                "Defect ID: %s\n" +
                "Version: %s\n" +
                "Customer ID: %s\n" +
                "Customer Name: %s\n" +
                "Users:\n%s\n" +
                "Message:\n%s",
                defectID,
                version,
                customerID,
                customerName,
                formatUsersList(users),
                message);

        Email email = new Email();
        email.addSection(header, body);
        _email.sendPublicEmailFromSupport(fromName, to, replyTo, subject,
                email.getTextEmail(), email.getHTMLEmail());
    }

    private String formatUsersList(List<UserID> users)
    {
        StringBuilder sb = new StringBuilder();
        for (UserID user : users) {
            sb.append(user.isTeamServerID() ? "Team Server" : user.getString())
                    .append("\n");
        }
        return sb.toString();
    }

    /**
     * This is used to implement TTL. We push down the expiry time in server time and pray for the
     * client's clock and the server's clock don't differ enough to cause an issue.
     */
    private long getExpiryTime()
    {
        return System.currentTimeMillis() + 7 * C.DAY;
    }
}
