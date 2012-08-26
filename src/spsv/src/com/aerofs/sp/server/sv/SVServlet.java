package com.aerofs.sp.server.sv;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import com.aerofs.servletlib.db.DatabaseConnectionFactory;
import com.aerofs.servletlib.sv.SVDatabase;
import static com.aerofs.servletlib.sv.SVParam.*;
import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVReply;
import com.aerofs.sp.server.AeroServlet;

public class SVServlet extends AeroServlet
{
    private static final Logger l = Util.l(SVServlet.class);

    private static final long serialVersionUID = 1L;

    private final DatabaseConnectionFactory _dbFactory = new DatabaseConnectionFactory();
    private final SVDatabase _db = new SVDatabase(_dbFactory);
    private final SVReactor _reactor = new SVReactor(_db);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        try {
            _reactor.init_();
            initdb_();
        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
    }

    private void initdb_()
            throws SQLException, ClassNotFoundException
    {
        String dbEndpoint = getServletContext().getInitParameter(MYSQL_ENDPOINT_INIT_PARAMETER);
        String dbUser = getServletContext().getInitParameter(MYSQL_USER_INIT_PARAMETER);
        String dbPass = getServletContext().getInitParameter(MYSQL_PASSWORD_INIT_PARAMETER);
        String dbSchema = getServletContext().getInitParameter(MYSQL_SV_SCHEMA_INIT_PARAMETER);

        // Be sure to initialize the factory before the database is initialized.
        _dbFactory.init_(dbEndpoint, dbSchema, dbUser, dbPass);
        _db.init_();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws IOException
    {
        try {
            PBSVCall call = PBSVCall.parseDelimitedFrom(req.getInputStream());

            // process call
            String client = req.getRemoteAddr() + ":" + req.getRemotePort();
            PBSVReply reply = _reactor.react(call, req.getInputStream(), client);

            // send reply
            byte[] bs = reply.toByteArray();
            resp.setContentLength(bs.length);
            resp.getOutputStream().write(bs);

        } catch (IOException e) {
            l.error("doPost: ", e);
            throw e;
        } catch (Throwable e) {
            l.error("doPost: ", e);
            throw new IOException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
        throws IOException
    {
        // no-op
    }
}
