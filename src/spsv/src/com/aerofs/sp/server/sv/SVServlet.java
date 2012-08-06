package com.aerofs.sp.server.sv;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.apache.log4j.Logger;

import com.aerofs.lib.Util;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVReply;
import com.aerofs.sp.server.AeroServlet;

public class SVServlet extends AeroServlet
{
    private static final Logger l = Util.l(SVServlet.class);

    private static final long serialVersionUID = 1L;

    private final SVDatabase _db = new SVDatabase();
    private final SVReactor _reactor = new SVReactor(_db);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        try {
            _reactor.init_();

            String dbEndpoint = getServletContext().getInitParameter("mysql_endpoint");
            String dbUser = getServletContext().getInitParameter("mysql_user");
            String dbPass = getServletContext().getInitParameter("mysql_password");
            String dbSchema = getServletContext().getInitParameter("mysql_sv_schema");
            _db.init_(dbEndpoint, dbSchema, dbUser, dbPass);

        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
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
