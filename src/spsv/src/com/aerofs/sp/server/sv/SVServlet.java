package com.aerofs.sp.server.sv;

import com.aerofs.lib.Util;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVReply;
import com.aerofs.servletlib.db.PooledSQLConnectionProvider;
import com.aerofs.servletlib.db.SQLThreadLocalTransaction;
import com.aerofs.sp.server.AeroServlet;
import com.yammer.metrics.reporting.GraphiteReporter;
import org.apache.log4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.sp.server.sv.SVParam.SV_DATABASE_REFERENCE_PARAMETER;
import static com.aerofs.sp.server.sv.SVParam.SV_METRICS_HOST_PARAMETER;
import static com.aerofs.sp.server.sv.SVParam.SV_METRICS_PORT_PARAMETER;
import static java.util.concurrent.TimeUnit.MINUTES;

public class SVServlet extends AeroServlet
{
    private static final Logger l = Util.l(SVServlet.class);

    private static final long serialVersionUID = 1L;

    private final PooledSQLConnectionProvider _conProvider = new PooledSQLConnectionProvider();
    private final SQLThreadLocalTransaction _transaction =
            new SQLThreadLocalTransaction(_conProvider);
    private final SVDatabase _db = new SVDatabase(_transaction);
    private final SVReactor _reactor = new SVReactor(_db, _transaction);

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();

        try {
            _reactor.init_();
            initdb_();
            initmetrics_();
        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
    }

    private void initdb_()
            throws SQLException, ClassNotFoundException
    {
        String svdbRef = getServletContext().getInitParameter(SV_DATABASE_REFERENCE_PARAMETER);

        _conProvider.init_(svdbRef);
    }

    private void initmetrics_()
    {
        GraphiteReporter.enable(2, MINUTES,
                SV_METRICS_HOST_PARAMETER, Short.parseShort(SV_METRICS_PORT_PARAMETER));
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

            _transaction.cleanUp();
        } catch (IOException e) {
            l.error("doPost: ", e);
            _transaction.handleException();
            throw e;
        } catch (Throwable e) {
            l.error("doPost: ", e);
            _transaction.handleException();
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
