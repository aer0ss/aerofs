/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.sql.PooledSQLConnectionProvider;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.organization.OrganizationDatabase;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

import static com.aerofs.base.id.OrganizationID.PRIVATE_ORGANIZATION;
import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

public class GetUsersServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    private final Logger l = Loggers.getLogger(GetUsersServlet.class);

    private SQLThreadLocalTransaction   _sqlTrans;
    private OrganizationDatabase        _odb;

    private Gson                        _gson;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        PooledSQLConnectionProvider sqlConnProvider = new PooledSQLConnectionProvider();
        _sqlTrans = new SQLThreadLocalTransaction(sqlConnProvider);
        _odb = new OrganizationDatabase(_sqlTrans);

        _gson = new GsonBuilder()
                .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
                .create();

        l.info("GetUsersServlet initialized.");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    {
        l.info("GET");

        try {
            List<String> users = listUsers();
            String content = _gson.toJson(users);

            resp.setContentType("application/json");
            resp.setContentLength(content.length());
            resp.getOutputStream().print(content);
        } catch (Exception e) {
            l.warn("Failed GET request", e);
            resp.setStatus(500);
        }
    }

    private List<String> listUsers() throws Exception
    {
        List<String> users = Lists.newArrayList();

        int pageSize = 10000;
        List<UserID> userIDs;

        do {
            userIDs = getUsers(users.size(), pageSize);
            for (UserID userID : userIDs) {
                users.add(userID.getString());
            }
        } while (userIDs.size() >= pageSize);

        users.add(PRIVATE_ORGANIZATION.toTeamServerUserID().getString());

        return users;
    }

    private List<UserID> getUsers(int offset, int count) throws Exception
    {
        _sqlTrans.begin();

        try {
            List<UserID> users = _odb.listUsers(PRIVATE_ORGANIZATION, offset, count, null);
            _sqlTrans.commit();
            return users;
        } catch (Exception e) {
            _sqlTrans.handleException();
            throw e;
        } finally {
            _sqlTrans.cleanUp();
        }
    }
}
