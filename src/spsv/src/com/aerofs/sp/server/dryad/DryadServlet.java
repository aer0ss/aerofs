/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam.REDIS;
import com.aerofs.servlets.lib.SyncEmailSender;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.CommandDispatcher;
import com.aerofs.sp.server.SPServlet;
import com.aerofs.sp.server.lib.OrganizationDatabase;
import com.aerofs.sp.server.lib.UserDatabase;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;
import static com.aerofs.sp.server.lib.SPParam.VERKEHR_ADMIN_ATTRIBUTE;

public class DryadServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    private Logger l = Loggers.getLogger(SPServlet.class);

    private PooledSQLConnectionProvider _sqlConnProvider;
    private SQLThreadLocalTransaction _sqlTrans;
    private OrganizationDatabase _odb;
    private UserDatabase _udb;

    private SyncEmailSender _email;

    private VerkehrAdmin _verkehr;
    private PooledJedisConnectionProvider _jedisConnProvider;
    private JedisThreadLocalTransaction _jedisTrans;
    private JedisEpochCommandQueue _commandQueue;
    private CommandDispatcher _cmd;

    private DryadService _service;
    private Gson _gson;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        _sqlConnProvider = new PooledSQLConnectionProvider();
        String dbResourceName = getServletContext()
                .getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);
        _sqlConnProvider.init_(dbResourceName);

        _sqlTrans = new SQLThreadLocalTransaction(_sqlConnProvider);
        _odb = new OrganizationDatabase(_sqlTrans);
        _udb = new UserDatabase(_sqlTrans);

        _email = createEmailSender();

        _verkehr = (VerkehrAdmin) config.getServletContext().getAttribute(VERKEHR_ADMIN_ATTRIBUTE);

        _jedisConnProvider = new PooledJedisConnectionProvider();
        String redisHost = REDIS.AOF_ADDRESS.getHostName();
        short redisPort = (short)REDIS.AOF_ADDRESS.getPort();
        _jedisConnProvider.init_(redisHost, redisPort);

        _jedisTrans = new JedisThreadLocalTransaction(_jedisConnProvider);
        _commandQueue = new JedisEpochCommandQueue(_jedisTrans);
        _cmd = new CommandDispatcher(_commandQueue, _jedisTrans);
        _cmd.setAdminClient(_verkehr);

        _service = new DryadService(_sqlTrans, _odb, _udb, _email, _cmd);

        _gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    {
        try {
            String dryadID = UUID.randomUUID().toString().replaceAll("-", "").toUpperCase();
            String customerID = getStringProperty("customer_id", "0");
            String customerName = getStringProperty("license_company", "Unknown");
            String email = req.getParameter("email");
            String desc = req.getParameter("desc");
            String[] users = req.getParameterValues("users");

            // sanitize the external input, this will throw a NumberFormatException if customerID
            // isn't a long
            Long.parseLong(customerID);

            _service.postToZenDesk(dryadID, customerID, customerName, email, desc);
            _service.enqueueCommandsForUsers(dryadID, customerID, users);
        } catch (Exception e) {
            l.warn("Failed POST", e);
            resp.setStatus(500);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    {
        try {
            List<String> userIDs = _service.listUserIDs(0, 50);
            String content = _gson.toJson(userIDs);

            resp.setContentType("application/json");
            resp.setContentLength(content.length());
            resp.getOutputStream().print(content);
        } catch (Exception e) {
            l.warn("Failed GET", e);
            resp.setStatus(500);
        }
    }

    private SyncEmailSender createEmailSender()
    {
        // duplicate in AsyncEmailSender

        // falls back to use the local mail relay
        String host         = getStringProperty("email.sender.public_host", "localhost");
        String port         = getStringProperty("email.sender.public_port", "25");
        String username     = getStringProperty("email.sender.public_username", "");
        String password     = getStringProperty("email.sender.public_password", "");
        boolean enableTLS   = getBooleanProperty("email.sender.public_enable_tls", false);
        String cert         = getStringProperty("email.sender.public_cert", "");

        return new SyncEmailSender(host, port, username, password, enableTLS, cert);
    }
}
