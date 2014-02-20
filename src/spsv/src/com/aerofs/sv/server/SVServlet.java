package com.aerofs.sv.server;

import com.aerofs.base.Loggers;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVReply;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.servlets.lib.db.ExDbInternal;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.googlecode.flyway.core.Flyway;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.sv.server.SVParam.SV_DATABASE_REFERENCE_PARAMETER;

public class SVServlet extends AeroServlet
{
    private static final Logger l = Loggers.getLogger(SVServlet.class);

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
            initDB_();
        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
    }

    private void initDB_()
            throws SQLException, ClassNotFoundException
    {
        String svdbRef = getServletContext().getInitParameter(SV_DATABASE_REFERENCE_PARAMETER);

        _conProvider.init_(svdbRef);
        DataSource dataSource;
        try {
            dataSource = _conProvider.getDataSource();
        } catch (ExDbInternal e) {
            throw new SQLException(e.getCause());
        }

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.setInitOnMigrate(true);
        flyway.setSchemas("aerofs_sv");
        flyway.migrate();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws IOException
    {
        try {
            PBSVCall call = PBSVCall.parseDelimitedFrom(req.getInputStream());

            // process call
            String client = req.getHeader("X-Real-IP");
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
