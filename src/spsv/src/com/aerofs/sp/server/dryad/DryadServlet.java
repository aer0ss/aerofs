/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.dryad;

import com.aerofs.base.Loggers;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.SPServlet;
import com.aerofs.sp.server.lib.OrganizationDatabase;
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

import static com.aerofs.sp.server.lib.SPParam.SP_DATABASE_REFERENCE_PARAMETER;

public class DryadServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    private Logger l = Loggers.getLogger(SPServlet.class);

    private final PooledSQLConnectionProvider   _sqlConnProvider
            = new PooledSQLConnectionProvider();
    private final SQLThreadLocalTransaction     _sqlTrans
            = new SQLThreadLocalTransaction(_sqlConnProvider);
    private final OrganizationDatabase          _odb
            = new OrganizationDatabase(_sqlTrans);

    private final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private DryadService _service;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        String dbResourceName = getServletContext().getInitParameter(SP_DATABASE_REFERENCE_PARAMETER);
        _sqlConnProvider.init_(dbResourceName);

        _service = new DryadService(_sqlTrans, _odb);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
    {
        resp.setStatus(200);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    {
        try {
            try {
                List<String> userIDs = _service.listUserIDs(0, 50);
                String content = _gson.toJson(userIDs);

                resp.setContentType("application/json");
                resp.setContentLength(content.length());
                resp.getOutputStream().print(content);
            } finally {
                _sqlTrans.cleanUp();
            }
        } catch (Exception e) {
            l.warn("Failed GET request", e);
        }

        resp.setStatus(200);
    }
}
